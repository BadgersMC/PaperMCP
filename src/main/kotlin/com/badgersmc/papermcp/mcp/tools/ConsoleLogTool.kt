package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.JsonObject

/**
 * Tool to read console logs
 */
class ConsoleLogTool(private val bridge: MinecraftBridge) : MCPTool {

    override val name = "minecraft_read_console"

    override val description = "Read recent console logs from the Minecraft server"

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("limit", JsonObject().apply {
                addProperty("type", "integer")
                addProperty("description", "Maximum number of log lines to return (default: 100, max: 1000)")
                addProperty("default", 100)
            })
        })
        add("required", com.google.gson.JsonArray())
    }

    override fun execute(arguments: JsonObject): Any {
        val limit = arguments.get("limit")?.asInt?.coerceIn(1, 1000) ?: 100

        val logs = bridge.getConsoleLogs(limit)

        return mapOf(
            "logCount" to logs.size,
            "logs" to logs
        )
    }
}
