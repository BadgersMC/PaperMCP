package com.badgersmc.papermcp.websocket

import com.badgersmc.papermcp.PaperMCPPlugin
import com.badgersmc.papermcp.bridge.MinecraftBridge
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.javalin.Javalin
import io.javalin.websocket.WsContext
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

private val logger = Logger.getLogger("PaperMCP-WebSocket")

class WebSocketServer(
    private val plugin: PaperMCPPlugin,
    private val bridge: MinecraftBridge
) {
    private val gson = Gson()
    private val running = AtomicBoolean(false)
    private var app: Javalin? = null

    private val clients = ConcurrentHashMap<String, ClientInfo>()
    private val requestHandler = RequestHandler(plugin, bridge)

    fun start() {
        if (running.getAndSet(true)) {
            logger.warning("WebSocket server is already running")
            return
        }

        val host = plugin.config.getString("server.host") ?: "0.0.0.0"
        val port = plugin.config.getInt("server.port", 25577)

        logger.info("Starting WebSocket server on $host:$port...")

        // Start Javalin server on a separate thread to avoid blocking Minecraft main thread
        Thread {
            try {
                app = Javalin.create { config ->
                    config.showJavalinBanner = false
                }.ws("/") { ws ->
                    ws.onConnect { ctx ->
                        // Configure idle timeout for this connection
                        ctx.session.idleTimeout = java.time.Duration.ofHours(1)
                        handleConnect(ctx)
                    }
                    ws.onMessage { ctx ->
                        handleMessage(ctx, ctx.message())
                    }
                    ws.onClose { ctx ->
                        handleDisconnect(ctx)
                    }
                    ws.onError { ctx ->
                        logger.severe("WebSocket error: ${ctx.error()?.message}")
                    }
                }.start(host, port)

                logger.info("WebSocket server started on ws://$host:$port")
            } catch (e: Exception) {
                logger.severe("Failed to start WebSocket server: ${e.message}")
                e.printStackTrace()
                running.set(false)
            }
        }.apply {
            name = "PaperMCP-WebSocket"
            isDaemon = true
        }.start()
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }

        logger.info("Stopping WebSocket server...")
        app?.stop()
        app = null
        clients.clear()
    }

    val isRunning: Boolean
        get() = running.get()

    val connectedClients: Int
        get() = clients.size

    fun getClientList(): Map<String, ClientInfo> = clients.toMap()

    private fun handleConnect(ctx: WsContext) {
        val clientId = ctx.sessionId
        logger.info("Client connecting: $clientId from ${ctx.host()}")

        clients[clientId] = ClientInfo(
            id = clientId,
            remoteAddress = ctx.host(),
            connectedAt = System.currentTimeMillis(),
            authenticated = false
        )

        // Client must authenticate first
        logger.fine("Client $clientId awaiting authentication")
    }

    private fun handleMessage(ctx: WsContext, message: String) {
        val clientId = ctx.sessionId
        val client = clients[clientId] ?: run {
            logger.warning("Received message from unknown client: $clientId")
            return
        }

        try {
            val jsonObject = JsonParser.parseString(message).asJsonObject

            // Check if this is an authentication request
            if (jsonObject.has("type") && jsonObject.get("type").asString == "authenticate") {
                handleAuthentication(ctx, clientId, jsonObject)
                return
            }

            // Check if client is authenticated
            if (!client.authenticated) {
                sendError(ctx, null, -32000, "Not authenticated. Send authentication request first.")
                return
            }

            // Handle the request asynchronously to avoid blocking
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                val response = requestHandler.handle(jsonObject)
                ctx.send(gson.toJson(response))
            })

        } catch (e: Exception) {
            logger.severe("Error handling message from client $clientId: ${e.message}")
            e.printStackTrace()
            sendError(ctx, null, -32603, "Internal error: ${e.message}")
        }
    }

    private fun handleAuthentication(ctx: WsContext, clientId: String, request: JsonObject) {
        val token = request.get("token")?.asString
        val expectedToken = plugin.config.getString("server.authentication_token")

        if (token == expectedToken) {
            clients[clientId] = clients[clientId]!!.copy(authenticated = true)
            logger.info("Client $clientId authenticated successfully")

            ctx.send(gson.toJson(mapOf(
                "type" to "auth_response",
                "success" to true,
                "message" to "Authentication successful"
            )))
        } else {
            logger.warning("Client $clientId failed authentication")
            ctx.send(gson.toJson(mapOf(
                "type" to "auth_response",
                "success" to false,
                "message" to "Invalid authentication token"
            )))
            ctx.session.close()
        }
    }

    private fun handleDisconnect(ctx: WsContext) {
        val clientId = ctx.sessionId
        clients.remove(clientId)
        logger.info("Client disconnected: $clientId")
    }

    private fun sendError(ctx: WsContext, id: Any?, code: Int, message: String) {
        val error = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "error" to mapOf(
                "code" to code,
                "message" to message
            )
        )
        ctx.send(gson.toJson(error))
    }
}

data class ClientInfo(
    val id: String,
    val remoteAddress: String,
    val connectedAt: Long,
    val authenticated: Boolean
) {
    val connectedDuration: String
        get() {
            val seconds = (System.currentTimeMillis() - connectedAt) / 1000
            return "${seconds}s"
        }
}
