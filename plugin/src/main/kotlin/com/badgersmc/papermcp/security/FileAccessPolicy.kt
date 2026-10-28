package com.badgersmc.papermcp.security

import com.badgersmc.papermcp.PaperMCPPlugin
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

data class PathDecision(
    val allowed: Boolean,
    val absolutePath: Path?,
    val relativePath: String?,
    val reason: String
)

class FileAccessPolicy(private val plugin: PaperMCPPlugin) {
    private val serverRoot: Path = java.io.File(".").canonicalFile.toPath()

    fun resolve(path: String): PathDecision {
        val requested = runCatching { serverRoot.resolve(path).normalize() }.getOrNull()
            ?: return PathDecision(false, null, null, "Invalid path")
        val canonical = runCatching { requested.toFile().canonicalFile.toPath() }.getOrNull()
            ?: return PathDecision(false, null, null, "Unable to resolve path")

        if (!canonical.startsWith(serverRoot)) {
            return PathDecision(false, null, null, "Path is outside the server directory")
        }

        val relative = serverRoot.relativize(canonical)
            .toString()
            .replace('\\', '/')
            .ifBlank { "." }
        val denied = configuredPatterns(
            "mcp.security.files.denied_paths",
            DEFAULT_DENIED_PATHS
        )
        if (denied.any { matchesPath(it, relative) }) {
            return PathDecision(false, canonical, relative, "Path is denied by policy")
        }

        val allowed = configuredPatterns(
            "mcp.security.files.allowed_paths",
            DEFAULT_ALLOWED_PATHS
        )
        if (allowed.none { matchesPath(it, relative) }) {
            return PathDecision(false, canonical, relative, "Path is not in the allowed path set")
        }

        return PathDecision(true, canonical, relative, "Allowed")
    }

    fun serverRoot(): String = serverRoot.absolutePathString()

    fun canTraverse(relativePath: String): Boolean {
        val relative = relativePath
            .replace('\\', '/')
            .removePrefix("./")
            .ifBlank { "." }

        val denied = configuredPatterns(
            "mcp.security.files.denied_paths",
            DEFAULT_DENIED_PATHS
        )
        if (denied.any { matchesPath(it, relative) }) return false

        val allowed = configuredPatterns(
            "mcp.security.files.allowed_paths",
            DEFAULT_ALLOWED_PATHS
        )
        return allowed.any { pattern ->
            matchesPath(pattern, relative) || patternCouldMatchDescendant(pattern, relative)
        }
    }

    private fun patternCouldMatchDescendant(pattern: String, relative: String): Boolean {
        if (relative == ".") return true

        val normalizedPattern = pattern
            .trim()
            .replace('\\', '/')
            .removePrefix("./")
        val wildcardIndex = normalizedPattern.indexOfFirst { it == '*' || it == '?' }
        val fixedPrefix = if (wildcardIndex >= 0) {
            normalizedPattern.substring(0, wildcardIndex).trimEnd('/')
        } else {
            normalizedPattern.trimEnd('/')
        }
        if (fixedPrefix.isBlank() || fixedPrefix == ".") return false

        return fixedPrefix.equals(relative, ignoreCase = true) ||
            fixedPrefix.startsWith("$relative/", ignoreCase = true) ||
            relative.startsWith("$fixedPrefix/", ignoreCase = true)
    }

    private fun matchesPath(pattern: String, relative: String): Boolean {
        return if (File.separatorChar == '\\') {
            GlobMatcher.matches(pattern.lowercase(), relative.lowercase())
        } else {
            GlobMatcher.matches(pattern, relative)
        }
    }

    private fun configuredPatterns(path: String, fallback: List<String>): List<String> {
        return if (plugin.config.contains(path)) {
            plugin.config.getStringList(path).filter { it.isNotBlank() }
        } else {
            fallback
        }
    }

    companion object {
        val DEFAULT_ALLOWED_PATHS = listOf(
            ".",
            "server.properties",
            "paper*.yml",
            "bukkit.yml",
            "spigot.yml",
            "plugins/**",
            "logs/**",
            "config/**"
        )

        val DEFAULT_DENIED_PATHS = listOf(
            "**/.env",
            ".env",
            "**/*.key",
            "**/*.pem",
            "**/*.p12",
            "**/*.pfx",
            "**/secrets/**",
            "**/.ssh/**",
            "plugins/PaperMCP/config.yml*"
        )
    }
}

object GlobMatcher {
    fun matches(pattern: String, candidate: String): Boolean {
        val glob = pattern.trim().replace('\\', '/').removePrefix("./")
        val path = candidate.replace('\\', '/').removePrefix("./")
        if (glob == "**") return true

        val regex = buildString {
            append('^')
            var index = 0
            while (index < glob.length) {
                val char = glob[index]
                when {
                    char == '/' && index + 2 < glob.length &&
                        glob[index + 1] == '*' && glob[index + 2] == '*' &&
                        index + 3 == glob.length -> {
                        append("(?:/.*)?")
                        index += 3
                    }
                    char == '*' && index + 1 < glob.length && glob[index + 1] == '*' -> {
                        if (index + 2 < glob.length && glob[index + 2] == '/') {
                            append("(?:.*/)?")
                            index += 3
                        } else {
                            append(".*")
                            index += 2
                        }
                    }
                    char == '*' -> {
                        append("[^/]*")
                        index++
                    }
                    char == '?' -> {
                        append("[^/]")
                        index++
                    }
                    char in ".+()^$|{}[]" -> {
                        append('\\').append(char)
                        index++
                    }
                    else -> {
                        append(char)
                        index++
                    }
                }
            }
            append('$')
        }

        return Regex(regex, RegexOption.IGNORE_CASE).matches(path)
    }
}
