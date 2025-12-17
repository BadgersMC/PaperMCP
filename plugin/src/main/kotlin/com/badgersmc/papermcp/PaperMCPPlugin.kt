package com.badgersmc.papermcp

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.badgersmc.papermcp.websocket.WebSocketServer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class PaperMCPPlugin : JavaPlugin() {

    private lateinit var minecraftBridge: MinecraftBridge
    private lateinit var webSocketServer: WebSocketServer

    override fun onEnable() {
        logger.info("Enabling PaperMCP v${description.version}...")

        // Save default config
        saveDefaultConfig()

        // Validate authentication token
        val authToken = config.getString("server.authentication_token")
        if (authToken.isNullOrBlank() || authToken == "CHANGE_ME_TO_A_SECURE_TOKEN") {
            logger.severe("SECURITY WARNING: Please set a secure authentication_token in config.yml!")
            logger.severe("Plugin will not start without a valid token.")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Initialize Minecraft bridge
        minecraftBridge = MinecraftBridge(this)
        logger.info("Minecraft bridge initialized")

        // Initialize and start WebSocket server
        try {
            webSocketServer = WebSocketServer(this, minecraftBridge)
            webSocketServer.start()
            logger.info("WebSocket server started successfully")
            logger.info("Listening on ${config.getString("server.host")}:${config.getInt("server.port")}")
        } catch (e: Exception) {
            logger.severe("Failed to start WebSocket server: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info("PaperMCP enabled successfully!")
    }

    override fun onDisable() {
        logger.info("Disabling PaperMCP...")

        // Stop WebSocket server
        if (::webSocketServer.isInitialized) {
            webSocketServer.stop()
            logger.info("WebSocket server stopped")
        }

        logger.info("PaperMCP disabled")
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
                sender.sendMessage("§7/papermcp status - Show WebSocket server status")
                sender.sendMessage("§7/papermcp clients - List connected clients")
                sender.sendMessage("§7/papermcp reload - Reload configuration")
                sender.sendMessage("§7/papermcp help - Show this help")
                return true
            }

            when (args[0].lowercase()) {
                "status" -> {
                    val status = if (::webSocketServer.isInitialized && webSocketServer.isRunning) {
                        "§aRunning"
                    } else {
                        "§cStopped"
                    }
                    sender.sendMessage("§7WebSocket Server Status: $status")
                    sender.sendMessage("§7Connected Clients: ${if (::webSocketServer.isInitialized) webSocketServer.connectedClients else 0}")
                    sender.sendMessage("§7Listening on: ${config.getString("server.host")}:${config.getInt("server.port")}")
                }
                "clients" -> {
                    if (::webSocketServer.isInitialized) {
                        val clients = webSocketServer.getClientList()
                        sender.sendMessage("§7=== Connected Clients (${clients.size}) ===")
                        clients.forEach { (id, info) ->
                            sender.sendMessage("§7- $id: ${info.remoteAddress} (connected ${info.connectedDuration})")
                        }
                    } else {
                        sender.sendMessage("§cWebSocket server not initialized")
                    }
                }
                "reload" -> {
                    reloadConfig()
                    sender.sendMessage("§aConfiguration reloaded!")
                    sender.sendMessage("§eNote: WebSocket server restart required for changes to take effect")
                }
                "help" -> {
                    sender.sendMessage("§7=== PaperMCP Help ===")
                    sender.sendMessage("§7PaperMCP exposes your Minecraft server to AI via WebSocket.")
                    sender.sendMessage("§7Run PaperMCP Client on your PC to connect Claude Desktop.")
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
            return listOf("status", "clients", "reload", "help").filter { it.startsWith(args[0].lowercase()) }
        }
        return null
    }
}
