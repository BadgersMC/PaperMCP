package com.badgersmc.papermcp.mcp.tools

import com.google.gson.JsonObject

/**
 * Base interface for MCP tools
 */
interface MCPTool {
    /**
     * Tool name (must be unique)
     */
    val name: String

    /**
     * Tool description
     */
    val description: String

    /**
     * JSON schema for input parameters
     */
    val inputSchema: JsonObject

    /**
     * Execute the tool with given arguments
     * @param arguments JSON object containing tool arguments
     * @return Tool execution result
     */
    fun execute(arguments: JsonObject): Any
}
