package dev.racci.minix.core.services

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import dev.racci.minix.api.annotations.MappedConfig
import dev.racci.minix.api.annotations.MappedExtension
import dev.racci.minix.api.coroutine.contract.CoroutineSession
import dev.racci.minix.api.coroutine.coroutineService
import dev.racci.minix.api.coroutine.registerSuspendingEvents
import dev.racci.minix.api.extension.Extension
import dev.racci.minix.api.extension.ExtensionState
import dev.racci.minix.api.plugin.Minix
import dev.racci.minix.api.plugin.MinixPlugin
import dev.racci.minix.api.plugin.PluginData
import dev.racci.minix.api.plugin.SusPlugin
import dev.racci.minix.api.scheduler.CoroutineScheduler
import dev.racci.minix.api.services.DataService
import dev.racci.minix.api.services.PluginService
import dev.racci.minix.api.utils.kotlin.ifNotEmpty
import dev.racci.minix.api.utils.kotlin.invokeIfNotNull
import dev.racci.minix.api.utils.kotlin.invokeIfOverrides
import dev.racci.minix.api.utils.loadModule
import dev.racci.minix.api.utils.unsafeCast
import dev.racci.minix.core.coroutine.service.CoroutineSessionImpl
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bstats.bukkit.Metrics
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ext.getFullName
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration.Companion.seconds

class PluginServiceImpl(val minix: Minix) : PluginService {

    override val loadedPlugins by lazy { mutableMapOf<KClass<out MinixPlugin>, MinixPlugin>() }

    override val pluginCache: LoadingCache<MinixPlugin, PluginData<MinixPlugin>> = Caffeine.newBuilder().build(::PluginData)

    override val coroutineSession: LoadingCache<MinixPlugin, CoroutineSession> = Caffeine.newBuilder().build { plugin ->
        if (!plugin.isEnabled) {
            plugin.log.throwing(
                RuntimeException(
                    "Plugin ${plugin.name} attempt to start a new coroutine session while being disabled. " +
                        "Dispatchers such as plugin.minecraftDispatcher and plugin.asyncDispatcher are already " +
                        "disposed at this point and cannot be used!"
                )
            )
        }
        CoroutineSessionImpl(plugin)
    }

    @Suppress("UNCHECKED_CAST") // This should be safe i think
    override operator fun <P : MinixPlugin> get(plugin: P): PluginData<P> = pluginCache[plugin] as PluginData<P>

    override fun loadPlugin(plugin: MinixPlugin) {
        runBlocking {
            loadModule { single { plugin } bind (plugin.bindToKClass ?: plugin::class) }
            plugin.invokeIfOverrides(SusPlugin::handleLoad.name) { plugin.handleLoad() }
            plugin.loadReflection()
            pluginCache[plugin].extensions.ifNotEmpty { plugin.loadInOrder() }
        }
    }

    override fun startPlugin(plugin: MinixPlugin) {
        coroutineService.getCoroutineSession(plugin).wakeUpBlockService.isManipulatedServerHeartBeatEnabled = true
        runBlocking {
            val cache = pluginCache[plugin]

            plugin.invokeIfOverrides(SusPlugin::handleEnable.name) {
                minix.log.debug { "Running handleEnable for ${plugin.name}" }
                plugin.handleEnable()
            }

            cache.extensions.ifNotEmpty { plugin.startInOrder() }

            cache.listeners.ifNotEmpty { collection ->
                minix.log.debug { "Registering ${collection.size} listeners for ${plugin.name}" }
                collection.forEach(plugin::registerSuspendingEvents)
            }

            plugin.bStatsId.invokeIfNotNull {
                minix.log.debug { "Registering bStats for ${plugin.name}" }
                cache.metrics = Metrics(plugin, it)
            }

            plugin.invokeIfOverrides(SusPlugin::handleAfterLoad.name) {
                minix.log.debug { "Running handleAfterLoad for ${plugin.name}" }
                plugin.handleAfterLoad()
            }

            loadedPlugins += plugin::class to plugin
        }
        coroutineService.getCoroutineSession(plugin).wakeUpBlockService.isManipulatedServerHeartBeatEnabled = false
    }

