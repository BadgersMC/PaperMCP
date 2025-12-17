package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.PaperMCPPlugin
import com.google.gson.JsonObject
import mu.KotlinLogging
import org.bukkit.Bukkit
import org.bukkit.entity.Player

private val logger = KotlinLogging.logger {}

/**
 * Tool to send chat messages to players or broadcast to server
 */
class ChatTool(private val plugin: PaperMCPPlugin) : MCPTool {

    override val name = "minecraft_send_message"

    override val description = """
        Send a message to a specific player or broadcast to all players.
        Use 'broadcast' as target to send to everyone, or specify a player name.
        Messages are sent as if from the server/console.
    """.trimIndent()

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("message", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "The message to send")
            })
            add("target", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Target player name, or 'broadcast' for all players (default: broadcast)")
                addProperty("default", "broadcast")
            })
            add("prefix", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Optional prefix for the message (e.g., '[AI]', '[Server]')")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("message")
        })
    }

    override fun execute(arguments: JsonObject): Any {
        val message = arguments.get("message")?.asString
        if (message.isNullOrBlank()) {
            return mapOf(
                "success" to false,
                "error" to "Message cannot be empty"
            )
        }

        val target = arguments.get("target")?.asString?.lowercase() ?: "broadcast"
        val prefix = arguments.get("prefix")?.asString?.let { "§7$it§r " } ?: ""

        // Audit log
        if (plugin.config.getBoolean("mcp.security.audit_logging", true)) {
            logger.info { "MCP sending message to '$target': $message" }
        }

        return try {
            when (target) {
                "broadcast", "all", "*" -> {
                    // Broadcast to all players
                    val formattedMessage = "$prefix$message"
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        Bukkit.broadcastMessage(formattedMessage)
                    })

                    mapOf(
                        "success" to true,
                        "target" to "broadcast",
                        "recipients" to Bukkit.getOnlinePlayers().size,
                        "message" to formattedMessage
                    )
                }
                else -> {
                    // Send to specific player
                    val player = Bukkit.getOnlinePlayers().firstOrNull {
                        it.name.equals(target, ignoreCase = true)
                    }

                    if (player == null) {
                        return mapOf(
                            "success" to false,
                            "error" to "Player not found: $target",
                            "hint" to "Player may be offline or name is incorrect"
                        )
                    }

                    val formattedMessage = "$prefix$message"
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.sendMessage(formattedMessage)
                    })

                    mapOf(
                        "success" to true,
                        "target" to player.name,
                        "recipients" to 1,
                        "message" to formattedMessage
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending message" }
            mapOf(
                "success" to false,
                "error" to "Failed to send message: ${e.message}"
            )
        }
    }
}
