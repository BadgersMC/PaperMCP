package com.badgersmc.papermcp.bridge

import com.badgersmc.papermcp.PaperMCPPlugin
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Logger

private val logger = Logger.getLogger("PaperMCP")

class MinecraftBridge(private val plugin: PaperMCPPlugin) {

    private val chatLogBuffer = ConcurrentLinkedQueue<ChatMessage>()
    private val serverStartTime = System.currentTimeMillis()
    private val fileService = ServerFileService(plugin)

    init {
        Bukkit.getPluginManager().registerEvents(EventListeners(this), plugin)
        logger.info("MinecraftBridge initialized with event listeners")
    }

    private fun <T> runSync(action: () -> T): T {
        if (Bukkit.isPrimaryThread()) return action()
        return Bukkit.getScheduler().callSyncMethod(plugin) { action() }.get()
    }

    fun getServerInfo(): ServerInfo = runSync {
        ServerInfo(
            version = Bukkit.getVersion(),
            bukkitVersion = Bukkit.getBukkitVersion(),
            onlineMode = Bukkit.getOnlineMode(),
            maxPlayers = Bukkit.getMaxPlayers(),
            onlinePlayers = Bukkit.getOnlinePlayers().size,
            viewDistance = Bukkit.getViewDistance(),
            simulationDistance = Bukkit.getSimulationDistance(),
            tps = getTPS(),
            uptime = System.currentTimeMillis() - serverStartTime
        )
    }

    private fun getTPS(): DoubleArray {
        return try {
            Bukkit.getServer()::class.java.getMethod("getTPS").invoke(Bukkit.getServer()) as DoubleArray
        } catch (e: Exception) {
            doubleArrayOf(20.0, 20.0, 20.0)
        }
    }

    fun getOnlinePlayers(): List<PlayerInfo> = runSync {
        Bukkit.getOnlinePlayers().map { player ->
            PlayerInfo(
                uuid = player.uniqueId.toString(),
                name = player.name,
                health = player.health,
                maxHealth = player.maxHealth,
                gameMode = player.gameMode.name,
                location = LocationInfo(
                    world = player.world.name,
                    x = player.location.x,
                    y = player.location.y,
                    z = player.location.z
                )
            )
        }
    }

    fun getPlayer(nameOrUuid: String): PlayerInfo? = runSync {
        val player = Bukkit.getOnlinePlayers().firstOrNull {
            it.name.equals(nameOrUuid, ignoreCase = true) ||
                it.uniqueId.toString().equals(nameOrUuid, ignoreCase = true)
        } ?: return@runSync null

        PlayerInfo(
            uuid = player.uniqueId.toString(),
            name = player.name,
            health = player.health,
            maxHealth = player.maxHealth,
            gameMode = player.gameMode.name,
            location = LocationInfo(
                world = player.world.name,
                x = player.location.x,
                y = player.location.y,
                z = player.location.z
            )
        )
    }

