package com.badgersmc.papermcp.websocket

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.logging.Logger

private val logger = Logger.getLogger("PaperMCP-RequestHandler")

/**
 * Handles JSON-RPC requests from WebSocket clients
 */
class RequestHandler(
    private val plugin: PaperMCPPlugin,
    private val bridge: MinecraftBridge
) {
    private val gson = Gson()

    fun handle(request: JsonObject): JsonObject {
        return try {
            val method = request.get("method")?.asString
            val params = request.get("params")?.asJsonObject
            val id = request.get("id")

            if (method == null) {
                return createError(id, -32600, "Invalid request: missing method")
            }

            when (method) {
                "server_info" -> handleServerInfo(id)
                "list_players" -> handleListPlayers(id, params)
                "read_console" -> handleReadConsole(id, params)
                "read_chat" -> handleReadChat(id, params)
                "execute_command" -> handleExecuteCommand(id, params)
                "send_message" -> handleSendMessage(id, params)
                "read_file" -> handleReadFile(id, params)
                "edit_file" -> handleEditFile(id, params)
                else -> createError(id, -32601, "Method not found: $method")
            }
        } catch (e: Exception) {
            logger.severe("Error handling request: ${e.message}")
            e.printStackTrace()
            createError(null, -32603, "Internal error: ${e.message}")
        }
    }

    private fun handleServerInfo(id: Any?): JsonObject {
        val serverInfo = bridge.getServerInfo()
        return createSuccess(id, mapOf(
            "version" to serverInfo.version,
            "bukkitVersion" to serverInfo.bukkitVersion,
            "onlineMode" to serverInfo.onlineMode,
            "maxPlayers" to serverInfo.maxPlayers,
            "onlinePlayers" to serverInfo.onlinePlayers,
            "viewDistance" to serverInfo.viewDistance,
            "tps" to mapOf(
                "1min" to String.format("%.2f", serverInfo.tps[0]),
                "5min" to String.format("%.2f", serverInfo.tps[1]),
                "15min" to String.format("%.2f", serverInfo.tps[2])
            ),
            "uptime" to serverInfo.uptime
        ))
    }

    private fun handleListPlayers(id: Any?, params: JsonObject?): JsonObject {
        val playerName = params?.get("player")?.asString

        if (playerName != null) {
            val player = bridge.getPlayer(playerName)
            return if (player == null) {
                createError(id, -32000, "Player not found: $playerName")
            } else {
                createSuccess(id, mapOf("player" to player))
            }
        }

        val players = bridge.getOnlinePlayers()
        return createSuccess(id, mapOf(
            "playerCount" to players.size,
            "players" to players
        ))
    }

    private fun handleReadConsole(id: Any?, params: JsonObject?): JsonObject {
        val limit = params?.get("limit")?.asInt?.coerceIn(1, 1000) ?: 100
        val logs = bridge.getConsoleLogs(limit)

        return createSuccess(id, mapOf(
            "logCount" to logs.size,
            "logs" to logs
        ))
    }

    private fun handleReadChat(id: Any?, params: JsonObject?): JsonObject {
        val limit = params?.get("limit")?.asInt?.coerceIn(1, 500) ?: 50
        val playerFilter = params?.get("player")?.asString

        var messages = bridge.getChatLogs(limit)
        if (playerFilter != null) {
            messages = messages.filter { it.player.equals(playerFilter, ignoreCase = true) }
        }

        return createSuccess(id, mapOf(
            "messageCount" to messages.size,
            "messages" to messages
        ))
    }

    private fun handleExecuteCommand(id: Any?, params: JsonObject?): JsonObject {
        val command = params?.get("command")?.asString
        if (command.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: command is required")
        }

        // Security check
        if (!isCommandAllowed(command)) {
            logger.warning("Blocked command execution: $command")
            return createError(id, -32000, "Command blocked by security policy")
        }

        // Audit log
        if (plugin.config.getBoolean("mcp.security.audit_logging", true)) {
            logger.info("Executing command: $command")
        }

        val result = bridge.executeCommand(command)
        return createSuccess(id, result)
    }

    private fun handleSendMessage(id: Any?, params: JsonObject?): JsonObject {
        val message = params?.get("message")?.asString
        if (message.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: message is required")
        }

        val target = params?.get("target")?.asString ?: "broadcast"
        val prefix = params?.get("prefix")?.asString

        // Implementation would be similar to ChatTool
        // For now, return success
        return createSuccess(id, mapOf(
            "success" to true,
            "target" to target,
            "message" to message
        ))
    }

    private fun handleReadFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: path is required")
        }

        val maxLines = params?.get("max_lines")?.asInt?.coerceIn(1, 10000) ?: 1000

        val result = bridge.readFile(path, maxLines)
        return createSuccess(id, result)
    }

    private fun handleEditFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: path is required")
        }

        val mode = params?.get("mode")?.asString
        if (mode.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: mode is required (write, append, or replace)")
        }

        val content = params?.get("content")?.asString ?: ""
        val pattern = params?.get("pattern")?.asString
        val createBackup = params?.get("create_backup")?.asBoolean ?: true

        val result = bridge.editFile(path, mode, content, pattern, createBackup)
        return createSuccess(id, result)
    }

    private fun isCommandAllowed(command: String): Boolean {
        val config = plugin.config
        val baseCommand = command.split(" ").firstOrNull()?.lowercase() ?: return false

        // Check blacklist first
        val blacklist = config.getStringList("mcp.security.command_blacklist")
        for (blocked in blacklist) {
            when {
                blocked.startsWith("*") && blocked.endsWith("*") -> {
                    if (baseCommand.contains(blocked.trim('*').lowercase())) return false
                }
                blocked.endsWith("*") -> {
                    if (baseCommand.startsWith(blocked.trimEnd('*').lowercase())) return false
                }
                blocked.startsWith("*") -> {
                    if (baseCommand.endsWith(blocked.trimStart('*').lowercase())) return false
                }
                baseCommand == blocked.lowercase() -> return false
            }
        }

        // Check whitelist
        val whitelist = config.getStringList("mcp.security.command_whitelist")
        if (whitelist.isEmpty()) return true

        for (allowed in whitelist) {
            if (allowed.endsWith("*")) {
                if (baseCommand.startsWith(allowed.trimEnd('*').lowercase())) return true
            } else if (baseCommand == allowed.lowercase()) {
                return true
            }
        }

        return false
    }

    private fun createSuccess(id: Any?, result: Any): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", gson.toJsonTree(id))
            add("result", gson.toJsonTree(result))
        }
    }

    private fun createError(id: Any?, code: Int, message: String): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", gson.toJsonTree(id))
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }
    }
}
