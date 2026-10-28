# PaperMCP - Remote Minecraft Server Control via AI

**Connect Claude (or any MCP client) to your remote Minecraft server from anywhere**

PaperMCP enables AI assistants like Claude to monitor and manage your Minecraft server remotely through the Model Context Protocol (MCP). Perfect for server administrators who want AI-powered automation and monitoring.

## Architecture

```
[Your PC]                                    [Remote Datacenter]
┌──────────────────────────┐                ┌────────────────────────┐
│ Claude Desktop           │                │ Minecraft Server       │
│         ↓                │                │   ┌─────────────────┐  │
│ PaperMCP Client          │◄───WebSocket───────│ PaperMCP Plugin │  │
│ (MCP Server on stdio)    │   (Internet)   │   │ (WS Server)     │  │
└──────────────────────────┘                │   └─────────────────┘  │
                                            └────────────────────────┘
```

**Two Components:**
1. **PaperMCP Plugin** - Runs on your Minecraft server, exposes WebSocket API
2. **PaperMCP Client** - Runs on your PC, connects Claude to the remote server

## 📚 Documentation

- **[Complete Setup Guide](SETUP_GUIDE.md)** - Step-by-step installation and configuration (START HERE!)
- **[Usage Examples](USAGE_EXAMPLES.md)** - Real-world examples of what you can do with AI + Minecraft
- **[Quick Reference](QUICK_REFERENCE.md)** - Fast lookup for commands, configs, and troubleshooting

## Features

### Real-time Monitoring
- Server health (TPS, memory, uptime)
- Online players with locations and stats
- Console logs and output
- Player chat history

### Server Management
- Execute commands (with security controls)
- Send messages to players
- Manage time/weather/gamerules
- Teleport players

### Security
- Token-based authentication with constant-time comparison
- Enforced exact-IP and CIDR connection allowlist
- Normal and maintenance access modes
- Command allowlist/denylist with legacy wildcard hardening
- Server-root file sandbox with path allow/deny rules
- Sensitive-file deny rules, mutation backups, and audit logging
- Encrypted WebSocket connections through a TLS reverse proxy when required

## Quick Start

### 1. Install Plugin on Minecraft Server

```bash
# Build the plugin
./gradlew :plugin:build

# Copy to your server
scp plugin/build/libs/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar user@yourserver:/path/to/plugins/
```

**Configure Plugin** (`plugins/PaperMCP/config.yml`):
```yaml
server:
  host: "0.0.0.0"         # Listen on all interfaces
  port: 25577              # WebSocket port
  max_websocket_message_bytes: 8388608
  authentication_token: "your-super-secret-token-here"  # CHANGE THIS!
  allowed_ips:
    - "203.0.113.10"       # Exact IP or CIDR; do not leave empty on production

mcp:
  security:
    maintenance_mode: false
    allow_command_wildcards: false  # legacy "name*" entries safely downgrade to exact "name"

    command_whitelist:
      - "list"
      - "tps"
      - "time"
      - "weather"

    maintenance_command_whitelist:
      - "spark"
      - "lg"
      - "lumaguilds"
      - "nexo"
      - "geyser"

    command_blacklist:
      - "stop"
      - "restart"
      - "reload"
      - "op"
      - "deop"

    files:
      read: true
      write: true     # still requires maintenance_mode
      upload: true    # still requires maintenance_mode
      delete: false
```

**Open Firewall Port:**
```bash
# Allow WebSocket connections
sudo ufw allow 25577/tcp
```

### 2. Install Client on Your PC

```bash
# Build the client
./gradlew :client:build

# Copy client JAR to your PC
cp client/build/libs/PaperMCP-Client-1.0.0-SNAPSHOT.jar ~/papermcp/
```

**Configure Client** (`papermcp-client-config.json`):
```json
{
  "host": "your-minecraft-server.com",
  "port": 25577,
  "token": "your-super-secret-token-here",
  "use_ssl": false,
  "reconnect": {
    "enabled": true,
    "max_attempts": 10,
    "delay_ms": 5000
  }
}
```

### 3. Configure Claude Desktop

Add to your Claude Desktop config:

**Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Linux**: `~/.config/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": [
        "-jar",
        "/full/path/to/PaperMCP-Client-1.0.0-SNAPSHOT.jar",
        "/full/path/to/papermcp-client-config.json"
      ]
    }
  }
}
```

### 4. Test It!

1. Start your Minecraft server (plugin auto-starts)
2. Restart Claude Desktop
3. Ask Claude: "What's the TPS on my Minecraft server?"

## Available Tools

