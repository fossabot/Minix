package dev.racci.minix.api.plugin

import org.bukkit.plugin.Plugin
import kotlin.reflect.KClass

interface SusPlugin : Plugin {

    val bStatsId: Int?
    val bindToKClass: KClass<out MinixPlugin>?

    /**
     * This is called when the server picks up your Plugin and has begun loading it.
     */
    suspend fun handleLoad() {}

    /**
     * This is called Once the Plugin is ready to accept and register
     * events, commands etc.
     */
    suspend fun handleEnable() {}

    /**
     * This will be called after your Plugin is done loading
     * and Minix has finished its loading process for your Plugin.
     */
    suspend fun handleAfterLoad() {}

    /**
     * This is triggered when your Plugin is being disabled by the Server,
     * Please use this to clean up your Plugin to not leak resources.
     */
    suspend fun handleDisable() {}
}
