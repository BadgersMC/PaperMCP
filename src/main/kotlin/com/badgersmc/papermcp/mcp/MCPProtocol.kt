package com.badgersmc.papermcp.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * MCP Protocol handler
 * Implements the Model Context Protocol specification
 * https://spec.modelcontextprotocol.io/
 */
class MCPProtocol {

    private val gson = Gson()

    // ===== Protocol Constants =====
    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        const val SERVER_NAME = "PaperMCP"
        const val SERVER_VERSION = "1.0.0"
    }

    // ===== Message Types =====
    sealed class MCPMessage {
        abstract val jsonrpc: String

        data class Request(
            override val jsonrpc: String = "2.0",
            val id: Any,
            val method: String,
            val params: JsonObject? = null
        ) : MCPMessage()

        data class Response(
            override val jsonrpc: String = "2.0",
            val id: Any,
            val result: Any? = null,
            val error: ErrorObject? = null
        ) : MCPMessage()

        data class Notification(
            override val jsonrpc: String = "2.0",
            val method: String,
            val params: JsonObject? = null
        ) : MCPMessage()
    }

    data class ErrorObject(
        val code: Int,
        val message: String,
        val data: Any? = null
    )

    // ===== Error Codes =====
    object ErrorCodes {
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
    }

    // ===== Message Parsing =====

    fun parseMessage(json: String): MCPMessage? {
        return try {
            val jsonObject = JsonParser.parseString(json).asJsonObject

            when {
                jsonObject.has("method") && jsonObject.has("id") -> {
                    // Request
                    MCPMessage.Request(
                        jsonrpc = jsonObject.get("jsonrpc").asString,
                        id = jsonObject.get("id"),
                        method = jsonObject.get("method").asString,
                        params = jsonObject.get("params")?.asJsonObject
                    )
                }
                jsonObject.has("method") && !jsonObject.has("id") -> {
                    // Notification
                    MCPMessage.Notification(
                        jsonrpc = jsonObject.get("jsonrpc").asString,
                        method = jsonObject.get("method").asString,
                        params = jsonObject.get("params")?.asJsonObject
                    )
                }
                jsonObject.has("result") || jsonObject.has("error") -> {
                    // Response
                    MCPMessage.Response(
                        jsonrpc = jsonObject.get("jsonrpc").asString,
                        id = jsonObject.get("id"),
                        result = jsonObject.get("result"),
                        error = jsonObject.get("error")?.let {
                            gson.fromJson(it, ErrorObject::class.java)
                        }
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse MCP message: $json" }
            null
        }
    }

    // ===== Message Creation =====

    fun createSuccessResponse(id: Any, result: Any): String {
        val response = MCPMessage.Response(
            id = id,
            result = result
        )
        return gson.toJson(response)
    }

    fun createErrorResponse(id: Any?, code: Int, message: String, data: Any? = null): String {
        val response = MCPMessage.Response(
            id = id ?: "null",
            error = ErrorObject(code, message, data)
        )
        return gson.toJson(response)
    }

    fun createNotification(method: String, params: Any? = null): String {
        val notification = MCPMessage.Notification(
            method = method,
            params = if (params != null) gson.toJsonTree(params).asJsonObject else null
        )
        return gson.toJson(notification)
    }

    // ===== Initialize Response =====

    fun createInitializeResponse(id: Any, capabilities: Map<String, Any>): String {
        val result = mapOf(
            "protocolVersion" to PROTOCOL_VERSION,
            "serverInfo" to mapOf(
                "name" to SERVER_NAME,
                "version" to SERVER_VERSION
            ),
            "capabilities" to capabilities
        )
        return createSuccessResponse(id, result)
    }

    // ===== Tools Response =====

    fun createToolsListResponse(id: Any, tools: List<ToolDefinition>): String {
        val result = mapOf("tools" to tools)
        return createSuccessResponse(id, result)
    }

    // ===== Resources Response =====

    fun createResourcesListResponse(id: Any, resources: List<ResourceDefinition>): String {
        val result = mapOf("resources" to resources)
        return createSuccessResponse(id, result)
    }
}

// ===== Tool Definition =====

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

// ===== Resource Definition =====

data class ResourceDefinition(
    val uri: String,
    val name: String,
    val description: String,
    val mimeType: String? = null
)
