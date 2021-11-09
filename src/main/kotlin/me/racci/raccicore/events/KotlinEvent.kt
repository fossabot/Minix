package me.racci.raccicore.events

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.world.WorldEvent

@Deprecated("Deprecated for naming consistency", ReplaceWith("KEvent"))
typealias KotlinEvent = KEvent

/**
 * Represents an event.
 *
 * ## Includes Pre done handlers and cancellable params
 *
 * @param async If the event is Asynchronous.
 */
abstract class KEvent(
    async: Boolean = false
): Event(async), Cancellable {

    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) {cancelled = cancel}

    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = org.bukkit.event.HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }

}

/**
 * Represents a player event.
 *
 * ## Includes Pre done handlers and cancellable params
 *
 * @param player The player of the event.
 * @param async If the event is Asynchronous.
 */
abstract class KPlayerEvent(
    player: Player,
    async: Boolean = false
): PlayerEvent(player, async), Cancellable {

    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) {cancelled = cancel}

    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = org.bukkit.event.HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }

}

/**
 * Represents a World event,
 * Includes variables [isOverworld] [isNether] and [isEnd]
 * These make it easy to find what world type the event is within.
 *
 * ## Includes Pre done handlers and cancellable params
 *
 * @param world The world which this event happened in.
 * @param async If the event is Asynchronous,
 */
abstract class KWordEvent(
    world: World,
    async: Boolean = false,
): WorldEvent(async, world), Cancellable {

    val isOverworld: Boolean
        get() = world.environment == World.Environment.NORMAL
    val isNether: Boolean
        get() = world.environment == World.Environment.NETHER
    val isEnd: Boolean
        get() = world.environment == World.Environment.THE_END
    val isCustom: Boolean
        get() = world.environment == World.Environment.CUSTOM

    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) {cancelled = cancel}

    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = org.bukkit.event.HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }

}