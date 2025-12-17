package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.JsonObject

/**
 * Tool to get Minecraft server information
 */
class ServerInfoTool(private val bridge: MinecraftBridge) : MCPTool {

    override val name = "minecraft_server_info"

    override val description = "Get detailed information about the Minecraft server including version, TPS, player count, and performance metrics"

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject())
        add("required", com.google.gson.JsonArray())
    }

    override fun execute(arguments: JsonObject): Any {
        val serverInfo = bridge.getServerInfo()

        return mapOf(
            "version" to serverInfo.version,
            "bukkitVersion" to serverInfo.bukkitVersion,
            "onlineMode" to serverInfo.onlineMode,
            "maxPlayers" to serverInfo.maxPlayers,
            "onlinePlayers" to serverInfo.onlinePlayers,
            "viewDistance" to serverInfo.viewDistance,
            "simulationDistance" to serverInfo.simulationDistance,
            "tps" to mapOf(
                "1min" to String.format("%.2f", serverInfo.tps[0]),
                "5min" to String.format("%.2f", serverInfo.tps[1]),
                "15min" to String.format("%.2f", serverInfo.tps[2])
            ),
            "uptime" to formatUptime(serverInfo.uptime)
        )
    }

    private fun formatUptime(uptimeMs: Long): String {
        val seconds = uptimeMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
