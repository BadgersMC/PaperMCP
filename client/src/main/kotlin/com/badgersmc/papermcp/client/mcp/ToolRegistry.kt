package com.badgersmc.papermcp.client.mcp

import com.badgersmc.papermcp.client.websocket.WebSocketClient
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture

class ToolRegistry {

    private val tools = mutableMapOf<String, MCPTool>()

    init {
        // Register all available tools
        register(ServerInfoTool())
        register(ListPlayersTool())
        register(ReadConsoleTool())
        register(ReadChatTool())
        register(ExecuteCommandTool())
        register(SendMessageTool())
        register(ReadFileTool())
        register(ReadFileRangeTool())
        register(ListDirectoryTool())
        register(FileStatTool())
        register(BatchReadFilesTool())
        register(SearchFilesTool())
        register(FileHashTool())
        register(DownloadFileTool())
        register(EditFileTool())
        register(CopyFileTool())
        register(MoveFileTool())
        register(UploadFileTool())
        register(DeleteFileTool())
    }

    private fun register(tool: MCPTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): MCPTool? = tools[name]

    fun getAllTools(): List<ToolDefinition> {
        return tools.values.map { it.definition }
    }
}

interface MCPTool {
    val name: String
    val definition: ToolDefinition
    fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject>
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

// Individual tool implementations
class ServerInfoTool : MCPTool {
    override val name = "minecraft_server_info"

    override val definition = ToolDefinition(
        name = name,
        description = "Get detailed information about the Minecraft server including version, TPS, player count, and performance metrics",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject())
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("server_info")
            .thenApply { it.get("result").asJsonObject }
    }
}

class ListPlayersTool : MCPTool {
    override val name = "minecraft_list_players"

    override val definition = ToolDefinition(
        name = name,
        description = "List all online players with detailed information",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("player", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Optional: specific player name or UUID")
                })
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("list_players", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class ReadConsoleTool : MCPTool {
    override val name = "minecraft_read_console"

    override val definition = ToolDefinition(
        name = name,
        description = "Read recent console logs from the Minecraft server",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("limit", JsonObject().apply {
                    addProperty("type", "integer")
                    addProperty("description", "Maximum number of log lines (default: 100, capped by server mcp.buffers.console_log_size)")
                    addProperty("default", 100)
                })
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("read_console", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class ReadChatTool : MCPTool {
    override val name = "minecraft_read_chat"

    override val definition = ToolDefinition(
        name = name,
        description = "Read recent chat messages from players on the server",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("limit", JsonObject().apply {
                    addProperty("type", "integer")
                    addProperty("description", "Maximum number of chat messages (default: 50, capped by server mcp.buffers.chat_log_size)")
                })
                add("player", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Optional: filter by specific player name")
                })
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("read_chat", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class ExecuteCommandTool : MCPTool {
    override val name = "minecraft_execute_command"

    override val definition = ToolDefinition(
        name = name,
        description = "Execute a Minecraft server command with security checks",
        inputSchema = JsonObject().apply {
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
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("execute_command", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class SendMessageTool : MCPTool {
    override val name = "minecraft_send_message"

    override val definition = ToolDefinition(
        name = name,
        description = "Send a message to players or broadcast to the server",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("message", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "The message to send")
                })
                add("target", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Target player name or 'broadcast' (default: broadcast)")
                })
                add("prefix", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Optional prefix for the message")
                })
            })
            add("required", com.google.gson.JsonArray().apply {
                add("message")
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("send_message", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class ReadFileTool : MCPTool {
    override val name = "minecraft_read_file"

    override val definition = ToolDefinition(
        name = name,
        description = "Read an allowed text file from the Minecraft server. Path policy, size limits, and secret-file deny rules are enforced server-side.",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("path", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Path to the file relative to server directory (e.g., 'server.properties', 'plugins/MyPlugin/config.yml', 'logs/latest.log')")
                })
                add("max_lines", JsonObject().apply {
                    addProperty("type", "integer")
                    addProperty("description", "Maximum number of lines to read (default: 1000, max: 10000)")
                    addProperty("default", 1000)
                })
            })
            add("required", com.google.gson.JsonArray().apply {
                add("path")
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("read_file", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

class EditFileTool : MCPTool {
    override val name = "minecraft_edit_file"

    override val definition = ToolDefinition(
        name = name,
        description = "Edit an allowed text file using write, append, or find/replace. Requires PaperMCP maintenance mode and server-side file-write permission; backups are enabled by policy.",
        inputSchema = JsonObject().apply {
            addProperty("type", "object")
            add("properties", JsonObject().apply {
                add("path", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Path to the file relative to server directory")
                })
                add("mode", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Edit mode: 'write' (replace entire file), 'append' (add to end), or 'replace' (find and replace pattern)")
                })
                add("content", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Content to write/append, or replacement text for replace mode")
                })
                add("pattern", JsonObject().apply {
                    addProperty("type", "string")
                    addProperty("description", "Pattern to find (required for replace mode)")
                })
                add("create_backup", JsonObject().apply {
                    addProperty("type", "boolean")
                    addProperty("description", "Create backup before editing (default: true)")
                    addProperty("default", true)
                })
            })
            add("required", com.google.gson.JsonArray().apply {
                add("path")
                add("mode")
                add("content")
            })
        }
    )

    override fun execute(client: WebSocketClient, arguments: JsonObject): CompletableFuture<JsonObject> {
        return client.sendRequest("edit_file", arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}