    override fun unloadPlugin(plugin: MinixPlugin) {
        runBlocking {
            CoroutineScheduler.activateTasks(plugin)?.takeIf(IntArray::isNotEmpty)?.let {
                minix.log.debug { "Cancelling ${it.size} tasks for ${plugin.name}" }
                it.forEach { id -> CoroutineScheduler.cancelTask(id) }
            }

            val cache = pluginCache.getIfPresent(plugin)

            cache?.loadedExtensions?.takeIf(MutableList<*>::isNotEmpty)?.let { ex ->
                minix.log.debug { "Unloading ${ex.size} extensions for ${plugin.name}" }
                plugin.shutdownInOrder()
            }

            plugin.invokeIfOverrides(SusPlugin::handleDisable.name) {
                minix.log.debug { "Running handleDisable for ${plugin.name}" }
                plugin.handleDisable()
            }

            loadedPlugins -= plugin::class
        }
    }

    private fun MinixPlugin.loadReflection() {
        val reflections = Reflections(this::class.getFullName().split(".")[1], Scanners.TypesAnnotated)
        reflections.getTypesAnnotatedWith(MappedConfig::class.java)
            .filter {
                if (it.classLoader != this::class.java.classLoader) {
                    minix.log.debug { "Skipping ${it.name} because it's not loaded by ${this.name}." }
                    false
                } else true
            }
            .forEach {
                minix.log.debug { "Found MappedConfig [${it.simpleName}] from ${this.name}" }
                dev.racci.minix.api.utils.getKoin().get<DataService>().configurations[it.kotlin] // Call the cache so we load can have it loaded.
            }
        reflections.getTypesAnnotatedWith(MappedExtension::class.java)
            .filter { clazz ->
                when {
                    clazz.classLoader != this::class.java.classLoader -> {
                        minix.log.debug { "Skipping ${clazz.name} because it's not loaded by ${this.name}." }
                        false
                    }
                    clazz.isAssignableFrom(Extension::class.java) -> {
                        minix.log.debug { "Found MappedExtension [${clazz.simpleName}] from ${this.name}" }
                        true
                    }
                    else -> {
                        minix.log.warn { "${clazz.name} is annotated with MappedExtension but isn't an extension!." }
                        false
                    }
                }
            }
            .forEach { clazz ->
                minix.log.debug { "Found MappedExtension [${clazz.simpleName}] from ${this.name}" }
                pluginCache[this].extensions += { plugin: MinixPlugin ->
                    try {
                        clazz.kotlin.primaryConstructor!!.call(plugin) as Extension<*>
                    } catch (e: Exception) {
                        minix.log.error(e) { "Failed to create extension ${clazz.simpleName} for ${plugin.name}" }
                        throw e
                    }
                }
            }
    }

    private inline fun <reified P : MinixPlugin> P.getSortedExtensions(): MutableList<Extension<P>> {
        val extensions = pluginCache[this].let { cache ->
            cache.extensions
                .map { it.invoke(this) }
                .filterIsInstance<Extension<P>>()
                .toMutableList().also { it.addAll(cache.loadedExtensions.toMutableList().unsafeCast()) }
        }
        val sortedExtensions = mutableListOf<Extension<P>>()
        while (extensions.isNotEmpty()) {
            val next = extensions.first()
            extensions.remove(next)
            if (next !in sortedExtensions &&
                (
                    next.dependencies.isEmpty() ||
                        next.dependencies.all { dep -> sortedExtensions.find { it::class == dep } != null }
                    )
            ) {
                log.debug { "All dependencies for ${next.name} are loaded, adding to sorted" }
                sortedExtensions.add(next)
                continue
            }

            if (next in sortedExtensions &&
                next.dependencies.any { dep -> sortedExtensions.find { it::class == dep } == null }
            ) {
                log.debug { "Dependency for ${next.name} is not loaded, reordering needed deps." }
                val index = sortedExtensions.indexOf(next)
                log.debug { "Index of ${next.name} is $index" }
                sortedExtensions.remove(next)
                val neededDepends = next.dependencies.filter { dep -> sortedExtensions.find { it::class == dep } == null }.map { exKClass -> extensions.find { it::class == exKClass }!! }
                log.debug { "Needed depends for ${next.name} are ${neededDepends.joinToString { it.name }}" }
                sortedExtensions.addAll(index, neededDepends)
                sortedExtensions.add(index + neededDepends.size + 1, next)
                log.debug { "New index of ${next.name} is ${sortedExtensions.indexOf(next)}" }
                continue
            }

            for (dependency in next.dependencies) {
                if (sortedExtensions.find { it::class == dependency } != null) {
                    log.debug { "Dependency $dependency for ${next.name} is in sorted, skipping." }
                    continue
                }
                extensions.find { it::class == dependency }?.let {
                    sortedExtensions.add(it)
                    log.debug { "Adding ${it.name} to sorted before ${next.name}" }
                }
            }
            // TODO: i shouldn't need another check here
            if (next !in sortedExtensions) {
                log.debug { "Adding ${next.name} to sorted" }
                sortedExtensions.add(next)
            } else log.debug { "Extension ${next.name} is already in sorted, skipping." }
        }
        return sortedExtensions
    }

