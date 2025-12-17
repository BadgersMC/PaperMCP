package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.JsonObject

/**
 * Tool to list online players with detailed information
 */
class PlayerListTool(private val bridge: MinecraftBridge) : MCPTool {

    override val name = "minecraft_list_players"

    override val description = "List all online players with detailed information including health, location, gamemode, and more"

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("player", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Optional: specific player name or UUID to get info about")
            })
        })
        add("required", com.google.gson.JsonArray())
    }

    override fun execute(arguments: JsonObject): Any {
        // Check if specific player requested
        val playerName = arguments.get("player")?.asString

        if (playerName != null) {
            val player = bridge.getPlayer(playerName)
            if (player == null) {
                return mapOf(
                    "error" to "Player not found: $playerName"
                )
            }

            return mapOf(
                "player" to formatPlayer(player)
            )
        }

        // Return all players
        val players = bridge.getOnlinePlayers()

        return mapOf(
            "playerCount" to players.size,
            "players" to players.map { formatPlayer(it) }
        )
    }

    private fun formatPlayer(player: com.badgersmc.papermcp.bridge.PlayerInfo): Map<String, Any> {
        return mapOf(
            "uuid" to player.uuid,
            "name" to player.name,
            "health" to "${String.format("%.1f", player.health)}/${String.format("%.1f", player.maxHealth)}",
            "foodLevel" to player.foodLevel,
            "level" to player.level,
            "experience" to String.format("%.2f%%", player.exp * 100),
            "gameMode" to player.gameMode,
            "location" to mapOf(
                "world" to player.location.world,
                "x" to String.format("%.2f", player.location.x),
                "y" to String.format("%.2f", player.location.y),
                "z" to String.format("%.2f", player.location.z)
            ),
            "ping" to "${player.ping}ms",
            "isOp" to player.isOp
        )
    }
}
