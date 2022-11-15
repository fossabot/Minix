package dev.racci.minix.flowbus

import dev.racci.minix.api.data.Priority
import dev.racci.minix.flowbus.receiver.EventReceiver
import kotlinx.coroutines.flow.Flow

/**
 * @see FlowBus.dropEvent
 */
public inline fun <reified T : Any> FlowBus.dropEvent(): Unit = dropEvent(T::class)

/**
 * @see FlowBus.getLastEvent
 */
public inline fun <reified T : Any> FlowBus.getLastEvent(): T? = getLastEvent(T::class)

/**
 * @see FlowBus.getFlow
 */
public inline fun <reified T : Any> FlowBus.getFlow(): Flow<T> = getFlow(T::class)

/**
 * Simplified [EventReceiver.subscribeTo] for Kotlin.
 * Type of event is automatically inferred from [callback] parameter type.
 *
 * @param skipRetained Skips event already present in the flow. This is `false` by default.
 * @param priority The priority of this listener in the events' collection. (Currently Non Functional)
 * @param ignoreCancelled Whether to ignore events that are cancelled. (Currently Non functional)
 * @param callback The callback function.
 * @return This instance of [EventReceiver] for chaining
 */
public inline fun <reified T : Any> EventReceiver.subscribe(
    skipRetained: Boolean = false,
    priority: Priority = Priority.DEFAULT,
    ignoreCancelled: Boolean = false,
    noinline callback: suspend T.() -> Unit
): EventReceiver = subscribeTo(T::class, skipRetained, priority, ignoreCancelled, callback)

/**
 * A variant of [subscribe] that uses an instance of [EventCallback] as callback.
 *
 * @param skipRetained Skips event already present in the flow. This is `false` by default
 * @param priority The priority of this listener in the events' collection. (Currently Non Functional)
 * @param ignoreCancelled Whether to ignore events that are cancelled. (Currently Non functional)
 * @param callback Interface with implemented callback function
 * @return This instance of [EventReceiver] for chaining
 * @see [subscribe]
 */
public inline fun <reified T : Any> EventReceiver.subscribe(
    skipRetained: Boolean = false,
    priority: Priority = Priority.DEFAULT,
    ignoreCancelled: Boolean = false,
    callback: EventCallback<T>
): EventReceiver = subscribeTo(T::class, skipRetained, priority, ignoreCancelled, callback)

public inline fun <reified T : Any> EventReceiver.subscribeFlow(
    skipRetained: Boolean = false,
    priority: Priority = Priority.DEFAULT,
    ignoreCancelled: Boolean = false
): Flow<T> = flowOf(T::class, skipRetained, priority, ignoreCancelled)

@Suppress("EXTENSION_SHADOWED_BY_MEMBER") // This is intentional
public inline fun <reified T : Any> EventReceiver.unsubscribe() {
    unsubscribe(T::class)
}
