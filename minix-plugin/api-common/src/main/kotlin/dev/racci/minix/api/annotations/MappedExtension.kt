package dev.racci.minix.api.annotations

import dev.racci.minix.api.extension.Extension
import dev.racci.minix.api.plugin.MinixPlugin
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlin.reflect.KClass

/**
 * Marks an extension class.
 * The extension class must extend [Extension] and have a constructor with a single parameter of type [MinixPlugin].
 *
 * @property parent The KClass of your plugin.
 * @property name The unique name of the extension.
 * @property dependencies The other extensions that this requires to be loaded, Note this must be an [Extension] however it needs to be compiled as any KClass.
 * @property bindToKClass The class that this extension binds to, if not specified, the extension will bind to itself.
 * @property threadCount The number of threads in this extensions [ExecutorCoroutineDispatcher]
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class MappedExtension(
    val dependencies: Array<KClass<*>> = [],
    val bindToKClass: KClass<*> = Extension::class,
    val name: String = REPLACE_ME,
    val threadCount: Int = DEFAULT_THREAD_COUNT
) {
    public companion object {
        public const val REPLACE_ME: String = "CLASS_NAME_REPLACEMENT_NEEDED"
        public const val DEFAULT_THREAD_COUNT: Int = 1
    }
}
