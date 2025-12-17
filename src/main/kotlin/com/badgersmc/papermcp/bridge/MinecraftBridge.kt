package com.badgersmc.papermcp.bridge

import com.badgersmc.papermcp.PaperMCPPlugin
import mu.KotlinLogging
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentLinkedQueue

private val logger = KotlinLogging.logger {}

/**
 * Bridge between MCP server and Minecraft server
 * Provides safe access to Minecraft server data and operations
 */
class MinecraftBridge(private val plugin: PaperMCPPlugin) {

    private val consoleLogBuffer = ConcurrentLinkedQueue<String>()
    private val chatLogBuffer = ConcurrentLinkedQueue<ChatMessage>()
    private val maxBufferSize = 1000

    init {
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(EventListeners(this), plugin)
        logger.info { "MinecraftBridge initialized with event listeners" }
    }

    // ===== Server Information =====

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
            uptime = getUptime()
        )
    }

    private fun getTPS(): DoubleArray {
        // Paper provides TPS via reflection or direct API
        return try {
            Bukkit.getServer()::class.java.getMethod("getTPS").invoke(Bukkit.getServer()) as DoubleArray
        } catch (e: Exception) {
            doubleArrayOf(20.0, 20.0, 20.0) // Default if not available
        }
    }

    private fun getUptime(): Long {
        // Uptime in milliseconds
        return System.currentTimeMillis() - plugin.server.startTime
    }

    // ===== Player Information =====

    fun getOnlinePlayers(): List<PlayerInfo> {
        return Bukkit.getOnlinePlayers().map { player ->
            PlayerInfo(
                uuid = player.uniqueId.toString(),
                name = player.name,
                displayName = player.displayName(),
                health = player.health,
                maxHealth = player.maxHealth,
                foodLevel = player.foodLevel,
                level = player.level,
                exp = player.exp,
                gameMode = player.gameMode.name,
                location = LocationInfo(
                    world = player.world.name,
                    x = player.location.x,
                    y = player.location.y,
                    z = player.location.z,
                    yaw = player.location.yaw,
                    pitch = player.location.pitch
                ),
                ping = player.ping,
                isOp = player.isOp
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
            displayName = player.displayName(),
            health = player.health,
            maxHealth = player.maxHealth,
            foodLevel = player.foodLevel,
            level = player.level,
            exp = player.exp,
            gameMode = player.gameMode.name,
            location = LocationInfo(
                world = player.world.name,
                x = player.location.x,
                y = player.location.y,
                z = player.location.z,
                yaw = player.location.yaw,
                pitch = player.location.pitch
            ),
            ping = player.ping,
            isOp = player.isOp
        )
    }

    // ===== Console Logs =====

    fun addConsoleLog(message: String) {
        consoleLogBuffer.add(message)
        while (consoleLogBuffer.size > maxBufferSize) {
            consoleLogBuffer.poll()
        }
    }

    fun getConsoleLogs(limit: Int = 100): List<String> {
        return consoleLogBuffer.takeLast(limit)
    }

    // ===== Chat Logs =====

    fun addChatMessage(player: String, message: String) {
        chatLogBuffer.add(ChatMessage(System.currentTimeMillis(), player, message))
        while (chatLogBuffer.size > maxBufferSize) {
            chatLogBuffer.poll()
        }
    }

    fun getChatLogs(limit: Int = 100): List<ChatMessage> {
        return chatLogBuffer.takeLast(limit)
    }

    // ===== Command Execution =====

    fun executeCommand(command: String): CommandResult {
        return try {
            // Execute command on main thread
            val result = Bukkit.getScheduler().callSyncMethod(plugin) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            }.get()

            CommandResult(
                success = result,
                command = command,
                message = if (result) "Command executed successfully" else "Command execution failed"
            )
        } catch (e: Exception) {
            logger.error(e) { "Error executing command: $command" }
            CommandResult(
                success = false,
                command = command,
                message = "Error: ${e.message}",
                error = e.toString()
            )
        }
    }

    // ===== World Information =====

    fun getWorlds(): List<WorldInfo> {
        return Bukkit.getWorlds().map { world ->
            WorldInfo(
                name = world.name,
                environment = world.environment.name,
                difficulty = world.difficulty.name,
                time = world.time,
                fullTime = world.fullTime,
                playerCount = world.players.size,
                entityCount = world.entities.size,
                chunkCount = world.loadedChunks.size,
                spawnLocation = LocationInfo(
                    world = world.name,
                    x = world.spawnLocation.x,
                    y = world.spawnLocation.y,
                    z = world.spawnLocation.z,
                    yaw = 0f,
                    pitch = 0f
                )
            )
        }
    }

    // ===== Plugin Information =====

    fun getPlugins(): List<PluginInfo> {
        return Bukkit.getPluginManager().plugins.map { plugin ->
            PluginInfo(
                name = plugin.name,
                version = plugin.description.version,
                enabled = plugin.isEnabled,
                authors = plugin.description.authors,
                description = plugin.description.description ?: ""
            )
        }
    }
}

// ===== Data Classes =====

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
    val displayName: Any, // Can be Component or String
    val health: Double,
    val maxHealth: Double,
    val foodLevel: Int,
    val level: Int,
    val exp: Float,
    val gameMode: String,
    val location: LocationInfo,
    val ping: Int,
    val isOp: Boolean
)

data class LocationInfo(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
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

data class WorldInfo(
    val name: String,
    val environment: String,
    val difficulty: String,
    val time: Long,
    val fullTime: Long,
    val playerCount: Int,
    val entityCount: Int,
    val chunkCount: Int,
    val spawnLocation: LocationInfo
)

data class PluginInfo(
    val name: String,
    val version: String,
    val enabled: Boolean,
    val authors: List<String>,
    val description: String
)
