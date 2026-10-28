package com.badgersmc.papermcp

import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.badgersmc.papermcp.websocket.WebSocketServer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class PaperMCPPlugin : JavaPlugin() {

    private lateinit var minecraftBridge: MinecraftBridge
    private lateinit var webSocketServer: WebSocketServer

    @Volatile
    private var maintenanceMode: Boolean = false

    fun isMaintenanceMode(): Boolean = maintenanceMode

    private fun setMaintenanceMode(enabled: Boolean) {
        maintenanceMode = enabled
        logger.warning("PaperMCP maintenance mode ${if (enabled) "ENABLED" else "disabled"}")
    }

    override fun onEnable() {
        logger.info("Enabling PaperMCP v${description.version}...")

        // Save default config
        saveDefaultConfig()

        maintenanceMode = config.getBoolean("mcp.security.maintenance_mode", false)
        if (maintenanceMode) {
            logger.warning("PaperMCP started with maintenance mode ENABLED")
        }

        // Validate authentication token
        val authToken = config.getString("server.authentication_token")
        if (
            authToken.isNullOrBlank() ||
            authToken == "CHANGE_ME_TO_A_SECURE_TOKEN" ||
            authToken.length < 32
        ) {
            logger.severe("SECURITY WARNING: Set authentication_token to a random secret of at least 32 characters.")
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
                sender.sendMessage("§7/papermcp maintenance <on|off|status> - Toggle maintenance mode")
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
                    sender.sendMessage("§7Maintenance Mode: ${if (maintenanceMode) "§eENABLED" else "§aDisabled"}")
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
                    maintenanceMode = config.getBoolean("mcp.security.maintenance_mode", false)
                    sender.sendMessage("§aConfiguration reloaded!")
                    sender.sendMessage("§7Maintenance Mode: ${if (maintenanceMode) "§eENABLED" else "§aDisabled"}")
                    sender.sendMessage("§eNote: WebSocket listener settings require a plugin/server restart")
                }
                "maintenance" -> {
                    when (args.getOrNull(1)?.lowercase()) {
                        "on", "enable", "enabled" -> {
                            setMaintenanceMode(true)
                            sender.sendMessage("§ePaperMCP maintenance mode enabled.")
                        }
                        "off", "disable", "disabled" -> {
                            setMaintenanceMode(false)
                            sender.sendMessage("§aPaperMCP maintenance mode disabled.")
                        }
                        "status", null -> {
                            sender.sendMessage("§7Maintenance Mode: ${if (maintenanceMode) "§eENABLED" else "§aDisabled"}")
                        }
                        else -> sender.sendMessage("§cUsage: /papermcp maintenance <on|off|status>")
                    }
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
        if (!command.name.equals("papermcp", ignoreCase = true)) return null

        if (args.size == 1) {
            return listOf("status", "clients", "reload", "maintenance", "help")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("maintenance", ignoreCase = true)) {
            return listOf("on", "off", "status")
                .filter { it.startsWith(args[1].lowercase()) }
        }
        return null
    }
}
