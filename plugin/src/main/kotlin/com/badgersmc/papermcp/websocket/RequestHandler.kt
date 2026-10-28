package com.badgersmc.papermcp.websocket

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.badgersmc.papermcp.security.CommandPolicy
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.logging.Logger

private val logger = Logger.getLogger("PaperMCP-RequestHandler")
private val defaultMaintenanceCommands = listOf(
    "spark",
    "g",
    "guild",
    "lg",
    "lumaguilds",
    "nexo",
    "geyser",
    "floodgate",
    "viaversion"
)

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
                "read_file_range" -> handleReadFileRange(id, params)
                "list_directory" -> handleListDirectory(id, params)
                "file_stat" -> handleFileStat(id, params)
                "batch_read_files" -> handleBatchReadFiles(id, params)
                "search_files" -> handleSearchFiles(id, params)
                "file_hash" -> handleFileHash(id, params)
                "download_file" -> handleDownloadFile(id, params)
                "edit_file" -> handleEditFile(id, params)
                "copy_file" -> handleCopyFile(id, params)
                "move_file" -> handleMoveFile(id, params)
                "upload_file" -> handleUploadFile(id, params)
                "delete_file" -> handleDeleteFile(id, params)
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
            "uptime" to serverInfo.uptime,
            "maintenanceMode" to plugin.isMaintenanceMode()
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
        val maxConfigured = plugin.config
            .getInt("mcp.buffers.console_log_size", 5000)
            .coerceIn(100, 100_000)
        val limit = params?.get("limit")?.asInt?.coerceIn(1, maxConfigured) ?: minOf(100, maxConfigured)
        val logs = bridge.getConsoleLogs(limit)

        return createSuccess(id, mapOf(
            "logCount" to logs.size,
            "logs" to logs
        ))
    }

    private fun handleReadChat(id: Any?, params: JsonObject?): JsonObject {
        val maxConfigured = plugin.config
            .getInt("mcp.buffers.chat_log_size", 5000)
            .coerceIn(100, 100_000)
        val limit = params?.get("limit")?.asInt?.coerceIn(1, maxConfigured) ?: minOf(50, maxConfigured)
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

        val target = params.get("target")?.asString?.lowercase() ?: "broadcast"
        val prefix = params.get("prefix")?.asString

        // Audit log
        if (plugin.config.getBoolean("mcp.security.audit_logging", true)) {
            logger.info("MCP sending message to '$target': $message")
        }

        val result = bridge.sendMessage(message, target, prefix)
        return createSuccess(id, result)
    }

    private fun handleReadFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: path is required")
        }

        val maxLines = params.get("max_lines")?.asInt?.coerceIn(1, 10000) ?: 1000

        val result = bridge.readFile(path, maxLines)
        return createSuccess(id, result)
    }

    private fun handleReadFileRange(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) return createError(id, -32602, "Invalid params: path is required")
        val startLine = params.get("start_line")?.asInt?.coerceAtLeast(0) ?: 0
        val maxLines = params.get("max_lines")?.asInt?.coerceIn(1, 10_000) ?: 1000
        return createSuccess(id, bridge.readFileRange(path, startLine, maxLines))
    }

    private fun handleListDirectory(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString ?: "."
        val depth = params?.get("depth")?.asInt?.coerceIn(1, 8) ?: 1
        val maxEntries = params?.get("max_entries")?.asInt?.coerceAtLeast(1) ?: 1000
        return createSuccess(id, bridge.listDirectory(path, depth, maxEntries))
    }

    private fun handleFileStat(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) return createError(id, -32602, "Invalid params: path is required")
        return createSuccess(id, bridge.fileStat(path))
    }

    private fun handleBatchReadFiles(id: Any?, params: JsonObject?): JsonObject {
        val paths = params?.get("paths")?.asJsonArray
            ?.mapNotNull { element -> if (element.isJsonPrimitive) element.asString else null }
            ?: emptyList()
        if (paths.isEmpty()) return createError(id, -32602, "Invalid params: paths is required")
        val startLine = params?.get("start_line")?.asInt?.coerceAtLeast(0) ?: 0
        val maxLines = params?.get("max_lines_per_file")?.asInt?.coerceIn(1, 10_000) ?: 1000
        return createSuccess(id, bridge.batchReadFiles(paths, startLine, maxLines))
    }

    private fun handleSearchFiles(id: Any?, params: JsonObject?): JsonObject {
        val root = params?.get("root")?.asString ?: "."
        val query = params?.get("query")?.asString
        if (query.isNullOrBlank()) return createError(id, -32602, "Invalid params: query is required")
        val glob = params.get("file_glob")?.asString ?: "**"
        val regex = params.get("regex")?.asBoolean ?: false
        val caseSensitive = params.get("case_sensitive")?.asBoolean ?: false
        val maxResults = params.get("max_results")?.asInt?.coerceAtLeast(1) ?: 100
        return createSuccess(
            id,
            bridge.searchFiles(root, query, glob, regex, caseSensitive, maxResults)
        )
    }

    private fun handleFileHash(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) return createError(id, -32602, "Invalid params: path is required")
        val algorithm = params.get("algorithm")?.asString ?: "SHA-256"
        return createSuccess(id, bridge.hashFile(path, algorithm))
    }

    private fun handleDownloadFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) return createError(id, -32602, "Invalid params: path is required")
        val offset = params.get("offset")?.asLong ?: 0L
        if (offset < 0L) return createError(id, -32602, "Invalid params: offset must be >= 0")
        val maxBytes = params.get("max_bytes")?.asInt?.coerceAtLeast(1) ?: (1024 * 1024)
        return createSuccess(id, bridge.downloadFileChunk(path, offset, maxBytes))
    }

    private fun handleEditFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: path is required")
        }

        val mode = params.get("mode")?.asString
        if (mode.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: mode is required (write, append, or replace)")
        }

        val content = params.get("content")?.asString ?: ""
        val pattern = params.get("pattern")?.asString
        val createBackup = params.get("create_backup")?.asBoolean ?: true

        val result = bridge.editFile(path, mode, content, pattern, createBackup)
        return createSuccess(id, result)
    }

    private fun handleCopyFile(id: Any?, params: JsonObject?): JsonObject {
        val source = params?.get("source")?.asString
        val destination = params?.get("destination")?.asString
        if (source.isNullOrBlank() || destination.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: source and destination are required")
        }
        val overwrite = params.get("overwrite")?.asBoolean ?: false
        return createSuccess(id, bridge.copyFile(source, destination, overwrite))
    }

    private fun handleMoveFile(id: Any?, params: JsonObject?): JsonObject {
        val source = params?.get("source")?.asString
        val destination = params?.get("destination")?.asString
        if (source.isNullOrBlank() || destination.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: source and destination are required")
        }
        val overwrite = params.get("overwrite")?.asBoolean ?: false
        return createSuccess(id, bridge.moveFile(source, destination, overwrite))
    }

    private fun handleUploadFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        val data = params?.get("data_base64")?.asString
        if (path.isNullOrBlank() || data.isNullOrBlank()) {
            return createError(id, -32602, "Invalid params: path and data_base64 are required")
        }
        val offset = params.get("offset")?.asLong ?: 0L
        if (offset < 0L) return createError(id, -32602, "Invalid params: offset must be >= 0")
        val truncate = params.get("truncate")?.asBoolean ?: (offset == 0L)
        val createBackup = params.get("create_backup")?.asBoolean ?: true
        return createSuccess(
            id,
            bridge.uploadFileChunk(path, data, offset, truncate, createBackup)
        )
    }

    private fun handleDeleteFile(id: Any?, params: JsonObject?): JsonObject {
        val path = params?.get("path")?.asString
        if (path.isNullOrBlank()) return createError(id, -32602, "Invalid params: path is required")
        val recursive = params.get("recursive")?.asBoolean ?: false
        return createSuccess(id, bridge.deleteFile(path, recursive))
    }

    private fun isCommandAllowed(command: String): Boolean {
        val config = plugin.config
        val hasMaintenanceWhitelist = config.contains("mcp.security.maintenance_command_whitelist")
        val maintenanceCommands = if (hasMaintenanceWhitelist) {
            config.getStringList("mcp.security.maintenance_command_whitelist")
        } else {
            defaultMaintenanceCommands
        }
        val normalCommands = config.getStringList("mcp.security.command_whitelist")
            .let { configured ->
                if (hasMaintenanceWhitelist) {
                    configured
                } else {
                    configured.filterNot { entry ->
                        entry.trim().lowercase().removeSuffix("*") in defaultMaintenanceCommands
                    }
                }
            }

        return CommandPolicy.isAllowed(
            commandLine = command,
            normalWhitelist = normalCommands,
            maintenanceWhitelist = maintenanceCommands,
            blacklist = config.getStringList("mcp.security.command_blacklist"),
            maintenanceMode = plugin.isMaintenanceMode(),
            allowWhitelistWildcards = config.getBoolean(
                "mcp.security.allow_command_wildcards",
                false
            )
        )
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
