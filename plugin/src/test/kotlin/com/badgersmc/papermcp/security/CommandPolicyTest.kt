package com.badgersmc.papermcp.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPolicyTest {
    @Test
    fun normalCommandsRequireTheNormalAllowlist() {
        assertTrue(
            CommandPolicy.isAllowed(
                commandLine = "list",
                normalWhitelist = listOf("list"),
                maintenanceWhitelist = listOf("nexo"),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
        assertFalse(
            CommandPolicy.isAllowed(
                commandLine = "nexo reload",
                normalWhitelist = listOf("list"),
                maintenanceWhitelist = listOf("nexo"),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
    }
    @Test
    fun maintenanceAllowlistActivatesOnlyInMaintenanceMode() {
        assertTrue(
            CommandPolicy.isAllowed(
                commandLine = "nexo reload",
                normalWhitelist = listOf("list"),
                maintenanceWhitelist = listOf("nexo"),
                blacklist = emptyList(),
                maintenanceMode = true,
                allowWhitelistWildcards = false
            )
        )
    }

    @Test
    fun blacklistWinsOverAllowlistsAndNamespaceAliases() {
        assertFalse(
            CommandPolicy.isAllowed(
                commandLine = "minecraft:stop",
                normalWhitelist = listOf("stop"),
                maintenanceWhitelist = emptyList(),
                blacklist = listOf("stop"),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
    }
    @Test
    fun legacySuffixWildcardsDowngradeToExactNames() {
        assertTrue(
            CommandPolicy.isAllowed(
                commandLine = "lg info",
                normalWhitelist = listOf("lg*"),
                maintenanceWhitelist = emptyList(),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
        assertFalse(
            CommandPolicy.isAllowed(
                commandLine = "lgadmin wipe",
                normalWhitelist = listOf("lg*"),
                maintenanceWhitelist = emptyList(),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
    }

    @Test
    fun wildcardsCanBeExplicitlyReenabled() {
        assertTrue(
            CommandPolicy.isAllowed(
                commandLine = "lgadmin status",
                normalWhitelist = listOf("lg*"),
                maintenanceWhitelist = emptyList(),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = true
            )
        )
    }

    @Test
    fun emptyWhitelistFailsClosed() {
        assertFalse(
            CommandPolicy.isAllowed(
                commandLine = "list",
                normalWhitelist = emptyList(),
                maintenanceWhitelist = emptyList(),
                blacklist = emptyList(),
                maintenanceMode = false,
                allowWhitelistWildcards = false
            )
        )
    }
}
