package com.badgersmc.papermcp.security

import java.net.InetAddress

/**
 * Exact-IP and CIDR matcher used by the WebSocket admission gate.
 *
 * An empty allowlist means "allow all" to preserve the documented config
 * semantics. When entries are present, the remote peer must match one.
 */
object IpAllowlist {
    fun isAllowed(remoteAddress: String, rules: List<String>): Boolean {
        if (rules.isEmpty()) return true

        val normalizedRemote = remoteAddress
            .trim()
            .removePrefix("/")
            .substringBefore('%')
        val remote = runCatching { InetAddress.getByName(normalizedRemote) }.getOrNull()
            ?: return false

        return rules.any { rule -> matches(remote, rule.trim()) }
    }

    private fun matches(remote: InetAddress, rule: String): Boolean {
        if (rule.isBlank()) return false
        if ('/' !in rule) {
            val candidate = runCatching {
                InetAddress.getByName(rule.removePrefix("/").substringBefore('%'))
            }.getOrNull() ?: return false
            return remote.address.contentEquals(candidate.address)
        }

        val parts = rule.split('/', limit = 2)
        if (parts.size != 2) return false

        val network = runCatching { InetAddress.getByName(parts[0]) }.getOrNull()
            ?: return false
        val prefix = parts[1].toIntOrNull() ?: return false
        val remoteBytes = remote.address
        val networkBytes = network.address

        if (remoteBytes.size != networkBytes.size) return false
        val maxBits = remoteBytes.size * 8
        if (prefix !in 0..maxBits) return false

        var bitsRemaining = prefix
        for (index in remoteBytes.indices) {
            if (bitsRemaining <= 0) return true
            val bits = minOf(8, bitsRemaining)
            val mask = (0xFF shl (8 - bits)) and 0xFF
            val remoteByte = remoteBytes[index].toInt() and 0xFF
            val networkByte = networkBytes[index].toInt() and 0xFF
            if ((remoteByte and mask) != (networkByte and mask)) return false
            bitsRemaining -= bits
        }

        return true
    }
}