### `minecraft_server_info`
Get server stats, TPS, version, player count
```
AI: "How's my server performing?"
```

### `minecraft_list_players`
List online players with details
```
AI: "Who's online and where are they?"
```

### `minecraft_read_console`
Read recent console output
```
AI: "Show me the last 50 console messages"
```

### `minecraft_read_chat`
Read player chat history
```
AI: "What have players been talking about?"
```

### `minecraft_execute_command`
Execute server commands (with security)
```
AI: "Set the time to day and clear the weather"
```

### `minecraft_send_message`
Send messages to players
```
AI: "Announce that the server will restart in 10 minutes"
```

### File inspection and maintenance tools
PaperMCP also exposes:
- `minecraft_list_directory` and `minecraft_file_stat`
- `minecraft_read_file`, `minecraft_read_file_range`, and `minecraft_batch_read_files`
- `minecraft_search_files` and `minecraft_file_hash`
- `minecraft_download_file` for chunked binary/database retrieval
- `minecraft_edit_file`, `minecraft_copy_file`, and `minecraft_move_file`
- `minecraft_upload_file` for chunked JAR/ZIP/mcpack transfer
- `minecraft_delete_file` (disabled by default)

Read operations follow the configured server-root path policy. Mutating operations additionally require
PaperMCP maintenance mode; deletion requires its own explicit configuration switch. The live
`/papermcp maintenance on` toggle is intentionally ephemeral and resets to the configured
`maintenance_mode` value after a config reload or server restart.

## Use Cases

### 1. Automated Monitoring
```
AI: "Check server health every hour and notify me if TPS drops below 18"
```

### 2. Player Support
```
AI: "Monitor chat and respond to questions about server rules"
```

### 3. Administrative Tasks
```
AI: "If anyone complains about lag, check TPS and report back"
```

### 4. Event Management
```
AI: "At 8 PM, tp all players to spawn and announce the event"
```

## Security Best Practices

### 🔒 Critical Security Steps

1. **Change Default Token**
   - Use a long, random token (32+ characters)
   - Never commit tokens to git
   - Rotate tokens regularly

2. **Use Command Whitelist**
   ```yaml
   command_whitelist:
     - "list"      # Only allow specific commands
     - "tps"
     - "help"
   # Empty whitelist = allow all (dangerous!)
   ```

3. **Enable SSL/TLS**
   ```json
   {
     "use_ssl": true  # In client config
   }
   ```
   Configure nginx/caddy reverse proxy for SSL termination

4. **IP Whitelisting**
   ```yaml
   allowed_ips:
     - "your.home.ip.address"
     - "your.office.ip.address/24"
   ```

5. **Audit Logging**
   ```yaml
   audit_logging: true  # Track all AI actions
   ```

### Firewall Configuration

Only expose WebSocket port, not Minecraft port:

```bash
# Good: WebSocket on custom port
sudo ufw allow 25577/tcp

# Don't expose Minecraft port to internet if not needed
# sudo ufw allow 25565/tcp  # Only if players connect directly
```

### SSL/TLS Setup (Recommended)

Use nginx or Caddy as reverse proxy:

**Caddy** (easiest):
```
your-minecraft-server.com:25577 {
    reverse_proxy localhost:25577
}
```

**nginx**:
```nginx
server {
    listen 443 ssl;
    server_name mc.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:25577;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Then use `wss://mc.yourdomain.com` in client config.

## Commands

### Plugin Commands (In-Game)

- `/papermcp status` - Show WebSocket server status
- `/papermcp clients` - List connected clients
- `/papermcp reload` - Reload configuration
- `/papermcp maintenance <on|off|status>` - Control the file-mutation/maintenance gate
- `/papermcp help` - Show help

### Client (Command Line)

```bash
# Run with default config (./papermcp-client-config.json)
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar

# Run with custom config
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar /path/to/config.json

# Enable debug logging
java -Dlogback.configurationFile=logback-debug.xml -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar
```

## Building from Source

**Requirements:**
- Java 21 (for plugin)
- Java 17+ (for client)

```bash
# Build everything
./gradlew build

# Build just plugin (requires Java 21)
./gradlew :plugin:build

# Build just client (requires Java 17+)
./gradlew :client:build

# Outputs:
# plugin/build/libs/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar
# client/build/libs/PaperMCP-Client-1.0.0-SNAPSHOT.jar
```

## Troubleshooting

### Plugin Won't Start

**Error: "Please set a secure authentication_token"**
- Edit `plugins/PaperMCP/config.yml`
- Change `CHANGE_ME_TO_A_SECURE_TOKEN` to a random string

