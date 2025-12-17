package com.badgersmc.papermcp.mcp

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.badgersmc.papermcp.mcp.tools.ChatHistoryTool
import com.badgersmc.papermcp.mcp.tools.ChatTool
import com.badgersmc.papermcp.mcp.tools.CommandExecutionTool
import com.badgersmc.papermcp.mcp.tools.ConsoleLogTool
import com.badgersmc.papermcp.mcp.tools.MCPTool
import com.badgersmc.papermcp.mcp.tools.PlayerListTool
import com.badgersmc.papermcp.mcp.tools.ServerInfoTool
import com.google.gson.Gson
import com.google.gson.JsonObject
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/**
 * MCP Server implementation
 * Handles stdio communication and routes requests to appropriate tools
 */
class MCPServer(
    private val plugin: PaperMCPPlugin,
    private val bridge: MinecraftBridge
) {

    private val protocol = MCPProtocol()
    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var clientCount = AtomicBoolean(false) // Simple connected/disconnected state

    private val tools = mutableMapOf<String, MCPTool>()

    // Stdio streams
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    private val writer = BufferedWriter(OutputStreamWriter(System.out))

    init {
        // Register available tools
        registerTool(ServerInfoTool(bridge))
        registerTool(PlayerListTool(bridge))
        registerTool(ConsoleLogTool(bridge))
        registerTool(ChatHistoryTool(bridge))
        registerTool(CommandExecutionTool(plugin, bridge))
        registerTool(ChatTool(plugin))
    }

    private fun registerTool(tool: MCPTool) {
        tools[tool.name] = tool
        logger.debug { "Registered MCP tool: ${tool.name}" }
    }

    fun start() {
        if (running.getAndSet(true)) {
            logger.warn { "MCP server is already running" }
            return
        }

        logger.info { "Starting MCP server on stdio..." }

        // Start reader thread
        readerThread = thread(name = "MCP-Reader", isDaemon = true) {
            try {
                processMessages()
            } catch (e: Exception) {
                logger.error(e) { "Error in MCP reader thread" }
            } finally {
                running.set(false)
            }
        }

        logger.info { "MCP server started successfully" }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }

        logger.info { "Stopping MCP server..." }
        readerThread?.interrupt()
        readerThread = null
    }

    val isRunning: Boolean
        get() = running.get()

    val connectedClients: Int
        get() = if (clientCount.get()) 1 else 0

    private fun processMessages() {
        logger.info { "MCP server listening for messages on stdin..." }

        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                logger.debug { "Received: $line" }

                // Parse message
                val message = protocol.parseMessage(line)
                if (message == null) {
                    sendResponse(
                        protocol.createErrorResponse(
                            null,
                            MCPProtocol.ErrorCodes.PARSE_ERROR,
                            "Failed to parse message"
                        )
                    )
                    continue
                }

                // Handle request
                when (message) {
                    is MCPProtocol.MCPMessage.Request -> handleRequest(message)
                    is MCPProtocol.MCPMessage.Notification -> handleNotification(message)
                    else -> {
                        logger.warn { "Received unexpected message type: $message" }
                    }
                }
            } catch (e: InterruptedException) {
                logger.info { "MCP server interrupted" }
                break
            } catch (e: Exception) {
                logger.error(e) { "Error processing message" }
            }
        }

        logger.info { "MCP server stopped listening" }
    }

    private fun handleRequest(request: MCPProtocol.MCPMessage.Request) {
        logger.debug { "Handling request: ${request.method}" }

        try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolCall(request)
                "resources/list" -> handleResourcesList(request)
                "resources/read" -> handleResourceRead(request)
                else -> {
                    sendResponse(
                        protocol.createErrorResponse(
                            request.id,
                            MCPProtocol.ErrorCodes.METHOD_NOT_FOUND,
                            "Method not found: ${request.method}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling request: ${request.method}" }
            sendResponse(
                protocol.createErrorResponse(
                    request.id,
                    MCPProtocol.ErrorCodes.INTERNAL_ERROR,
                    "Internal error: ${e.message}"
                )
            )
        }
    }

    private fun handleNotification(notification: MCPProtocol.MCPMessage.Notification) {
        logger.debug { "Handling notification: ${notification.method}" }

        when (notification.method) {
            "notifications/initialized" -> {
                clientCount.set(true)
                logger.info { "Client initialized and connected" }
            }
            "notifications/cancelled" -> {
                logger.info { "Client cancelled request" }
            }
            else -> {
                logger.debug { "Unhandled notification: ${notification.method}" }
            }
        }
    }

    private fun handleInitialize(request: MCPProtocol.MCPMessage.Request) {
        logger.info { "Client initializing..." }

        val capabilities = mapOf(
            "tools" to emptyMap<String, Any>(),
            "resources" to emptyMap<String, Any>()
        )

        val response = protocol.createInitializeResponse(request.id, capabilities)
        sendResponse(response)
    }

    private fun handleToolsList(request: MCPProtocol.MCPMessage.Request) {
        val toolDefinitions = tools.values.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }

        val response = protocol.createToolsListResponse(request.id, toolDefinitions)
        sendResponse(response)
    }

    private fun handleToolCall(request: MCPProtocol.MCPMessage.Request) {
        val params = request.params
        if (params == null) {
            sendResponse(
                protocol.createErrorResponse(
                    request.id,
                    MCPProtocol.ErrorCodes.INVALID_PARAMS,
                    "Missing parameters"
                )
            )
            return
        }

        val toolName = params.get("name")?.asString
        if (toolName == null) {
            sendResponse(
                protocol.createErrorResponse(
                    request.id,
                    MCPProtocol.ErrorCodes.INVALID_PARAMS,
                    "Missing tool name"
                )
            )
            return
        }

        val tool = tools[toolName]
        if (tool == null) {
            sendResponse(
                protocol.createErrorResponse(
                    request.id,
                    MCPProtocol.ErrorCodes.METHOD_NOT_FOUND,
                    "Tool not found: $toolName"
                )
            )
            return
        }

        // Execute tool
        try {
            val arguments = params.get("arguments")?.asJsonObject ?: JsonObject()
            val result = tool.execute(arguments)

            val response = protocol.createSuccessResponse(
                request.id,
                mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to gson.toJson(result)
                        )
                    )
                )
            )
            sendResponse(response)
        } catch (e: Exception) {
            logger.error(e) { "Error executing tool: $toolName" }
            sendResponse(
                protocol.createErrorResponse(
                    request.id,
                    MCPProtocol.ErrorCodes.INTERNAL_ERROR,
                    "Tool execution failed: ${e.message}"
                )
            )
        }
    }

    private fun handleResourcesList(request: MCPProtocol.MCPMessage.Request) {
        // TODO: Implement resources (config files, logs, etc.)
        val resources = emptyList<ResourceDefinition>()
        val response = protocol.createResourcesListResponse(request.id, resources)
        sendResponse(response)
    }

    private fun handleResourceRead(request: MCPProtocol.MCPMessage.Request) {
        // TODO: Implement resource reading
        sendResponse(
            protocol.createErrorResponse(
                request.id,
                MCPProtocol.ErrorCodes.METHOD_NOT_FOUND,
                "Resources not yet implemented"
            )
        )
    }

    @Synchronized
    private fun sendResponse(response: String) {
        try {
            logger.debug { "Sending: $response" }
            writer.write(response)
            writer.newLine()
            writer.flush()
        } catch (e: Exception) {
            logger.error(e) { "Error sending response" }
        }
    }
}
