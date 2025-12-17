package com.badgersmc.papermcp.mcp.tools

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.JsonObject
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Tool to execute server commands with security checks
 */
class CommandExecutionTool(
    private val plugin: PaperMCPPlugin,
    private val bridge: MinecraftBridge
) : MCPTool {

    override val name = "minecraft_execute_command"

    override val description = """
        Execute a Minecraft server command. Commands are subject to whitelist/blacklist security checks.
        Use this to perform administrative tasks like managing players, changing game rules, or controlling the server.
        Examples: 'list', 'time set day', 'weather clear', 'gamemode creative PlayerName'
    """.trimIndent()

    override val inputSchema: JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("command", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "The command to execute (without leading slash)")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("command")
        })
    }

    override fun execute(arguments: JsonObject): Any {
        val command = arguments.get("command")?.asString
        if (command.isNullOrBlank()) {
            return mapOf(
                "success" to false,
                "error" to "Command cannot be empty"
            )
        }

        // Remove leading slash if present
        val cleanCommand = command.trimStart('/')

        // Security check
        if (!isCommandAllowed(cleanCommand)) {
            logger.warn { "MCP attempted to execute blocked command: $cleanCommand" }
            return mapOf(
                "success" to false,
                "error" to "Command is blocked by security policy",
                "command" to cleanCommand,
                "hint" to "Check command_whitelist and command_blacklist in config.yml"
            )
        }

        // Audit log
        if (plugin.config.getBoolean("mcp.security.audit_logging", true)) {
            logger.info { "MCP executing command: $cleanCommand" }
        }

        // Execute command
        val result = bridge.executeCommand(cleanCommand)

        return mapOf(
            "success" to result.success,
            "command" to result.command,
            "message" to result.message,
            "error" to result.error
        )
    }

    private fun isCommandAllowed(command: String): Boolean {
        val config = plugin.config

        // Get the base command (first word)
        val baseCommand = command.split(" ").firstOrNull()?.lowercase() ?: return false

        // Check blacklist first (blacklist takes priority)
        val blacklist = config.getStringList("mcp.security.command_blacklist")
        for (blocked in blacklist) {
            if (blocked.startsWith("*") && blocked.endsWith("*")) {
                // Contains check: *op* matches deop, op, etc.
                val pattern = blocked.trim('*').lowercase()
                if (baseCommand.contains(pattern)) {
                    return false
                }
            } else if (blocked.endsWith("*")) {
                // Starts with: op* matches op, operator, etc.
                val pattern = blocked.trimEnd('*').lowercase()
                if (baseCommand.startsWith(pattern)) {
                    return false
                }
            } else if (blocked.startsWith("*")) {
                // Ends with: *op matches op, deop, etc.
                val pattern = blocked.trimStart('*').lowercase()
                if (baseCommand.endsWith(pattern)) {
                    return false
                }
            } else if (baseCommand == blocked.lowercase()) {
                // Exact match
                return false
            }
        }

        // Check whitelist (if not empty, only allow whitelisted commands)
        val whitelist = config.getStringList("mcp.security.command_whitelist")
        if (whitelist.isEmpty()) {
            // Empty whitelist = all non-blacklisted commands allowed
            return true
        }

        // Check if command is in whitelist
        for (allowed in whitelist) {
            if (allowed.endsWith("*")) {
                // Prefix match: time* matches time, timeset, etc.
                val pattern = allowed.trimEnd('*').lowercase()
                if (baseCommand.startsWith(pattern)) {
                    return true
                }
            } else if (baseCommand == allowed.lowercase()) {
                // Exact match
                return true
            }
        }

        return false
    }
}