    fun getConsoleLogs(limit: Int = 100): List<String> {
        return try {
            val logFile = java.io.File("logs/latest.log")
            if (!logFile.exists()) {
                return listOf("Log file not found")
            }

            val safeLimit = limit.coerceAtLeast(1)
            val tail = ArrayDeque<String>(safeLimit)
            logFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (tail.size == safeLimit) tail.removeFirst()
                    tail.addLast(line)
                }
            }
            tail.toList()
        } catch (e: Exception) {
            logger.severe("Error reading console logs: ${e.message}")
            listOf("Error reading logs: ${e.message}")
        }
    }

    fun addChatMessage(player: String, message: String) {
        val maxBufferSize = plugin.config
            .getInt("mcp.buffers.chat_log_size", 5000)
            .coerceIn(100, 100_000)
        chatLogBuffer.add(ChatMessage(System.currentTimeMillis(), player, message))
        while (chatLogBuffer.size > maxBufferSize) {
            chatLogBuffer.poll()
        }
    }

    fun getChatLogs(limit: Int = 100): List<ChatMessage> {
        return chatLogBuffer.toList().takeLast(limit)
    }

    fun executeCommand(command: String): CommandResult {
        return try {
            val result = runSync {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.trimStart('/'))
            }
            CommandResult(
                success = result,
                command = command,
                message = if (result) "Command executed successfully" else "Command execution failed"
            )
        } catch (e: Exception) {
            logger.severe("Error executing command: $command - ${e.message}")
            e.printStackTrace()
            CommandResult(
                success = false,
                command = command,
                message = "Error: ${e.message}",
                error = e.toString()
            )
        }
    }

    fun sendMessage(message: String, target: String = "broadcast", prefix: String? = null): MessageResult {
        return try {
            val formattedPrefix = prefix?.let { "§7$it§r " } ?: ""
            val formattedMessage = "$formattedPrefix$message"

            runSync {
                when (target) {
                    "broadcast", "all", "*" -> {
                        Bukkit.broadcastMessage(formattedMessage)
                        MessageResult(
                            success = true,
                            target = "broadcast",
                            recipients = Bukkit.getOnlinePlayers().size,
                            message = formattedMessage
                        )
                    }
                    else -> {
                        val player = Bukkit.getOnlinePlayers().firstOrNull {
                            it.name.equals(target, ignoreCase = true)
                        }

                        if (player == null) {
                            MessageResult(
                                success = false,
                                target = target,
                                recipients = 0,
                                message = formattedMessage,
                                error = "Player not found: $target"
                            )
                        } else {
                            player.sendMessage(formattedMessage)
                            MessageResult(
                                success = true,
                                target = player.name,
                                recipients = 1,
                                message = formattedMessage
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("Error sending message: ${e.message}")
            e.printStackTrace()
            MessageResult(
                success = false,
                target = target,
                recipients = 0,
                message = message,
                error = "Error: ${e.message}"
            )
        }
    }

    fun readFile(path: String, maxLines: Int = 1000): FileReadResult =
        fileService.readFile(path, maxLines)

    fun readFileRange(path: String, startLine: Int, maxLines: Int): FileReadResult =
        fileService.readFileRange(path, startLine, maxLines)

    fun listDirectory(path: String, depth: Int, maxEntries: Int): Map<String, Any?> =
        fileService.listDirectory(path, depth, maxEntries)

    fun fileStat(path: String): Map<String, Any?> =
        fileService.stat(path)

    fun batchReadFiles(paths: List<String>, startLine: Int, maxLinesPerFile: Int): Map<String, Any?> =
        fileService.batchRead(paths, startLine, maxLinesPerFile)

    fun hashFile(path: String, algorithm: String): Map<String, Any?> =
        fileService.hash(path, algorithm)

    fun downloadFileChunk(path: String, offset: Long, maxBytes: Int): Map<String, Any?> =
        fileService.downloadChunk(path, offset, maxBytes)

    fun searchFiles(
        rootPath: String,
        query: String,
        fileGlob: String,
        regex: Boolean,
        caseSensitive: Boolean,
        maxResults: Int
    ): Map<String, Any?> =
        fileService.search(rootPath, query, fileGlob, regex, caseSensitive, maxResults)

    fun editFile(
        path: String,
        mode: String,
        content: String,
        pattern: String? = null,
        createBackup: Boolean = true
    ): FileEditResult =
        fileService.editFile(path, mode, content, pattern, createBackup)

    fun copyFile(source: String, destination: String, overwrite: Boolean): Map<String, Any?> =
        fileService.copy(source, destination, overwrite)

    fun moveFile(source: String, destination: String, overwrite: Boolean): Map<String, Any?> =
        fileService.move(source, destination, overwrite)

    fun uploadFileChunk(
        path: String,
        dataBase64: String,
        offset: Long,
        truncate: Boolean,
        createBackup: Boolean
    ): Map<String, Any?> =
        fileService.uploadChunk(path, dataBase64, offset, truncate, createBackup)

    fun deleteFile(path: String, recursive: Boolean): Map<String, Any?> =
        fileService.delete(path, recursive)
}

data class ServerInfo(
    val version: String,
    val bukkitVersion: String,
    val onlineMode: Boolean,
    val maxPlayers: Int,
    val onlinePlayers: Int,
    val viewDistance: Int,
    val simulationDistance: Int,
    val tps: DoubleArray,
    val uptime: Long
)

data class PlayerInfo(
    val uuid: String,
    val name: String,
    val health: Double,
    val maxHealth: Double,
    val gameMode: String,
    val location: LocationInfo
)

data class LocationInfo(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double
)

data class ChatMessage(
    val timestamp: Long,
    val player: String,
    val message: String
)

data class CommandResult(
    val success: Boolean,
    val command: String,
    val message: String,
    val error: String? = null
)

data class MessageResult(
    val success: Boolean,
    val target: String,
    val recipients: Int,
    val message: String,
    val error: String? = null
)

data class FileReadResult(
    val success: Boolean,
    val path: String,
    val message: String,
    val lines: List<String>,
    val totalLines: Int? = null,
    val truncated: Boolean = false
)

data class FileEditResult(
    val success: Boolean,
    val path: String,
    val message: String,
    val backupPath: String? = null,
    val replacements: Int? = null
)
