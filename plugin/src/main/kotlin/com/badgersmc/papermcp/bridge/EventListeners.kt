package com.badgersmc.papermcp.bridge

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventListeners(private val bridge: MinecraftBridge) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        bridge.addChatMessage(event.player.name, event.message)
    }

    // Join/quit events are already logged to latest.log by the server
}
