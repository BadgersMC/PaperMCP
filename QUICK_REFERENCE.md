# PaperMCP Quick Reference

Fast lookup guide for PaperMCP setup and usage.

## Setup Checklist

### Server (Plugin)

- [ ] Java 21 installed
- [ ] Build plugin: `./gradlew :plugin:build`
- [ ] Upload JAR to `plugins/` folder
- [ ] Edit `plugins/PaperMCP/config.yml`
- [ ] Set authentication token (min 32 chars)
- [ ] Configure command whitelist/blacklist
- [ ] Open firewall port: `sudo ufw allow 25577/tcp`
- [ ] Restart server
- [ ] Verify in logs: "PaperMCP enabled successfully!"

### Client (Your PC)

- [ ] Java 17+ installed
- [ ] Build client: `./gradlew :client:build`
- [ ] Create config directory
- [ ] Copy JAR to config directory
- [ ] Create `config.json` with server details
- [ ] Test connection: `java -jar PaperMCP-Client-*.jar config.json`
- [ ] Edit Claude Desktop config
- [ ] Restart Claude Desktop
- [ ] Verify tools appear (hammer icon 🔨)

---

## File Locations

### Server
```
Plugin JAR:  plugins/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar
Config:      plugins/PaperMCP/config.yml
Logs:        logs/latest.log
```

### Client (Windows)
```
JAR:          C:\PaperMCP\PaperMCP-Client-1.0.0-SNAPSHOT.jar
Config:       C:\PaperMCP\config.json
Claude Config: %APPDATA%\Claude\claude_desktop_config.json
```

### Client (Linux/Mac)
```
JAR:          ~/papermcp/PaperMCP-Client-1.0.0-SNAPSHOT.jar
Config:       ~/papermcp/config.json
Claude Config: ~/.config/Claude/claude_desktop_config.json (Linux)
               ~/Library/Application Support/Claude/claude_desktop_config.json (Mac)
```

---

## Configuration Templates

### Plugin: config.yml (Minimal)

```yaml
server:
  host: "0.0.0.0"
  port: 25577
  authentication_token: "YOUR-32-CHAR-RANDOM-TOKEN-HERE"

mcp:
  security:
    command_whitelist:
      - "list"
      - "tps"
      - "time*"
      - "weather*"
    command_blacklist:
      - "stop"
      - "*op*"
      - "ban*"
    audit_logging: true
```

### Client: config.json

```json
{
  "host": "yourserver.com",
  "port": 25577,
  "token": "YOUR-32-CHAR-RANDOM-TOKEN-HERE",
  "use_ssl": false,
  "reconnect": {
    "enabled": true,
    "max_attempts": 10,
    "delay_ms": 5000
  }
}
```

### Claude Desktop: claude_desktop_config.json (Windows)

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\PaperMCP\\PaperMCP-Client-1.0.0-SNAPSHOT.jar",
        "C:\\PaperMCP\\config.json"
      ]
    }
  }
}
```

### Claude Desktop: claude_desktop_config.json (Linux/Mac)

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": [
        "-jar",
        "/home/user/papermcp/PaperMCP-Client-1.0.0-SNAPSHOT.jar",
        "/home/user/papermcp/config.json"
      ]
    }
  }
}
```

---

## Commands

### In-Game Commands

```
/papermcp status          Show WebSocket server status
/papermcp clients         List connected clients
/papermcp reload          Reload configuration
/papermcp help            Show help
```

### CLI Commands

```bash
# Build
./gradlew build                    # Build everything
./gradlew :plugin:build            # Plugin only
./gradlew :client:build            # Client only

# Run client
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json

# Debug mode
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
     -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json

# Server operations
sudo ufw allow 25577/tcp           # Open firewall
sudo ufw status                    # Check firewall
telnet yourserver.com 25577        # Test connection
tail -f logs/latest.log | grep PaperMCP  # Watch logs
```

---

## Available Tools

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `minecraft_server_info` | Server stats, TPS, uptime | None |
| `minecraft_list_players` | Online players with details | `player` (optional) |
| `minecraft_read_console` | Console logs | `limit` (default: 100) |
| `minecraft_read_chat` | Chat history | `limit`, `player` (optional) |
| `minecraft_execute_command` | Execute commands | `command` (required) |
| `minecraft_send_message` | Send messages | `message`, `target`, `prefix` |

---

## Common Prompts

### Monitoring
```
"What's the server TPS?"
"Who's online and where are they?"
"Check for errors in the last 100 console lines"
"What's the server uptime?"
```

### Player Info
```
"Is [player] online?"
"Where is [player]?"
"What have players been chatting about?"
"Who's been most active today?"
```

### Administration
```
"Set time to day"
"Clear the weather"
"Teleport [player1] to [player2]"
"Announce [message] to everyone"
"Set [player] to creative mode"
```

