package com.badgersmc.papermcp.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpAllowlistTest {
    @Test
    fun `empty allowlist permits connections`() {
        assertTrue(IpAllowlist.isAllowed("203.0.113.10", emptyList()))
    }

    @Test
    fun `exact IPv4 rules are enforced`() {
        assertTrue(IpAllowlist.isAllowed("203.0.113.10", listOf("203.0.113.10")))
        assertFalse(IpAllowlist.isAllowed("203.0.113.11", listOf("203.0.113.10")))
    }

    @Test
    fun `IPv4 CIDR rules are enforced`() {
        assertTrue(IpAllowlist.isAllowed("10.20.30.44", listOf("10.20.30.0/24")))
        assertFalse(IpAllowlist.isAllowed("10.20.31.44", listOf("10.20.30.0/24")))
    }

    @Test
    fun `IPv6 CIDR rules are enforced`() {
        assertTrue(IpAllowlist.isAllowed("2001:db8::42", listOf("2001:db8::/32")))
        assertFalse(IpAllowlist.isAllowed("2001:db9::42", listOf("2001:db8::/32")))
    }
}