    // TODO: Refactor the dependency system to be more efficient and less hacky maybe using a shared flow
    private suspend fun MinixPlugin.loadInOrder() {
        val cache = pluginCache[this]
        val sorted = getSortedExtensions()
        sorted.forEach { ex ->
            val module = module { single { ex } bind (ex.bindToKClass ?: ex::class) }
            loadKoinModules(module)
            ex.setState(ExtensionState.LOADING)
            try {
                withTimeout(5.seconds) {
                    ex.invokeIfOverrides(Extension<*>::handleLoad.name) { ex.handleLoad() }
                }
            } catch (e: Throwable) {
                if (e is TimeoutCancellationException) { log.warn { "Extension ${ex.name} took too longer than 5 seconds to load!" } }
                ex.setState(ExtensionState.FAILED_LOADING)
                ex.errorDependents(sorted, e)
                unloadKoinModules(module)
            }
            ex.setState(ExtensionState.LOADED)
            cache.loadedExtensions += ex
        }
        cache.extensions.clear()
    }

    private suspend fun MinixPlugin.startInOrder() {
        val cache = pluginCache[this]
        val sorted = getSortedExtensions()
        sorted.forEach { ex ->
            val module = module { single { ex } bind (ex.bindToKClass ?: ex::class) }
            if (!ex.bound) { loadKoinModules(module) }
            ex.setState(ExtensionState.ENABLING)
            try {
                withTimeout(5.seconds) {
                    ex.invokeIfOverrides(Extension<*>::handleEnable.name) { ex.handleEnable() }
                }
            } catch (e: Throwable) {
                if (e is TimeoutCancellationException) { log.warn { "Extension ${ex.name} took too longer than 5 seconds to enable!" } }
                ex.setState(ExtensionState.FAILED_ENABLING)
                ex.errorDependents(sorted, e)
                unloadKoinModules(module)
            }
            ex.setState(ExtensionState.ENABLED)
            cache.loadedExtensions += ex
        }
        cache.extensions.clear()
    }

    private suspend fun Extension<MinixPlugin>.errorDependents(
        extensions: MutableList<Extension<MinixPlugin>>,
        error: Throwable
    ) {
        val deps = extensions.filter { this::class in it.dependencies }
        log.error(error) {
            val builder = StringBuilder()
            builder.append("There was an error while loading / enabling extension ${this.name}!")
            builder.append("\n\t\tThis is not a fatal error, but it may cause other extensions to fail to load.")
            if (deps.isNotEmpty()) {
                builder.append("\n\t\tThese extensions will not be loaded:")
                deps.forEach { builder.append("\n\t\t\t${it.name}") }
            }
            builder.toString()
        }
        deps.forEach {
            it.setState(ExtensionState.FAILED_DEPENDENCIES)
        }
    }

    private suspend inline fun <reified P : MinixPlugin> P.shutdownInOrder() {
        val cache = pluginCache[this]
        for (ex in cache.loadedExtensions.asReversed()) {
            ex.setState(ExtensionState.UNLOADING)
            try {
                withTimeout(5.seconds) {
                    ex.invokeIfOverrides(Extension<*>::handleUnload.name) { ex.handleUnload() }
                }
            } catch (e: Throwable) {
                if (e is TimeoutCancellationException) {
                    log.warn { "Extension ${ex.name} took too longer than 5 seconds to unload!" }
                } else log.error(e) { "Extension ${ex.name} through an error while unloading!" }
                ex.setState(ExtensionState.FAILED_UNLOADING)
            }
            unloadKoinModules(module { single { ex } bind (ex.bindToKClass ?: ex::class) }) // TODO: This is a bit of a hack, but it works for now.
            ex.setState(ExtensionState.UNLOADED)
            cache.loadedExtensions -= ex
            cache.unloadedExtensions += ex
        }
    }
}
