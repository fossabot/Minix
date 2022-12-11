package dev.racci.minix.core.coroutine

import dev.racci.minix.api.coroutine.CoroutineSession
import dev.racci.minix.api.data.enums.EventExecutionType
import dev.racci.minix.api.plugin.MinixPlugin
import dev.racci.minix.api.plugin.WithPlugin
import dev.racci.minix.core.coroutine.dispatcher.MinecraftCoroutineDispatcher
import dev.racci.minix.core.coroutine.dispatcher.MinixCoroutineDispatcher
import dev.racci.minix.core.coroutine.dispatcher.service.EventService
import dev.racci.minix.core.coroutine.dispatcher.service.WakeUpBlockService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.koin.core.annotation.Factory
import kotlin.coroutines.CoroutineContext

@Factory([CoroutineSession::class])
internal class PaperCoroutineSession(
    plugin: MinixPlugin
) : CoroutineSession, WithPlugin<MinixPlugin> by plugin {
    private val wakeUpBlockService = WakeUpBlockService(plugin)
    private val eventService = EventService(plugin, this)

    /** Async context that stays off the main thread. */
    override val context: CoroutineContext = MinixCoroutineDispatcher(plugin, wakeUpBlockService)

    /** Sync context that stays on the main thread. */
    override val minecraftContext: CoroutineContext = MinecraftCoroutineDispatcher(plugin, wakeUpBlockService)

    override val coroutineScope: CoroutineScope = MinixCoroutineExceptionHandler(plugin)
        .let(::CoroutineScope) + SupervisorJob() + context

    override fun withManipulatedServerHeartBeat(block: suspend CoroutineScope.() -> Unit) {
        wakeUpBlockService.isManipulatedServerHeartBeatEnabled = true
        runBlocking { block() }
        wakeUpBlockService.isManipulatedServerHeartBeatEnabled = false
    }

    override suspend fun registerSuspendedListener(
        listener: Listener
    ): Unit = eventService.registerSuspendListener(listener)

    override fun fireSuspendingEvent(
        event: Event,
        executionType: EventExecutionType
    ): Collection<Job> = eventService.fireSuspendingEvent(event /*, executionType*/)

    /**
     * Launches the given function on the plugin coroutineService scope.
     * @return Cancelable coroutineService job.
     */
    override fun launch(
        context: CoroutineContext,
        parentScope: CoroutineScope,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> Unit
    ): Job = when {
        !parentScope.isActive -> Job()
        context == Dispatchers.Unconfined -> launchInternal(parentScope, context, CoroutineStart.UNDISPATCHED, block) // If the context is unconfined. Always schedule immediately.
        else -> launchInternal(parentScope, context, start, block)
    }

    override fun dispose() {
        coroutineScope.coroutineContext.cancelChildren()
        coroutineScope.cancel()

        wakeUpBlockService.dispose()
        context.cancel()
        minecraftContext.cancel()
    }

    private fun launchInternal(
        parentScope: CoroutineScope?,
        dispatcher: CoroutineContext,
        coroutineStart: CoroutineStart,
        f: suspend CoroutineScope.() -> Unit
    ): Job = (parentScope ?: coroutineScope).launch(dispatcher, coroutineStart, f)

    init {
        if (!plugin.enabled) {
            throw plugin.logger.fatal {
                """
                Plugin ${plugin.value} attempted to start a new coroutine session while being disabled.
                Dispatchers such as plugin.minecraftDispatcher and plugin.asyncDispatcher are already
                disposed of at this point and cannot be used.
                """.trimIndent()
            }
        }
    }
}