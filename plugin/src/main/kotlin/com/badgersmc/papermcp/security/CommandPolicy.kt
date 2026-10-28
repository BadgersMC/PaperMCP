package com.badgersmc.papermcp.security

/**
 * Evaluates server commands against explicit normal/maintenance allowlists
 * and an always-on denylist.
 */
object CommandPolicy {
    fun isAllowed(
        commandLine: String,
        normalWhitelist: List<String>,
        maintenanceWhitelist: List<String>,
        blacklist: List<String>,
        maintenanceMode: Boolean,
        allowWhitelistWildcards: Boolean
    ): Boolean {
        val baseCommand = commandLine
            .trim()
            .trimStart('/')
            .substringBefore(' ')
            .lowercase()
        if (baseCommand.isBlank()) return false

        val commandNames = setOf(baseCommand, baseCommand.substringAfter(':'))

        if (blacklist.any { pattern ->
                commandNames.any { command -> patternMatches(command, pattern) }
            }
        ) {
            return false
        }
        val whitelist = buildList {
            addAll(normalWhitelist)
            if (maintenanceMode) addAll(maintenanceWhitelist)
        }
        if (whitelist.isEmpty()) return false

        return whitelist.any { pattern ->
            val normalized = pattern.trim().lowercase()
            val simpleLegacySuffixWildcard =
                !allowWhitelistWildcards &&
                    normalized.endsWith("*") &&
                    !normalized.startsWith("*") &&
                    normalized.count { it == '*' } == 1

            when {
                simpleLegacySuffixWildcard -> {
                    val exactLegacyName = normalized.removeSuffix("*")
                    commandNames.any { it == exactLegacyName }
                }
                '*' in normalized && !allowWhitelistWildcards -> false
                else -> commandNames.any { command -> patternMatches(command, normalized) }
            }
        }
    }

    internal fun patternMatches(command: String, pattern: String): Boolean {
        val normalized = pattern.trim().lowercase()
        if (normalized.isEmpty()) return false
        if (normalized == "*") return true

        return when {
            normalized.startsWith("*") && normalized.endsWith("*") ->
                command.contains(normalized.trim('*'))
            normalized.startsWith("*") ->
                command.endsWith(normalized.trimStart('*'))
            normalized.endsWith("*") ->
                command.startsWith(normalized.trimEnd('*'))
            else -> command == normalized
        }
    }
}
