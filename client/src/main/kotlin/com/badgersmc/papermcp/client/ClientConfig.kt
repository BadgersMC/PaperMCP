package com.badgersmc.papermcp.client

import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path

data class ClientConfig(
    val host: String,
    val port: Int,
    val token: String,
    val use_ssl: Boolean = false,
    val reconnect: ReconnectConfig = ReconnectConfig()
) {
    data class ReconnectConfig(
        val enabled: Boolean = true,
        val max_attempts: Int = 10,
        val delay_ms: Long = 5000
    )

    companion object {
        private val gson = Gson()

        fun load(path: Path): ClientConfig {
            val json = Files.readString(path)
            return gson.fromJson(json, ClientConfig::class.java)
        }
    }

    fun getWebSocketUrl(): String {
        val protocol = if (use_ssl) "wss" else "ws"
        return "$protocol://$host:$port/"
    }
}