**Port Already in Use**
- Change `server.port` in config.yml
- Or stop conflicting service: `sudo lsof -i :25577`

### Client Can't Connect

**Authentication Failed**
- Ensure tokens match in plugin and client configs
- Tokens are case-sensitive

**Connection Refused**
- Check firewall: `sudo ufw status`
- Verify port is open: `telnet yourserver.com 25577`
- Check plugin logs: `logs/latest.log`

**SSL/TLS Errors**
- If using `use_ssl: true`, ensure reverse proxy is configured
- Check certificate validity
- Try with `use_ssl: false` first to isolate issue

### Claude Desktop Not Seeing Tools

**Tools Don't Appear**
- Restart Claude Desktop after config changes
- Check client logs for errors
- Verify JSON syntax in `claude_desktop_config.json`

**"Request timed out"**
- Client may not be connected to server
- Check client logs
- Verify server is running: `/papermcp status`

## Development

### Project Structure

```
PaperMCP/
├── plugin/                       # Minecraft server plugin
│   └── src/main/kotlin/com/badgersmc/papermcp/
│       ├── PaperMCPPlugin.kt    # Main plugin class
│       ├── bridge/              # Minecraft API bridge
│       │   ├── MinecraftBridge.kt
│       │   └── EventListeners.kt
│       └── websocket/           # WebSocket server
│           ├── WebSocketServer.kt
│           └── RequestHandler.kt
│
└── client/                       # PC client application
    └── src/main/kotlin/com/badgersmc/papermcp/client/
        ├── PaperMCPClient.kt    # Main entry point
        ├── ClientConfig.kt      # Configuration
        ├── websocket/           # WebSocket client
        │   └── WebSocketClient.kt
        └── mcp/                 # MCP protocol
            ├── MCPProtocolHandler.kt
            └── ToolRegistry.kt
```

### Adding New Tools

**1. Add method to RequestHandler.kt (plugin)**
```kotlin
private fun handleMyNewTool(id: Any?, params: JsonObject?): JsonObject {
    // Implement tool logic
    return createSuccess(id, result)
}
```

**2. Add tool to ToolRegistry.kt (client)**
```kotlin
class MyNewTool : MCPTool {
    override val name = "minecraft_my_tool"
    override val definition = ToolDefinition(...)
    override fun execute(client: WebSocketClient, arguments: JsonObject) =
        client.sendRequest("my_new_tool", arguments)
            .thenApply { it.get("result").asJsonObject }
}
```

### Running Tests

```bash
./gradlew test
```

## Roadmap

- [x] WebSocket server plugin
- [x] Remote client with MCP protocol
- [x] Token authentication
- [x] Command execution with security
- [x] Chat and console monitoring
- [ ] SSL/TLS support (native)
- [ ] Inventory management tools
- [ ] World editing capabilities
- [ ] Multi-server support (proxy mode)
- [ ] Web dashboard
- [ ] Metrics and analytics
- [ ] Plugin API for extensions

## FAQ

**Q: Is this secure for production use?**
A: With proper configuration (strong tokens, command whitelist, SSL, IP filtering), yes. Always audit what AI has access to.

**Q: Does this work with Forge/Fabric?**
A: Currently only Paper/Spigot. Forge/Fabric support planned.

**Q: Can multiple people connect?**
A: Yes, the WebSocket server supports multiple concurrent clients.

**Q: What's the performance impact?**
A: Minimal. The plugin uses async I/O and runs on separate threads.

**Q: Does this work with BungeeCord/Velocity?**
A: Yes, install the plugin on each backend server. Proxy support coming soon.

**Q: What Java version is required?**
A: The plugin requires **Java 21** (for Minecraft 1.21+). The client requires Java 17+. Make sure your server is running Java 21 before installing the plugin.

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## Support

- **Issues**: [GitHub Issues](https://github.com/BadgersMC/PaperMCP/issues)
- **Pull Requests**: Contributions welcome!
- **Docs**: See the markdown files in this repository

## Credits

Built with ❤️ by BadgersMC

**Technologies:**
- [Model Context Protocol](https://modelcontextprotocol.io) by Anthropic
- [Paper MC](https://papermc.io) - High-performance Minecraft server
- [Javalin](https://javalin.io) - Lightweight web framework
- [Kotlin](https://kotlinlang.org) - Modern JVM language

---

⚠️ **Disclaimer**: This tool gives AI access to your Minecraft server. Use appropriate security measures and only connect trusted AI clients. The authors are not responsible for any damage caused by misconfiguration or misuse.
