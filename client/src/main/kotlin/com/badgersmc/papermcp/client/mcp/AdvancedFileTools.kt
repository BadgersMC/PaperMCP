package com.badgersmc.papermcp.client.mcp

import com.badgersmc.papermcp.client.websocket.WebSocketClient
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture

abstract class RemoteMethodTool(
    override val name: String,
    private val method: String,
    description: String,
    schema: JsonObject
) : MCPTool {
    override val definition = ToolDefinition(name, description, schema)

    override fun execute(
        client: WebSocketClient,
        arguments: JsonObject
    ): CompletableFuture<JsonObject> {
        return client.sendRequest(method, arguments)
            .thenApply { it.get("result").asJsonObject }
    }
}

private fun stringProperty(description: String, default: String? = null) =
    JsonObject().apply {
        addProperty("type", "string")
        addProperty("description", description)
        if (default != null) addProperty("default", default)
    }

private fun integerProperty(description: String, default: Int? = null) =
    JsonObject().apply {
        addProperty("type", "integer")
        addProperty("description", description)
        if (default != null) addProperty("default", default)
    }

private fun booleanProperty(description: String, default: Boolean? = null) =
    JsonObject().apply {
        addProperty("type", "boolean")
        addProperty("description", description)
        if (default != null) addProperty("default", default)
    }

private fun stringArrayProperty(description: String) =
    JsonObject().apply {
        addProperty("type", "array")
        addProperty("description", description)
        add("items", JsonObject().apply { addProperty("type", "string") })
    }

private fun schema(
    properties: Map<String, JsonObject>,
    required: List<String> = emptyList()
): JsonObject = JsonObject().apply {
    addProperty("type", "object")
    add("properties", JsonObject().apply {
        properties.forEach { (name, definition) -> add(name, definition) }
    })
    if (required.isNotEmpty()) {
        add("required", JsonArray().apply { required.forEach { add(it) } })
    }
}

class ReadFileRangeTool : RemoteMethodTool(
    name = "minecraft_read_file_range",
    method = "read_file_range",
    description = "Read a line range from an allowed server file without loading the entire file.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Path relative to the Minecraft server root"),
            "start_line" to integerProperty("Zero-based starting line", 0),
            "max_lines" to integerProperty("Maximum lines to return", 1000)
        ),
        required = listOf("path")
    )
)

class ListDirectoryTool : RemoteMethodTool(
    name = "minecraft_list_directory",
    method = "list_directory",
    description = "List files and directories inside the configured PaperMCP file allowlist.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Directory relative to server root", "."),
            "depth" to integerProperty("Traversal depth, 1-8", 1),
            "max_entries" to integerProperty("Maximum entries to return", 1000)
        )
    )
)

class FileStatTool : RemoteMethodTool(
    name = "minecraft_file_stat",
    method = "file_stat",
    description = "Get type, size, timestamps, and access metadata for an allowed server path.",
    schema = schema(
        mapOf("path" to stringProperty("Path relative to server root")),
        required = listOf("path")
    )
)

class BatchReadFilesTool : RemoteMethodTool(
    name = "minecraft_batch_read_files",
    method = "batch_read_files",
    description = "Read many allowed text files in one request. Server-side limits cap file count and lines.",
    schema = schema(
        mapOf(
            "paths" to stringArrayProperty("Paths relative to server root"),
            "start_line" to integerProperty("Zero-based starting line for every file", 0),
            "max_lines_per_file" to integerProperty("Maximum lines returned per file", 1000)
        ),
        required = listOf("paths")
    )
)

class SearchFilesTool : RemoteMethodTool(
    name = "minecraft_search_files",
    method = "search_files",
    description = "Search text across allowed server files with glob, regex, and result limits.",
    schema = schema(
        mapOf(
            "root" to stringProperty("Allowed directory to search", "."),
            "query" to stringProperty("Text or regular expression to search for"),
            "file_glob" to stringProperty("Glob applied to server-relative paths", "**"),
            "regex" to booleanProperty("Interpret query as a regular expression", false),
            "case_sensitive" to booleanProperty("Use case-sensitive matching", false),
            "max_results" to integerProperty("Maximum matches returned", 100)
        ),
        required = listOf("query")
    )
)

class FileHashTool : RemoteMethodTool(
    name = "minecraft_file_hash",
    method = "file_hash",
    description = "Hash an allowed server file using SHA-256 or SHA-512.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Path relative to server root"),
            "algorithm" to stringProperty("SHA-256 or SHA-512", "SHA-256")
        ),
        required = listOf("path")
    )
)

class DownloadFileTool : RemoteMethodTool(
    name = "minecraft_download_file",
    method = "download_file",
    description = "Download an allowed binary file in base64 chunks. Useful for databases, JARs, ZIPs, and resource packs that cannot be read as text.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Path relative to server root"),
            "offset" to integerProperty("Byte offset to begin reading", 0),
            "max_bytes" to integerProperty("Maximum bytes to return in this chunk", 1024 * 1024)
        ),
        required = listOf("path")
    )
)

class CopyFileTool : RemoteMethodTool(
    name = "minecraft_copy_file",
    method = "copy_file",
    description = "Copy an allowed server file. Requires PaperMCP maintenance mode and file writes.",
    schema = schema(
        mapOf(
            "source" to stringProperty("Source path relative to server root"),
            "destination" to stringProperty("Destination path relative to server root"),
            "overwrite" to booleanProperty("Overwrite destination if present", false)
        ),
        required = listOf("source", "destination")
    )
)

class MoveFileTool : RemoteMethodTool(
    name = "minecraft_move_file",
    method = "move_file",
    description = "Move or rename an allowed server file. Requires maintenance mode and file writes.",
    schema = schema(
        mapOf(
            "source" to stringProperty("Source path relative to server root"),
            "destination" to stringProperty("Destination path relative to server root"),
            "overwrite" to booleanProperty("Overwrite destination if present", false)
        ),
        required = listOf("source", "destination")
    )
)

class UploadFileTool : RemoteMethodTool(
    name = "minecraft_upload_file",
    method = "upload_file",
    description = "Upload a base64 binary chunk to an allowed server path. Supports chunked JAR/ZIP/mcpack transfer. For deployments, prefer uploading to a temporary sibling path, verify with minecraft_file_hash, then move into place. Requires maintenance mode, writes, and uploads.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Destination path relative to server root"),
            "data_base64" to stringProperty("Base64-encoded binary chunk"),
            "offset" to integerProperty("Byte offset for this chunk", 0),
            "truncate" to booleanProperty(
                "Whether to truncate before writing. If omitted, the server truncates only when offset is 0."
            ),
            "create_backup" to booleanProperty("Back up an existing destination on the first chunk", true)
        ),
        required = listOf("path", "data_base64")
    )
)

class DeleteFileTool : RemoteMethodTool(
    name = "minecraft_delete_file",
    method = "delete_file",
    description = "Delete an allowed server path. Disabled by default, requires maintenance mode and file-write permission, and directory deletion requires a separate server-side opt-in.",
    schema = schema(
        mapOf(
            "path" to stringProperty("Path relative to server root"),
            "recursive" to booleanProperty("Allow recursive directory deletion", false)
        ),
        required = listOf("path")
    )
)
