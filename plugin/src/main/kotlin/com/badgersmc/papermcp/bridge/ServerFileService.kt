package com.badgersmc.papermcp.bridge

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.security.FileAccessPolicy
import com.badgersmc.papermcp.security.GlobMatcher
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.MalformedInputException
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.logging.Logger
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

private val fileLogger = Logger.getLogger("PaperMCP-Files")

class ServerFileService(private val plugin: PaperMCPPlugin) {
    private val policy = FileAccessPolicy(plugin)
    private val serverRoot = File(".").canonicalFile.toPath()

    fun readFile(path: String, maxLines: Int = 1000): FileReadResult =
        readFileRange(path, 0, maxLines)

    fun readFileRange(path: String, startLine: Int, maxLines: Int): FileReadResult {
        if (!readEnabled()) return readDenied(path, "File reads are disabled")

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) {
            return readDenied(path, decision.reason)
        }

        val file = decision.absolutePath.toFile()
        if (!file.exists()) return readDenied(path, "File not found")
        if (!file.isFile) return readDenied(path, "Path is not a file")

        val maxBytes = maxReadBytes()
        if (file.length() > maxBytes) {
            return readDenied(path, "File too large (${file.length()} bytes > $maxBytes)")
        }

        return try {
            val safeStart = startLine.coerceAtLeast(0)
            val safeLimit = maxLines.coerceIn(1, 10_000)
            val selected = ArrayList<String>(safeLimit)
            var total = 0

            file.bufferedReader().useLines { sequence ->
                sequence.forEach { line ->
                    if (total >= safeStart && selected.size < safeLimit) selected += line
                    total++
                }
            }

            audit("read", decision.relativePath ?: path)
            FileReadResult(
                success = true,
                path = decision.relativePath ?: path,
                message = "File read successfully (${selected.size} lines from $safeStart)",
                lines = selected,
                totalLines = total,
                truncated = safeStart + selected.size < total
            )
        } catch (e: Exception) {
            fileLogger.warning("Read failed for ${decision.relativePath}: ${e.message}")
            readDenied(path, "Error: ${e.message}")
        }
    }

    fun listDirectory(path: String, depth: Int, maxEntries: Int): Map<String, Any?> {
        if (!readEnabled()) return failure(path, "File reads are disabled")
        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        val root = decision.absolutePath
        if (!root.isDirectory()) return failure(path, "Path is not a directory")

        val safeDepth = depth.coerceIn(1, 8)
        val safeMax = maxEntries.coerceIn(1, maxListEntries())
        val entries = mutableListOf<Map<String, Any?>>()
        var truncated = false

        Files.walkFileTree(
            root,
            emptySet(),
            safeDepth,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (dir == root) return FileVisitResult.CONTINUE

                    val relative = relativeToServer(dir)
                    if (!policy.canTraverse(relative)) return FileVisitResult.SKIP_SUBTREE
                    val childDecision = policy.resolve(relative)
                    if (!childDecision.allowed) return FileVisitResult.CONTINUE

                    entries += entryFor(dir, relative)
                    if (entries.size >= safeMax) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    val relative = relativeToServer(file)
                    val childDecision = policy.resolve(relative)
                    if (!childDecision.allowed) return FileVisitResult.CONTINUE

                    entries += entryFor(file, relative)
                    if (entries.size >= safeMax) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )

        audit("list", decision.relativePath ?: path)
        return mapOf(
            "success" to true,
            "path" to (decision.relativePath ?: path),
            "entries" to entries,
            "entryCount" to entries.size,
            "truncated" to truncated
        )
    }

    fun stat(path: String): Map<String, Any?> {
        if (!readEnabled()) return failure(path, "File reads are disabled")
        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        val file = decision.absolutePath.toFile()
        if (!file.exists()) return failure(path, "File not found")

        audit("stat", decision.relativePath ?: path)
        return mapOf(
            "success" to true,
            "path" to (decision.relativePath ?: path),
            "type" to if (file.isDirectory) "directory" else "file",
            "size" to if (file.isFile) file.length() else null,
            "lastModified" to file.lastModified(),
            "readable" to file.canRead(),
            "writable" to file.canWrite()
        )
    }

    fun batchRead(paths: List<String>, startLine: Int, maxLinesPerFile: Int): Map<String, Any?> {
        if (!readEnabled()) return failure("", "File reads are disabled")
        val safePaths = paths.take(maxBatchFiles())
        val results = mutableListOf<FileReadResult>()
        var remainingLines = maxBatchTotalLines()

        for (path in safePaths) {
            if (remainingLines <= 0) break
            val perFileLimit = minOf(maxLinesPerFile.coerceIn(1, 10_000), remainingLines)
            val result = readFileRange(path, startLine, perFileLimit)
            results += result
            remainingLines -= result.lines.size
        }

        return mapOf(
            "success" to results.all { it.success },
            "requested" to paths.size,
            "processed" to results.size,
            "truncated" to (
                paths.size > safePaths.size ||
                    results.size < safePaths.size ||
                    results.any { it.truncated }
                ),
            "files" to results
        )
    }

    fun hash(path: String, algorithm: String): Map<String, Any?> {
        if (!readEnabled()) return failure(path, "File reads are disabled")
        val normalizedAlgorithm = algorithm.uppercase()
        if (normalizedAlgorithm !in setOf("SHA-256", "SHA-512")) {
            return failure(path, "Unsupported hash algorithm: $algorithm")
        }

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        if (!decision.absolutePath.isRegularFile()) return failure(path, "Path is not a file")
        if (decision.absolutePath.toFile().length() > maxReadBytes()) {
            return failure(path, "File exceeds configured read limit")
        }

        val digest = MessageDigest.getInstance(normalizedAlgorithm)
        Files.newInputStream(decision.absolutePath).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }

        val value = digest.digest().joinToString("") { "%02x".format(it) }
        audit("hash", decision.relativePath ?: path)
        return mapOf(
            "success" to true,
            "path" to (decision.relativePath ?: path),
            "algorithm" to normalizedAlgorithm,
            "hash" to value,
            "size" to decision.absolutePath.toFile().length()
        )
    }

    fun search(
        rootPath: String,
        query: String,
        fileGlob: String,
        regex: Boolean,
        caseSensitive: Boolean,
        maxResults: Int
    ): Map<String, Any?> {
        if (!readEnabled()) return failure(rootPath, "File reads are disabled")
        if (query.isBlank()) return failure(rootPath, "Search query is required")

        val decision = policy.resolve(rootPath)
        if (!decision.allowed || decision.absolutePath == null) return failure(rootPath, decision.reason)
        if (!decision.absolutePath.isDirectory()) return failure(rootPath, "Search root is not a directory")

        val safeMax = maxResults.coerceIn(1, maxSearchResults())
        val maxScanned = maxSearchFiles()
        val result = mutableListOf<Map<String, Any?>>()
        var scanned = 0
        val matcher = if (regex) {
            runCatching {
                Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }.getOrElse { return failure(rootPath, "Invalid regex: ${it.message}") }
        } else null

        var truncated = false
        Files.walkFileTree(
            decision.absolutePath,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (dir == decision.absolutePath) return FileVisitResult.CONTINUE
                    val relative = relativeToServer(dir)
                    return if (policy.canTraverse(relative)) {
                        FileVisitResult.CONTINUE
                    } else {
                        FileVisitResult.SKIP_SUBTREE
                    }
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    if (result.size >= safeMax || scanned >= maxScanned) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }

                    val relative = relativeToServer(file)
                    val candidateDecision = policy.resolve(relative)
                    if (!candidateDecision.allowed) return FileVisitResult.CONTINUE

                    scanned++
                    if (!GlobMatcher.matches(fileGlob.ifBlank { "**" }, relative)) {
                        return FileVisitResult.CONTINUE
                    }
                    if (file.toFile().length() > maxSearchFileBytes()) {
                        return FileVisitResult.CONTINUE
                    }

                    searchFile(
                        file.toFile(),
                        relative,
                        query,
                        matcher,
                        caseSensitive,
                        safeMax,
                        result
                    )
                    if (result.size >= safeMax) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    return FileVisitResult.CONTINUE
                }
            }
        )

        audit("search", decision.relativePath ?: rootPath)
        return mapOf(
            "success" to true,
            "root" to (decision.relativePath ?: rootPath),
            "query" to query,
            "results" to result,
            "resultCount" to result.size,
            "filesScanned" to scanned,
            "truncated" to truncated
        )
    }

    fun editFile(
        path: String,
        mode: String,
        content: String,
        pattern: String?,
        createBackup: Boolean
    ): FileEditResult {
        val denied = mutationDenied(path, requiresUpload = false)
        if (denied != null) return denied

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) {
            return FileEditResult(false, path, decision.reason)
        }
        if (decision.relativePath == ".") {
            return FileEditResult(false, path, "The server root cannot be modified")
        }

        val file = decision.absolutePath.toFile()
        val maxBytes = maxWriteBytes()
        val backupPath = if (shouldBackup(createBackup) && file.isFile) {
            backup(file, decision.relativePath!!)
        } else null

        return try {
            when (mode.lowercase()) {
                "write" -> {
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    if (bytes.size > maxBytes) return FileEditResult(false, path, "Content exceeds write limit")
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    audit("write", decision.relativePath!!)
                    FileEditResult(true, decision.relativePath, "File written successfully", backupPath)
                }
                "append" -> {
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    if (file.length() + bytes.size > maxBytes) {
                        return FileEditResult(false, path, "Result would exceed write limit")
                    }
                    file.parentFile?.mkdirs()
                    file.appendBytes(bytes)
                    audit("append", decision.relativePath!!)
                    FileEditResult(true, decision.relativePath, "Content appended successfully", backupPath)
                }
                "replace" -> replaceText(file, decision.relativePath!!, pattern, content, backupPath)
                else -> FileEditResult(false, path, "Invalid mode: $mode")
            }
        } catch (e: Exception) {
            fileLogger.warning("Edit failed for ${decision.relativePath}: ${e.message}")
            FileEditResult(false, path, "Error: ${e.message}")
        }
    }

    fun downloadChunk(path: String, offset: Long, maxBytes: Int): Map<String, Any?> {
        if (!readEnabled()) return failure(path, "File reads are disabled")
        if (offset < 0L) return failure(path, "Offset must be >= 0")

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        if (!decision.absolutePath.isRegularFile()) return failure(path, "Path is not a file")

        val file = decision.absolutePath.toFile()
        if (file.length() > maxReadBytes()) {
            return failure(path, "File exceeds configured read limit")
        }

        val safeMax = maxBytes.coerceIn(1, maxDownloadChunkBytes())
        if (offset > file.length()) return failure(path, "Offset is past end of file")

        val buffer = ByteArray(minOf(safeMax.toLong(), file.length() - offset).toInt())
        val bytesRead = if (buffer.isEmpty()) {
            0
        } else {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                raf.read(buffer)
            }.coerceAtLeast(0)
        }
        val payload = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
        val nextOffset = offset + bytesRead

        audit("download", "${decision.relativePath}@$offset+$bytesRead")
        return mapOf(
            "success" to true,
            "path" to decision.relativePath,
            "offset" to offset,
            "bytesRead" to bytesRead,
            "size" to file.length(),
            "nextOffset" to nextOffset,
            "eof" to (nextOffset >= file.length()),
            "dataBase64" to Base64.getEncoder().encodeToString(payload)
        )
    }

    fun copy(source: String, destination: String, overwrite: Boolean): Map<String, Any?> =
        copyOrMove(source, destination, overwrite, move = false)

    fun move(source: String, destination: String, overwrite: Boolean): Map<String, Any?> =
        copyOrMove(source, destination, overwrite, move = true)

    fun uploadChunk(
        path: String,
        dataBase64: String,
        offset: Long,
        truncate: Boolean,
        createBackup: Boolean
    ): Map<String, Any?> {
        val denied = mutationDenied(path, requiresUpload = true)
        if (denied != null) return resultFromEdit(denied)

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        if (decision.relativePath == ".") return failure(path, "The server root cannot be modified")
        val file = decision.absolutePath.toFile()

        val bytes = runCatching { Base64.getDecoder().decode(dataBase64) }.getOrElse {
            return failure(path, "Invalid base64 payload")
        }
        if (bytes.size > maxUploadChunkBytes()) {
            return failure(path, "Upload chunk exceeds configured chunk limit")
        }
        val safeOffset = offset.coerceAtLeast(0)
        if (truncate && safeOffset != 0L) {
            return failure(path, "truncate=true is only valid for the first chunk at offset 0")
        }
        val projectedSize = if (truncate) {
            bytes.size.toLong()
        } else {
            maxOf(file.length(), safeOffset + bytes.size)
        }
        if (projectedSize > maxWriteBytes()) return failure(path, "Upload would exceed write limit")

        val backupPath = if (safeOffset == 0L && shouldBackup(createBackup) && file.isFile) {
            backup(file, decision.relativePath!!)
        } else null

        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            if (truncate && safeOffset == 0L) raf.setLength(0)
            raf.seek(safeOffset)
            raf.write(bytes)
        }

        audit("upload", "${decision.relativePath}@$safeOffset+${bytes.size}")
        return mapOf(
            "success" to true,
            "path" to decision.relativePath,
            "bytesWritten" to bytes.size,
            "offset" to safeOffset,
            "size" to file.length(),
            "backupPath" to backupPath
        )
    }

    fun delete(path: String, recursive: Boolean): Map<String, Any?> {
        val denied = mutationDenied(path, requiresUpload = false)
        if (denied != null) return resultFromEdit(denied)
        if (!plugin.config.getBoolean("mcp.security.files.delete", false)) {
            return failure(path, "File deletion is disabled")
        }

        val decision = policy.resolve(path)
        if (!decision.allowed || decision.absolutePath == null) return failure(path, decision.reason)
        if (decision.relativePath == ".") return failure(path, "The server root cannot be deleted")
        val file = decision.absolutePath.toFile()
        if (!file.exists()) return failure(path, "Path does not exist")
        if (file.isDirectory) {
            if (!recursive) return failure(path, "Directory deletion requires recursive=true")
            if (!plugin.config.getBoolean("mcp.security.files.delete_directories", false)) {
                return failure(path, "Directory deletion is disabled")
            }
        }

        val backupPath = if (file.isFile && backupsEnabled()) backup(file, decision.relativePath!!) else null
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted) return failure(path, "Delete failed")

        audit("delete", decision.relativePath!!)
        return mapOf(
            "success" to true,
            "path" to decision.relativePath,
            "backupPath" to backupPath
        )
    }

    private fun copyOrMove(
        source: String,
        destination: String,
        overwrite: Boolean,
        move: Boolean
    ): Map<String, Any?> {
        val denied = mutationDenied(destination, requiresUpload = false)
        if (denied != null) return resultFromEdit(denied)

        val sourceDecision = policy.resolve(source)
        val destinationDecision = policy.resolve(destination)
        if (!sourceDecision.allowed || sourceDecision.absolutePath == null) return failure(source, sourceDecision.reason)
        if (!destinationDecision.allowed || destinationDecision.absolutePath == null) return failure(destination, destinationDecision.reason)
        if (destinationDecision.relativePath == ".") return failure(destination, "The server root cannot be overwritten")
        if (!Files.isRegularFile(sourceDecision.absolutePath)) return failure(source, "Source is not a file")

        val destinationFile = destinationDecision.absolutePath.toFile()
        if (destinationFile.exists() && !overwrite) return failure(destination, "Destination already exists")
        if (sourceDecision.absolutePath.toFile().length() > maxWriteBytes()) {
            return failure(source, "Source exceeds write limit")
        }

        val backupPath = if (destinationFile.isFile && backupsEnabled()) {
            backup(destinationFile, destinationDecision.relativePath!!)
        } else null
        destinationFile.parentFile?.mkdirs()

        val options: Array<CopyOption> = if (overwrite) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }
        if (move) {
            Files.move(sourceDecision.absolutePath, destinationDecision.absolutePath, *options)
        } else {
            Files.copy(sourceDecision.absolutePath, destinationDecision.absolutePath, *options)
        }

        audit(
            if (move) "move" else "copy",
            "${sourceDecision.relativePath} -> ${destinationDecision.relativePath}"
        )
        return mapOf(
            "success" to true,
            "source" to sourceDecision.relativePath,
            "destination" to destinationDecision.relativePath,
            "size" to destinationFile.length(),
            "backupPath" to backupPath
        )
    }

    private fun replaceText(
        file: File,
        relativePath: String,
        pattern: String?,
        replacement: String,
        backupPath: String?
    ): FileEditResult {
        if (pattern.isNullOrEmpty()) return FileEditResult(false, relativePath, "Pattern is required")
        if (!file.isFile) return FileEditResult(false, relativePath, "File not found")

        val original = file.readText()
        val count = countOccurrences(original, pattern)
        if (count == 0) return FileEditResult(false, relativePath, "Pattern not found", backupPath, 0)

        val updated = original.replace(pattern, replacement)
        if (updated.toByteArray(Charsets.UTF_8).size > maxWriteBytes()) {
            return FileEditResult(false, relativePath, "Result would exceed write limit", backupPath, 0)
        }

        file.writeText(updated)
        audit("replace", "$relativePath ($count replacements)")
        return FileEditResult(
            true,
            relativePath,
            "Replaced $count occurrence(s)",
            backupPath,
            count
        )
    }

    private fun relativeToServer(path: Path): String =
        serverRoot
            .relativize(path.toFile().canonicalFile.toPath())
            .toString()
            .replace('\\', '/')
            .ifBlank { "." }

    private fun entryFor(path: Path, relative: String): Map<String, Any?> {
        val file = path.toFile()
        return mapOf(
            "path" to relative,
            "name" to file.name,
            "type" to if (file.isDirectory) "directory" else "file",
            "size" to if (file.isFile) file.length() else null,
            "lastModified" to file.lastModified()
        )
    }

    private fun searchFile(
        file: File,
        relative: String,
        query: String,
        regex: Regex?,
        caseSensitive: Boolean,
        maxResults: Int,
        output: MutableList<Map<String, Any?>>
    ) {
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (output.size >= maxResults) return@forEachIndexed
                    val matched = regex?.containsMatchIn(line) ?: if (caseSensitive) {
                        line.contains(query)
                    } else {
                        line.contains(query, ignoreCase = true)
                    }
                    if (matched) {
                        output += mapOf(
                            "path" to relative,
                            "line" to (index + 1),
                            "text" to line.take(500)
                        )
                    }
                }
            }
        } catch (_: MalformedInputException) {
            // Binary or non-text file: intentionally skipped.
        } catch (_: Exception) {
            // Individual unreadable files do not abort the whole search.
        }
    }

    private fun mutationDenied(path: String, requiresUpload: Boolean): FileEditResult? {
        if (!plugin.isMaintenanceMode()) {
            return FileEditResult(false, path, "Maintenance mode is required for file mutation")
        }
        if (!plugin.config.getBoolean("mcp.security.files.write", false)) {
            return FileEditResult(false, path, "File writes are disabled")
        }
        if (requiresUpload && !plugin.config.getBoolean("mcp.security.files.upload", false)) {
            return FileEditResult(false, path, "Binary uploads are disabled")
        }
        return null
    }

    private fun readEnabled(): Boolean =
        plugin.config.getBoolean("mcp.security.files.read", true)

    private fun backupsEnabled(): Boolean =
        plugin.config.getBoolean("mcp.security.files.backups", true)

    private fun shouldBackup(requested: Boolean): Boolean =
        requested && backupsEnabled()

    private fun maxReadBytes(): Long =
        plugin.config.getLong("mcp.security.files.max_read_bytes", 50L * 1024L * 1024L)
            .coerceAtLeast(1024L)

    private fun maxWriteBytes(): Long =
        plugin.config.getLong("mcp.security.files.max_write_bytes", 50L * 1024L * 1024L)
            .coerceAtLeast(1024L)

    private fun maxUploadChunkBytes(): Int =
        plugin.config.getInt("mcp.security.files.max_upload_chunk_bytes", 4 * 1024 * 1024)
            .coerceIn(1024, 16 * 1024 * 1024)

    private fun maxDownloadChunkBytes(): Int =
        plugin.config.getInt("mcp.security.files.max_download_chunk_bytes", 1024 * 1024)
            .coerceIn(1024, 4 * 1024 * 1024)

    private fun maxSearchFileBytes(): Long =
        plugin.config.getLong("mcp.security.files.max_search_file_bytes", 2L * 1024L * 1024L)
            .coerceAtLeast(1024L)

    private fun maxSearchResults(): Int =
        plugin.config.getInt("mcp.security.files.max_search_results", 500).coerceIn(1, 10_000)

    private fun maxSearchFiles(): Int =
        plugin.config.getInt("mcp.security.files.max_search_files", 5_000).coerceIn(1, 100_000)

    private fun maxBatchFiles(): Int =
        plugin.config.getInt("mcp.security.files.max_batch_files", 100).coerceIn(1, 1_000)

    private fun maxBatchTotalLines(): Int =
        plugin.config.getInt("mcp.security.files.max_batch_total_lines", 10_000)
            .coerceIn(100, 100_000)

    private fun maxListEntries(): Int =
        plugin.config.getInt("mcp.security.files.max_list_entries", 2_000).coerceIn(1, 20_000)

    private fun backup(file: File, relativePath: String): String {
        val timestamp = Instant.now().toEpochMilli()
        val safeRelative = relativePath.replace('/', File.separatorChar)
        val backupFile = File(".papermcp-backups", "$safeRelative.$timestamp.bak")
        backupFile.parentFile?.mkdirs()
        file.copyTo(backupFile, overwrite = false)
        return backupFile.canonicalFile.toPath()
            .let {
                File(".").canonicalFile.toPath()
                    .relativize(it)
                    .toString()
                    .replace('\\', '/')
            }
    }

    private fun countOccurrences(text: String, pattern: String): Int {
        var count = 0
        var index = 0
        while (true) {
            val found = text.indexOf(pattern, index)
            if (found < 0) return count
            count++
            index = found + pattern.length
        }
    }

    private fun audit(operation: String, detail: String) {
        if (plugin.config.getBoolean("mcp.security.audit_logging", true)) {
            fileLogger.info("MCP file $operation: $detail")
        }
    }

    private fun readDenied(path: String, reason: String) = FileReadResult(
        success = false,
        path = path,
        message = reason,
        lines = emptyList()
    )

    private fun failure(path: String, reason: String): Map<String, Any?> = mapOf(
        "success" to false,
        "path" to path,
        "message" to reason
    )

    private fun resultFromEdit(result: FileEditResult): Map<String, Any?> = mapOf(
        "success" to result.success,
        "path" to result.path,
        "message" to result.message,
        "backupPath" to result.backupPath
    )
}
