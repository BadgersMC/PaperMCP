package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool to read chat history
 */
class ChatHistoryTool(private val bridge: MinecraftBridge) : MCPTool {

    override val name = "minecraft_read_chat"

    override val description = "Read recent chat messages from players on the server"

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("limit", JsonObject().apply {
                addProperty("type", "integer")
                addProperty("description", "Maximum number of chat messages to return (default: 50, max: 500)")
                addProperty("default", 50)
            })
            add("player", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Optional: filter messages by specific player name")
            })
        })
        add("required", com.google.gson.JsonArray())
    }

    override fun execute(arguments: JsonObject): Any {
        val limit = arguments.get("limit")?.asInt?.coerceIn(1, 500) ?: 50
        val playerFilter = arguments.get("player")?.asString

        var chatMessages = bridge.getChatLogs(limit)

        // Filter by player if specified
        if (playerFilter != null) {
            chatMessages = chatMessages.filter {
                it.player.equals(playerFilter, ignoreCase = true)
            }
        }

        val dateFormat = SimpleDateFormat("HH:mm:ss")

        return mapOf(
            "messageCount" to chatMessages.size,
            "messages" to chatMessages.map { msg ->
                mapOf(
                    "timestamp" to dateFormat.format(Date(msg.timestamp)),
                    "player" to msg.player,
                    "message" to msg.message
                )
            }
        )
    }
}
