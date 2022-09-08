@file:Suppress("UNUSED", "TooGenericExceptionCaught")

package dev.racci.minix.api.utils.kotlin

import dev.racci.minix.api.exceptions.LevelConversionException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

inline fun <reified T : Throwable, reified U : Any> catch(
    err: (T) -> U,
    run: () -> U
): U = try {
    run()
} catch (ex: Throwable) {
    if (ex is T) err(ex) else throw ex
}

inline fun <reified T : Throwable> catch(
    err: (T) -> Unit = { it.printStackTrace() },
    run: () -> Unit
) = catch<T, Unit>(err, run)

inline fun <reified T : Throwable, reified U : Any> catch(
    default: U,
    run: () -> U
): U = catch<T, U>({ default }, run)

inline fun <reified T : Throwable, R> catchAndReturn(
    err: (T) -> Unit = { it.printStackTrace() },
    run: () -> R
): R? = try {
    run()
} catch (ex: Exception) {
    if (ex is T) err(ex) else throw ex
    null
}

/**
 * Run a try catch and if there is an exception return false
 *
 * @param T The Throwable type
 * @param errorCallback What to run if there was an error, defaults to false
 * @param run The block to catch on
 * @return True if there was no exception, false if there was
 */
inline fun <reified T : Throwable> booleanCatch(
    errorCallback: (T) -> Boolean = { true },
    run: () -> Any?
): Boolean = try {
    run()
    true
} catch (t: Throwable) {
    if (t is T) errorCallback(t) else throw t
}

infix fun KClass<*>.doesOverride(functionName: String): Boolean {
    val function = this.functions.find { it.name == functionName }
    if (function != null) return this.doesOverride(function)

    val property = this.memberProperties.find { it.name == functionName }
    if (property != null) return this.doesOverride(property)

    return false
}

infix fun KClass<*>.doesOverride(function: KFunction<*>): Boolean {
    return this.functions.find { it == function } in declaredFunctions
}

infix fun KClass<*>.doesOverride(property: KProperty1<*, *>): Boolean {
    return this.memberProperties.find { it == property } in memberProperties
}

/**
 * Invokes the given block if the receiver overrides the supplied method.
 *
 * @param T The type of the receiver.
 * @param methodName The name of the method to check for.
 * @param block The block to invoke.
 * @return If the block was invoked.
 */
inline fun <reified T : Any> T.invokeIfOverrides(
    methodName: String,
    block: (T) -> Unit
): Boolean {
    if (this::class.doesOverride(methodName)) {
        block(this)
        return true
    }
    return false
}

inline fun <reified T : Any> T.ifOverrides(
    function: KFunction<*>,
    action: () -> Unit
): Boolean = this::class.doesOverride(function).ifTrue(action)

/**
 * Invokes the given block if the receiver is not null.
 *
 * @param T The type of the receiver.
 * @param block The block to invoke.
 * @return If the block was invoked.
 */
inline fun <T> T?.invokeIfNotNull(block: (T) -> Unit): Boolean = if (this != null) {
    block(this)
    true
} else false

/**
 * Invokes the given block if the receiver is null.
 *
 * @param T The type of the receiver.
 * @param block The block to invoke.
 * @return If the block was invoked
 */
inline fun <T> T?.invokeIfNull(block: () -> Unit): Boolean = if (this == null) {
    block()
    true
} else false

/**
 * Invokes the given block if the boolean is true.
 *
 * @param block The block to invoke.
 * @return If the block was invoked.
 */
inline fun Boolean?.ifTrue(block: () -> Unit): Boolean = if (this == true) {
    block()
    true
} else false

/**
 * Invokes the given block if the boolean is false.
 *
 * @param block The block to invoke.
 * @return If the block was invoked.
 */
inline fun Boolean?.ifFalse(block: () -> Unit): Boolean = if (this == false) {
    block()
    true
} else false

/**
 * Invokes the given block if the receiver is not empty.
 *
 * @param T The value type of the collection.
 * @param block The block to invoke.
 * @return The collection itself.
 */
inline fun <reified T> Collection<T>?.ifNotEmpty(block: (Collection<T>) -> Unit): Collection<T>? {
    if (!this.isNullOrEmpty()) block(this)
    return this
}

inline fun <reified T : () -> R, R> T.ifFulfilled(
    boolean: Boolean? = null,
    block: () -> Boolean = { false }
) { if (boolean == true || block()) this() }

infix fun <F, S, T> Pair<F, S>.to(other: T): Triple<F, S, T> = Triple(first, second, other)

/** Gets the parent kClass of a companionObject else null. */
val <T : Any> KClass<T>.companionParent get() = if (isCompanion) java.declaringClass.kotlin else null

/** Get an enum instance from the ordinal value. */
@Throws(LevelConversionException::class)
inline fun <reified E : Enum<*>> Enum.Companion.fromOrdinal(
    ordinal: Int,
    orElse: (Int) -> E = { throw LevelConversionException("Couldn't convert $ordinal to an enum of ${E::class.simpleName}") }
): E = E::class.java.enumConstants.getOrElse(ordinal, orElse)

/** Converts an invokable to a string safely. */
fun (() -> Any?).toSafeString(): String {
    return try {
        this.invoke().toString()
    } catch (e: Exception) {
        "String invocation failed: $e"
    }
}
