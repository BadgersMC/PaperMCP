package com.badgersmc.papermcp.client

import com.badgersmc.papermcp.client.mcp.MCPProtocolHandler
import com.badgersmc.papermcp.client.websocket.WebSocketClient
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    logger.info { "PaperMCP Client starting..." }

    // Load configuration
    val configPath = if (args.isNotEmpty()) {
        Paths.get(args[0])
    } else {
        Paths.get("papermcp-client-config.json")
    }

    if (!Files.exists(configPath)) {
        logger.error { "Configuration file not found: $configPath" }
        logger.info { "Creating default configuration..." }
        createDefaultConfig(configPath)
        logger.info { "Please edit $configPath with your server details and restart" }
        exitProcess(1)
    }

    val config = ClientConfig.load(configPath)
    logger.info { "Loaded configuration from $configPath" }
    logger.info { "Connecting to ${config.host}:${config.port}..." }

    // Create stdin/stdout handlers for MCP protocol
    val stdin = BufferedReader(InputStreamReader(System.`in`))
    val stdout = BufferedWriter(OutputStreamWriter(System.out))

    // Create WebSocket client
    val wsClient = WebSocketClient(config)

    // Create MCP protocol handler
    val mcpHandler = MCPProtocolHandler(wsClient, stdin, stdout)

    // Connect to remote server
    try {
        wsClient.connect()
        logger.info { "Connected to Minecraft server" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to connect to server" }
        exitProcess(1)
    }

    // Start MCP protocol handler
    mcpHandler.start()

    // Wait for shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down..." }
        mcpHandler.stop()
        wsClient.disconnect()
    })

    // Keep main thread alive
    Thread.currentThread().join()
}

private fun createDefaultConfig(path: Path) {
    val defaultConfig = """
        {
          "host": "your-minecraft-server.com",
          "port": 25577,
          "token": "your-authentication-token-here",
          "use_ssl": false,
          "reconnect": {
            "enabled": true,
            "max_attempts": 10,
            "delay_ms": 5000
          }
        }
    """.trimIndent()

    Files.writeString(path, defaultConfig)
}
