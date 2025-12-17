package com.badgersmc.papermcp.bridge

import mu.KotlinLogging
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

private val logger = KotlinLogging.logger {}

/**
 * Event listener for capturing Minecraft events
 */
class EventListeners(private val bridge: MinecraftBridge) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // Log chat message
        bridge.addChatMessage(event.player.name, event.message)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val message = "${event.player.name} joined the server"
        bridge.addConsoleLog("[JOIN] $message")
        logger.debug { message }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val message = "${event.player.name} left the server"
        bridge.addConsoleLog("[QUIT] $message")
        logger.debug { message }
    }
}
