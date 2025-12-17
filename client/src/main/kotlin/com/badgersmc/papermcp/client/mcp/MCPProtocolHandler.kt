package com.badgersmc.papermcp.client.mcp

import com.badgersmc.papermcp.client.websocket.WebSocketClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/**
 * Handles MCP protocol on stdio and bridges to WebSocket client
 */
class MCPProtocolHandler(
    private val wsClient: WebSocketClient,
    private val stdin: BufferedReader,
    private val stdout: BufferedWriter
) {
    private val gson = com.google.gson.GsonBuilder().serializeNulls().create()
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null

    private val toolRegistry = ToolRegistry()

    fun start() {
        if (running.getAndSet(true)) {
            logger.warn { "MCP protocol handler already running" }
            return
        }

        logger.info { "Starting MCP protocol handler on stdio..." }

        // Start reader thread
        readerThread = thread(name = "MCP-Stdin-Reader", isDaemon = true) {
            try {
                processStdin()
            } catch (e: Exception) {
                logger.error(e) { "Error in stdin reader" }
            } finally {
                running.set(false)
            }
        }

        logger.info { "MCP protocol handler started" }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }

        logger.info { "Stopping MCP protocol handler..." }
        readerThread?.interrupt()
        readerThread = null
    }

    private fun processStdin() {
        logger.info { "Listening for MCP messages on stdin..." }

        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val line = stdin.readLine() ?: break
                if (line.isBlank()) continue

                logger.debug { "Received from stdin: $line" }

                val message = JsonParser.parseString(line).asJsonObject
                handleMCPRequest(message)

            } catch (e: InterruptedException) {
                logger.info { "Stdin reader interrupted" }
                break
            } catch (e: Exception) {
                logger.error(e) { "Error processing stdin" }
            }
        }

        logger.info { "Stdin reader stopped" }
    }

    private fun handleMCPRequest(request: JsonObject) {
        val method = request.get("method")?.asString
        val id = request.get("id")

        if (method == null) {
            sendError(id, -32600, "Invalid request")
            return
        }

        when (method) {
            "initialize" -> handleInitialize(request)
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolCall(request)
            else -> sendError(id, -32601, "Method not found: $method")
        }
    }

    private fun handleInitialize(request: JsonObject) {
        val id = request.get("id")

        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", gson.toJsonTree(id))
            add("result", JsonObject().apply {
                addProperty("protocolVersion", "2024-11-05")
                add("serverInfo", JsonObject().apply {
                    addProperty("name", "PaperMCP-Client")
                    addProperty("version", "1.0.0")
                })
                add("capabilities", JsonObject().apply {
                    add("tools", JsonObject())
                })
            })
        }

        sendResponse(response)
    }

    private fun handleToolsList(request: JsonObject) {
        val id = request.get("id")

        val tools = toolRegistry.getAllTools()

        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", gson.toJsonTree(id))
            add("result", JsonObject().apply {
                add("tools", gson.toJsonTree(tools))
            })
        }

        sendResponse(response)
    }

    private fun handleToolCall(request: JsonObject) {
        val id = request.get("id")
        val params = request.get("params")?.asJsonObject

        if (params == null) {
            sendError(id, -32602, "Missing parameters")
            return
        }

        val toolName = params.get("name")?.asString
        val arguments = params.get("arguments")?.asJsonObject

        if (toolName == null) {
            sendError(id, -32602, "Missing tool name")
            return
        }

        val tool = toolRegistry.getTool(toolName)
        if (tool == null) {
            sendError(id, -32601, "Tool not found: $toolName")
            return
        }

        // Execute tool by calling remote server
        tool.execute(wsClient, arguments ?: JsonObject())
            .thenAccept { result ->
                val response = JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    add("id", gson.toJsonTree(id))
                    add("result", JsonObject().apply {
                        add("content", gson.toJsonTree(listOf(
                            mapOf(
                                "type" to "text",
                                "text" to gson.toJson(result)
                            )
                        )))
                    })
                }
                sendResponse(response)
            }
            .exceptionally { ex ->
                logger.error(ex) { "Tool execution failed: $toolName" }
                sendError(id, -32603, "Tool execution failed: ${ex.message}")
                null
            }
    }

    @Synchronized
    private fun sendResponse(response: JsonObject) {
        try {
            val json = gson.toJson(response)
            logger.debug { "Sending to stdout: $json" }
            stdout.write(json)
            stdout.newLine()
            stdout.flush()
        } catch (e: Exception) {
            logger.error(e) { "Error sending response" }
        }
    }

    private fun sendError(id: Any?, code: Int, message: String) {
        val error = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", gson.toJsonTree(id))
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
        }
        sendResponse(error)
    }
}
