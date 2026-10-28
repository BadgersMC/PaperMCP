package com.badgersmc.papermcp.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobMatcherTest {
    @Test
    fun `double star matches nested plugin paths`() {
        assertTrue(GlobMatcher.matches("plugins/**", "plugins"))
        assertTrue(GlobMatcher.matches("plugins/**", "plugins/Nexo/items/lg.yml"))
        assertTrue(GlobMatcher.matches("plugins/**", "plugins/Nexo/config.yml"))
        assertTrue(GlobMatcher.matches("plugins/**", "Plugins/Nexo/config.yml"))
        assertFalse(GlobMatcher.matches("plugins/**", "logs/latest.log"))
    }

    @Test
    fun `single star stays at root level`() {
        assertTrue(GlobMatcher.matches("*", "server.properties"))
        assertFalse(GlobMatcher.matches("*", "plugins/Nexo/config.yml"))
    }

    @Test
    fun `sensitive file patterns match root and nested files`() {
        assertTrue(GlobMatcher.matches(".env", ".env"))
        assertTrue(GlobMatcher.matches("**/.env", "plugins/Test/.env"))
        assertTrue(GlobMatcher.matches("**/*.pem", "plugins/Test/keys/server.pem"))
        assertFalse(GlobMatcher.matches("**/*.pem", "plugins/Test/config.yml"))
    }
}
