package com.badgersmc.papermcp.bridge

import com.badgersmc.papermcp.PaperMCPPlugin
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Logger

private val logger = Logger.getLogger("PaperMCP")

class MinecraftBridge(private val plugin: PaperMCPPlugin) {

    private val chatLogBuffer = ConcurrentLinkedQueue<ChatMessage>()
    private val maxBufferSize = 1000
    private val serverStartTime = System.currentTimeMillis()

    init {
        Bukkit.getPluginManager().registerEvents(EventListeners(this), plugin)
        logger.info("MinecraftBridge initialized with event listeners")
    }

    fun getServerInfo(): ServerInfo {
        return ServerInfo(
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

    fun getOnlinePlayers(): List<PlayerInfo> {
        return Bukkit.getOnlinePlayers().map { player ->
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

    fun getPlayer(nameOrUuid: String): PlayerInfo? {
        val player = Bukkit.getOnlinePlayers().firstOrNull {
            it.name.equals(nameOrUuid, ignoreCase = true) ||
            it.uniqueId.toString().equals(nameOrUuid, ignoreCase = true)
        } ?: return null

        return PlayerInfo(
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

            logFile.readLines().takeLast(limit)
        } catch (e: Exception) {
            logger.severe("Error reading console logs: ${e.message}")
            listOf("Error reading logs: ${e.message}")
        }
    }

    fun addChatMessage(player: String, message: String) {
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
            // Check if we're on the main thread
            if (Bukkit.isPrimaryThread()) {
                // Already on main thread, execute directly
                val result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.trimStart('/'))
                CommandResult(
                    success = result,
                    command = command,
                    message = if (result) "Command executed successfully" else "Command execution failed"
                )
            } else {
                // Not on main thread, schedule and wait
                val result = Bukkit.getScheduler().callSyncMethod(plugin) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.trimStart('/'))
                }.get()

                CommandResult(
                    success = result,
                    command = command,
                    message = if (result) "Command executed successfully" else "Command execution failed"
                )
            }
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

    fun readFile(path: String, maxLines: Int = 1000): FileReadResult {
        return try {
            val file = java.io.File(path)

            // Security check: prevent reading files outside server directory
            val canonicalPath = file.canonicalPath
            val serverPath = java.io.File(".").canonicalPath

            if (!canonicalPath.startsWith(serverPath)) {
                return FileReadResult(
                    success = false,
                    path = path,
                    message = "Access denied: Path is outside server directory",
                    lines = emptyList()
                )
            }

            if (!file.exists()) {
                return FileReadResult(
                    success = false,
                    path = path,
                    message = "File not found",
                    lines = emptyList()
                )
            }

            if (!file.isFile) {
                return FileReadResult(
                    success = false,
                    path = path,
                    message = "Path is not a file",
                    lines = emptyList()
                )
            }

            if (file.length() > 10 * 1024 * 1024) { // 10MB limit
                return FileReadResult(
                    success = false,
                    path = path,
                    message = "File too large (>10MB)",
                    lines = emptyList()
                )
            }

            val lines = file.readLines().take(maxLines)

            FileReadResult(
                success = true,
                path = canonicalPath,
                message = "File read successfully (${lines.size} lines)",
                lines = lines,
                totalLines = file.readLines().size,
                truncated = file.readLines().size > maxLines
            )
        } catch (e: Exception) {
            logger.severe("Error reading file: $path - ${e.message}")
            FileReadResult(
                success = false,
                path = path,
                message = "Error: ${e.message}",
                lines = emptyList()
            )
        }
    }

    fun editFile(path: String, mode: String, content: String, pattern: String? = null, createBackup: Boolean = true): FileEditResult {
        return try {
            val file = java.io.File(path)

            // Security check: prevent editing files outside server directory
            val canonicalPath = file.canonicalPath
            val serverPath = java.io.File(".").canonicalPath

            if (!canonicalPath.startsWith(serverPath)) {
                return FileEditResult(
                    success = false,
                    path = path,
                    message = "Access denied: Path is outside server directory"
                )
            }

            // Check file size limit for existing files
            if (file.exists() && file.isFile && file.length() > 10 * 1024 * 1024) {
                return FileEditResult(
                    success = false,
                    path = path,
                    message = "File too large (>10MB)"
                )
            }

            // Create backup if requested and file exists
            var backupPath: String? = null
            if (createBackup && file.exists() && file.isFile) {
                val backupFile = java.io.File("${file.absolutePath}.backup")
                file.copyTo(backupFile, overwrite = true)
                backupPath = backupFile.absolutePath
            }

            when (mode) {
                "write" -> {
                    // Replace entire file
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    FileEditResult(
                        success = true,
                        path = canonicalPath,
                        message = "File written successfully",
                        backupPath = backupPath
                    )
                }
                "append" -> {
                    // Append to file
                    file.parentFile?.mkdirs()
                    file.appendText(content)
                    FileEditResult(
                        success = true,
                        path = canonicalPath,
                        message = "Content appended successfully",
                        backupPath = backupPath
                    )
                }
                "replace" -> {
                    // Find and replace pattern
                    if (pattern.isNullOrBlank()) {
                        return FileEditResult(
                            success = false,
                            path = path,
                            message = "Pattern is required for replace mode"
                        )
                    }

                    if (!file.exists()) {
                        return FileEditResult(
                            success = false,
                            path = path,
                            message = "File not found"
                        )
                    }

                    val originalContent = file.readText()
                    val newContent = originalContent.replace(pattern, content)
                    val replacements = (originalContent.length - newContent.length) / (pattern.length - content.length)

                    file.writeText(newContent)

                    FileEditResult(
                        success = true,
                        path = canonicalPath,
                        message = "Replaced $replacements occurrence(s)",
                        backupPath = backupPath,
                        replacements = replacements
                    )
                }
                else -> {
                    FileEditResult(
                        success = false,
                        path = path,
                        message = "Invalid mode: $mode (must be 'write', 'append', or 'replace')"
                    )
                }
            }
        } catch (e: Exception) {
            logger.severe("Error editing file: $path - ${e.message}")
            e.printStackTrace()
            FileEditResult(
                success = false,
                path = path,
                message = "Error: ${e.message}"
            )
        }
    }
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
