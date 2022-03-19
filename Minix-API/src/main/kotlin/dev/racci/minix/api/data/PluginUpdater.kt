package dev.racci.minix.api.data

import dev.racci.minix.api.services.DataService
import dev.racci.minix.api.updater.UpdateMode
import dev.racci.minix.api.updater.UpdateResult
import dev.racci.minix.api.updater.Version
import dev.racci.minix.api.updater.providers.UpdateProvider
import kotlinx.datetime.Instant
import org.bukkit.plugin.Plugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import kotlin.time.Duration

@ConfigSerializable
data class PluginUpdater(
    @Comment("The name of the plugin")
    var name: String,
    @Comment("Which update type to use")
    var updateMode: UpdateMode = UpdateMode.UPDATE,
    @Comment("What providers are supported by this plugin")
    var providers: Array<Pair<String, UpdateProvider>>,
    @Comment("What release channels should be updated to")
    var channels: Array<String> = arrayOf("release"),
    @Comment("How long between update checks")
    var interval: Duration = updaterConfig.interval,
    @Comment("Should we ignore any folders, or files when backing up this plugin?")
    var ignored: Array<String> = emptyArray()
) {

    @Transient var failedAttempts: Int = 0
    @Transient var pluginInstance: Plugin? = null
    @Transient var lastRun: Instant? = null
    @Transient var remoteVersion: Version? = null
    val localVersion: Version by lazy { Version(pluginInstance!!.description.version) } // By the time these are called the instance should be set.
    val localFile: String by lazy { pluginInstance!!.description.version }
    @Transient var result: UpdateResult? = null
    @Transient var activeProvider: Int = 0
    val provider: UpdateProvider get() = providers[activeProvider].second

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PluginUpdater

        if (name != other.name) return false
        if (updateMode != other.updateMode) return false
        if (!providers.contentEquals(other.providers)) return false
        if (!ignored.contentEquals(other.ignored)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + updateMode.hashCode()
        result = 31 * result + providers.contentHashCode()
        result = 31 * result + ignored.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "PluginUpdater(name=$name:updateMode=$updateMode:providers=${providers.joinToString(":", "[", "]")}:ignored=${ignored.joinToString(":", "[", "]")})"
    }

    companion object : KoinComponent {
        private val updaterConfig by lazy { get<DataService>().get<UpdaterConfig>() }
    }
}