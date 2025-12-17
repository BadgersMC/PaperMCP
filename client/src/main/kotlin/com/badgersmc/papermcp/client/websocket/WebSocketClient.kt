package com.badgersmc.papermcp.client.websocket

import com.badgersmc.papermcp.client.ClientConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mu.KotlinLogging
import org.java_websocket.client.WebSocketClient as JavaWebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

class WebSocketClient(private val config: ClientConfig) {

    private val gson = Gson()
    private var wsClient: JavaWebSocketClient? = null
    private var authenticated = false

    private val requestIdCounter = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()

    private var messageHandler: ((JsonObject) -> Unit)? = null

    fun connect() {
        val uri = URI(config.getWebSocketUrl())
        logger.info { "Connecting to $uri..." }

        wsClient = object : JavaWebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                logger.info { "WebSocket connected" }
                // Enable ping/pong to keep connection alive
                this.setConnectionLostTimeout(0) // Disable client-side timeout
                authenticate()
            }

            override fun onMessage(message: String) {
                handleMessage(message)
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                logger.warn { "WebSocket closed: $reason (code: $code)" }
                authenticated = false

                if (config.reconnect.enabled && remote) {
                    scheduleReconnect()
                }
            }

            override fun onError(ex: Exception) {
                logger.error(ex) { "WebSocket error" }
            }
        }

        wsClient?.connectBlocking(10, TimeUnit.SECONDS)
            ?: throw Exception("Failed to connect to server")
    }

    fun disconnect() {
        logger.info { "Disconnecting..." }
        wsClient?.closeBlocking()
        wsClient = null
        authenticated = false
    }

    fun isConnected(): Boolean = wsClient?.isOpen == true && authenticated

    fun setMessageHandler(handler: (JsonObject) -> Unit) {
        this.messageHandler = handler
    }

    fun sendRequest(method: String, params: JsonObject? = null): CompletableFuture<JsonObject> {
        val requestId = requestIdCounter.incrementAndGet()
        val future = CompletableFuture<JsonObject>()

        pendingRequests[requestId] = future

        val request = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", requestId)
            addProperty("method", method)
            if (params != null) {
                add("params", params)
            }
        }

        wsClient?.send(gson.toJson(request))

        // Timeout after 30 seconds
        future.orTimeout(30, TimeUnit.SECONDS)
            .exceptionally { ex ->
                logger.error { "Request $requestId timed out or failed: ${ex.message}" }
                pendingRequests.remove(requestId)
                null
            }

        return future
    }

    private fun authenticate() {
        logger.info { "Authenticating..." }

        val authRequest = JsonObject().apply {
            addProperty("type", "authenticate")
            addProperty("token", config.token)
        }

        wsClient?.send(gson.toJson(authRequest))
    }

    private fun handleMessage(message: String) {
        try {
            val json = JsonParser.parseString(message).asJsonObject

            // Check if it's an authentication response
            if (json.has("type") && json.get("type").asString == "auth_response") {
                val success = json.get("success").asBoolean
                if (success) {
                    logger.info { "Authentication successful" }
                    authenticated = true
                } else {
                    logger.error { "Authentication failed: ${json.get("message")?.asString}" }
                    wsClient?.close()
                }
                return
            }

            // Check if it's a response to a pending request
            if (json.has("id")) {
                val id = json.get("id").asInt
                val future = pendingRequests.remove(id)
                future?.complete(json)
                return
            }

            // Otherwise, pass to message handler (for notifications, etc.)
            messageHandler?.invoke(json)

        } catch (e: Exception) {
            logger.error(e) { "Error handling message: $message" }
        }
    }

    private fun scheduleReconnect() {
        logger.info { "Scheduling reconnect in ${config.reconnect.delay_ms}ms..." }
        Thread.sleep(config.reconnect.delay_ms)

        try {
            connect()
        } catch (e: Exception) {
            logger.error(e) { "Reconnect failed" }
            scheduleReconnect()
        }
    }
}