### Analysis
```
"Analyze server performance over the last hour"
"What are players most interested in?"
"Summarize today's activity"
"Any issues I should know about?"
```

---

## Troubleshooting Quick Fixes

### Plugin won't start
```bash
# Check Java version (need 21)
java -version

# Check logs for errors
tail -f logs/latest.log | grep ERROR

# Verify token is set
grep authentication_token plugins/PaperMCP/config.yml
```

### Can't connect to server
```bash
# Test connectivity
telnet yourserver.com 25577
ping yourserver.com

# Check firewall
sudo ufw status
sudo ss -tulpn | grep 25577

# Check server logs
grep PaperMCP logs/latest.log
```

### Client authentication fails
```bash
# Verify tokens match (case sensitive!)
# Server: plugins/PaperMCP/config.yml
# Client: config.json

# Check server accepts connection
tail -f logs/latest.log | grep "Client connecting"
```

### Claude Desktop doesn't see tools
```bash
# Verify config path is correct (absolute path!)
# Restart Claude Desktop completely (Quit, not just close)
# Test client manually first:
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json

# Check Claude logs
# Windows: %APPDATA%\Claude\logs
# Mac: ~/Library/Logs/Claude
# Linux: ~/.config/Claude/logs
```

---

## Security Checklist

- [ ] Strong authentication token (32+ chars, random)
- [ ] Command whitelist enabled
- [ ] Dangerous commands blacklisted (`*op*`, `stop`, `ban*`)
- [ ] Audit logging enabled
- [ ] SSL/TLS if connecting over internet
- [ ] IP whitelist if possible
- [ ] Regular log review
- [ ] Token rotation schedule
- [ ] Firewall configured (only needed ports)
- [ ] Tested in dev before production

---

## Port Reference

```
WebSocket:  25577 (default, configurable)
Minecraft:  25565 (separate, don't expose if not needed)
SSL/HTTPS:  443 (if using reverse proxy)
```

---

## Command Security Examples

### Read-Only (Safest)
```yaml
command_whitelist:
  - "list"
  - "tps"
  - "help"
  - "version"
command_blacklist:
  - "*"  # Block everything else
```

### Moderate (Recommended)
```yaml
command_whitelist:
  - "list"
  - "tps"
  - "time*"
  - "weather*"
  - "gamemode"
  - "tp"
command_blacklist:
  - "stop"
  - "*op*"
  - "ban*"
  - "kick"
```

### Administrative (Use with caution)
```yaml
command_whitelist: []  # Allow all
command_blacklist:
  - "stop"
  - "restart"
  - "*op*"
  - "ban*"
```

---

## Performance Tips

### Plugin
- Runs on separate thread (minimal impact)
- Async I/O (non-blocking)
- Configurable buffer sizes
- Expected impact: < 0.1 TPS

### Client
- Lightweight (< 50 MB RAM)
- Auto-reconnect on disconnect
- Timeout protection (30s default)
- Can run as background service

---

## Useful Log Searches

```bash
# Connection attempts
grep "Client connecting" logs/latest.log

# Authentication
grep "Authentication" logs/latest.log

# Command execution (audit)
grep "MCP executing command" logs/latest.log

# Errors
grep ERROR logs/latest.log | grep PaperMCP

# All PaperMCP activity
grep PaperMCP logs/latest.log
```

---

## Version Requirements

| Component | Requirement |
|-----------|-------------|
| Server Plugin | Java 21, Paper/Spigot 1.21+ |
| Client | Java 17+, Any OS |
| Claude Desktop | Latest version |
| Network | Port forwarding or reverse proxy |

---

## Emergency Commands

### Kill Stuck Client
```bash
# Windows
taskkill /F /IM java.exe

# Linux/Mac
pkill -f PaperMCP-Client
```

### Disable Plugin
```bash
# On server
cd plugins
mv PaperMCP-Plugin-*.jar ../disabled_plugins/
# Restart server
```

### Reset Configuration
```bash
# Server
rm plugins/PaperMCP/config.yml
# Restart to regenerate

# Client
# Just edit config.json directly
```

---

## Support Resources

- **Full Setup Guide:** `SETUP_GUIDE.md`
- **Usage Examples:** `USAGE_EXAMPLES.md`
- **GitHub Issues:** https://github.com/BadgersMC/PaperMCP/issues

---

## Quick Build & Deploy

```bash
# Build both components
./gradlew build

# Plugin
scp plugin/build/libs/PaperMCP-Plugin-*.jar user@server:/path/to/plugins/

# Client (copy to your PC)
cp client/build/libs/PaperMCP-Client-*.jar C:\PaperMCP\
# or
cp client/build/libs/PaperMCP-Client-*.jar ~/papermcp/
```

---

**Last Updated:** 2025-12-16
**Version:** 1.0.0-SNAPSHOT
