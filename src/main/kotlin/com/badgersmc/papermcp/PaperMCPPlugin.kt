package com.badgersmc.papermcp

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.badgersmc.papermcp.mcp.MCPServer
import mu.KotlinLogging
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

private val logger = KotlinLogging.logger {}

class PaperMCPPlugin : JavaPlugin() {

    private lateinit var minecraftBridge: MinecraftBridge
    private lateinit var mcpServer: MCPServer

    override fun onEnable() {
        logger.info { "Enabling PaperMCP v${description.version}..." }

        // Save default config
        saveDefaultConfig()

        // Initialize Minecraft bridge
        minecraftBridge = MinecraftBridge(this)
        logger.info { "Minecraft bridge initialized" }

        // Initialize and start MCP server
        try {
            mcpServer = MCPServer(this, minecraftBridge)
            mcpServer.start()
            logger.info { "MCP server started successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start MCP server" }
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info { "PaperMCP enabled successfully!" }
    }

    override fun onDisable() {
        logger.info { "Disabling PaperMCP..." }

        // Stop MCP server
        if (::mcpServer.isInitialized) {
            mcpServer.stop()
            logger.info { "MCP server stopped" }
        }

        logger.info { "PaperMCP disabled" }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (command.name.equals("papermcp", ignoreCase = true)) {
            if (!sender.hasPermission("papermcp.admin")) {
                sender.sendMessage("§cYou don't have permission to use this command.")
                return true
            }

            if (args.isEmpty()) {
                sender.sendMessage("§7=== PaperMCP v${description.version} ===")
                sender.sendMessage("§7/papermcp status - Show MCP server status")
                sender.sendMessage("§7/papermcp reload - Reload configuration")
                sender.sendMessage("§7/papermcp help - Show this help")
                return true
            }

            when (args[0].lowercase()) {
                "status" -> {
                    val status = if (::mcpServer.isInitialized && mcpServer.isRunning) {
                        "§aRunning"
                    } else {
                        "§cStopped"
                    }
                    sender.sendMessage("§7MCP Server Status: $status")
                    sender.sendMessage("§7Connected Clients: ${if (::mcpServer.isInitialized) mcpServer.connectedClients else 0}")
                }
                "reload" -> {
                    reloadConfig()
                    sender.sendMessage("§aConfiguration reloaded successfully!")
                }
                "help" -> {
                    sender.sendMessage("§7=== PaperMCP Help ===")
                    sender.sendMessage("§7PaperMCP exposes your Minecraft server to AI via the Model Context Protocol (MCP).")
                    sender.sendMessage("§7Configure MCP clients (like Claude Desktop) to connect via stdio transport.")
                    sender.sendMessage("§7For more info: https://github.com/BadgersMC/PaperMCP")
                }
                else -> {
                    sender.sendMessage("§cUnknown subcommand. Use /papermcp help")
                }
            }
            return true
        }
        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (command.name.equals("papermcp", ignoreCase = true) && args.size == 1) {
            return listOf("status", "reload", "help").filter { it.startsWith(args[0].lowercase()) }
        }
        return null
    }
}
