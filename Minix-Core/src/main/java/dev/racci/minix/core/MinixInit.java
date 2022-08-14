package dev.racci.minix.core;

import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.app.builder.InjectingApplicationBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.PluginClassLoader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;


/**
 * Creating a dummy plugin without any kotlin methods, so we can dynamically load the libraries.
 */
@SuppressWarnings({"java:S3011", "java:S1171"}) // We need reflection and non-static init.
public class MinixInit extends JavaPlugin {

    {
        ClassLoader libraryLoader;
        try {
            var field = PluginClassLoader.class.getDeclaredField("libraryLoader");
            field.setAccessible(true);
            libraryLoader = (ClassLoader) field.get(getClassLoader());
            field.setAccessible(false);
        } catch(NoSuchFieldException e) {
            this.getLogger().severe("Failed to find field 'libraryLoader' in class 'PluginClassLoader', unsupported version?");
            throw new ReflectingInitializationException(e);
        } catch(IllegalAccessException e) {
            this.getLogger().severe("Failed to access field 'libraryLoader' in class 'PluginClassLoader', unsupported version?");
            throw new ReflectingInitializationException(e);
        } catch(ClassCastException e) {
            this.getLogger().severe("Failed to cast field 'libraryLoader' as 'ClassLoader' in class 'PluginClassLoader', unsupported version?");
            throw new ReflectingInitializationException(e);
        }

        ApplicationBuilder builder;
        try {
            builder = InjectingApplicationBuilder.createAppending("Minix", libraryLoader);
        } catch(ReflectiveOperationException | IOException | URISyntaxException | NoSuchAlgorithmException e) {
            this.getLogger().severe("Failed to create application builder.");
            throw new ReflectingInitializationException(e);
        }

        var folder = Path.of(String.format("%s/libraries", getDataFolder()));
        if(!Files.exists(folder) && !folder.toFile().mkdirs()) {
            this.getLogger().severe("Error while creating parent directories.");
            throw new ReflectingInitializationException(null);
        }
        builder.downloadDirectoryPath(folder);

        try {
            builder.build();
        } catch(IOException | ReflectiveOperationException | URISyntaxException | NoSuchAlgorithmException e) {
            this.getLogger().severe("Failed to build application.");
            throw new ReflectingInitializationException(e);
        }

        new DummyLoader().loadPlugin(this.getDescription(), this, (PluginClassLoader) this.getClassLoader());
    }
}