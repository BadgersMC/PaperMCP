# PaperMCP Setup Guide

Complete walkthrough for setting up PaperMCP to connect Claude to your remote Minecraft server.

## Overview

PaperMCP consists of two parts:
1. **Plugin** - Runs on your Minecraft server (datacenter)
2. **Client** - Runs on your PC, connects Claude to the server

## Prerequisites

- **Server Side:** Java 21, Paper/Spigot 1.21+
- **Client Side:** Java 17+, Claude Desktop
- **Network:** Ability to open a port on your server

---

## Part 1: Server Setup (Plugin)

### Step 1: Build the Plugin

```bash
# Build the plugin (requires Java 21)
./gradlew :plugin:build

# The JAR will be at:
# plugin/build/libs/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar
```

### Step 2: Upload to Server

```bash
# Upload via SCP
scp plugin/build/libs/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar user@yourserver.com:/path/to/minecraft/plugins/

# Or use SFTP/FTP client of your choice
```

### Step 3: First Start (Generate Config)

Start your Minecraft server to generate the default configuration:

```bash
# On your server
cd /path/to/minecraft
./start.sh  # or however you start your server
```

The plugin will create `plugins/PaperMCP/config.yml` and then disable itself because the default token hasn't been changed.

### Step 4: Configure the Plugin

Edit `plugins/PaperMCP/config.yml`:

```bash
nano plugins/PaperMCP/config.yml
```

**Minimal configuration:**

```yaml
server:
  host: "0.0.0.0"  # Listen on all interfaces
  port: 25577      # WebSocket port (choose any available port)
  authentication_token: "your-super-secret-random-token-here"  # CHANGE THIS!

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

**Generate a secure token:**

```bash
# On Linux/Mac
openssl rand -base64 32

# Or use any password generator
# Example: "K7mP9nQ2rT5vW8xA1bC4dE6fG9hJ2kL5"
```

**Save the token** - you'll need it for the client configuration!

### Step 5: Open Firewall Port

```bash
# UFW (Ubuntu/Debian)
sudo ufw allow 25577/tcp
sudo ufw status

# firewalld (CentOS/RHEL)
sudo firewall-cmd --permanent --add-port=25577/tcp
sudo firewall-cmd --reload

# Check if port is open
sudo ss -tulpn | grep 25577
```

### Step 6: Restart Server

```bash
# Restart your Minecraft server
./restart.sh  # or stop and start
```

**Verify plugin loaded:**

```
[Server] INFO [PaperMCP] Enabling PaperMCP v1.0.0-SNAPSHOT...
[Server] INFO [PaperMCP] Minecraft bridge initialized with event listeners
[Server] INFO [PaperMCP] WebSocket server started successfully
[Server] INFO [PaperMCP] Listening on 0.0.0.0:25577
[Server] INFO [PaperMCP] PaperMCP enabled successfully!
```

### Step 7: Test WebSocket Server

From your PC, test the connection:

```bash
# Using telnet
telnet yourserver.com 25577

# Using netcat
nc -zv yourserver.com 25577

# Should show: Connection succeeded
```

---

## Part 2: Client Setup (Your PC)

### Step 1: Build the Client

```bash
# Build the client (requires Java 17+)
./gradlew :client:build

# The JAR will be at:
# client/build/libs/PaperMCP-Client-1.0.0-SNAPSHOT.jar
```

### Step 2: Create Directory Structure

```bash
# Create a dedicated folder
mkdir C:\PaperMCP
cd C:\PaperMCP

# Copy the client JAR from your build location
copy path\to\PaperMCP\client\build\libs\PaperMCP-Client-1.0.0-SNAPSHOT.jar .
```

### Step 3: Create Client Configuration

Create `C:\PaperMCP\config.json`:

```json
{
  "host": "yourserver.com",
  "port": 25577,
  "token": "K7mP9nQ2rT5vW8xA1bC4dE6fG9hJ2kL5",
  "use_ssl": false,
  "reconnect": {
    "enabled": true,
    "max_attempts": 10,
    "delay_ms": 5000
  }
}
```

**Important:**
- Use the **same token** from your server's `config.yml`
- Use your actual server hostname or IP
- Set `use_ssl: true` if you've set up SSL (see Advanced Setup)

### Step 4: Test the Client Manually

Before connecting Claude, test the client:

```bash
cd C:\PaperMCP
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json
```

**Expected output:**

```
12:34:56.789 [main] INFO  PaperMCPClient - PaperMCP Client starting...
12:34:56.890 [main] INFO  PaperMCPClient - Loaded configuration from config.json
12:34:56.891 [main] INFO  PaperMCPClient - Connecting to yourserver.com:25577...
12:34:57.123 [main] INFO  WebSocketClient - Connecting to ws://yourserver.com:25577/...
12:34:57.456 [main] INFO  WebSocketClient - WebSocket connected
12:34:57.457 [main] INFO  WebSocketClient - Authenticating...
12:34:57.567 [main] INFO  WebSocketClient - Authentication successful
12:34:57.568 [main] INFO  PaperMCPClient - Connected to Minecraft server
12:34:57.569 [main] INFO  MCPProtocolHandler - Starting MCP protocol handler on stdio...
12:34:57.570 [main] INFO  MCPProtocolHandler - MCP protocol handler started
12:34:57.571 [main] INFO  MCPProtocolHandler - Listening for MCP messages on stdin...
```

Press `Ctrl+C` to stop (it's waiting for MCP messages on stdin).

### Step 5: Configure Claude Desktop

**Find your Claude config file:**

- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Linux:** `~/.config/Claude/claude_desktop_config.json`

**Edit the file:**

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

**On macOS/Linux:**

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": [
        "-jar",
        "/home/youruser/papermcp/PaperMCP-Client-1.0.0-SNAPSHOT.jar",
        "/home/youruser/papermcp/config.json"
      ]
    }
  }
}
```

**Important:**
- Use **full absolute paths**, not relative paths
- On Windows, use double backslashes `\\` in paths
- Save the file

### Step 6: Restart Claude Desktop

1. **Quit Claude Desktop completely** (not just close the window)
   - Windows: Right-click system tray icon → Exit
   - macOS: Cmd+Q
   - Linux: Quit from system tray

2. **Start Claude Desktop again**

3. **Verify MCP tools loaded:**
   - Look for the hammer icon 🔨 in Claude Desktop
   - Click it to see available tools
   - You should see 6 tools starting with `minecraft_`

---

## Part 3: Using PaperMCP

### Basic Usage

Once set up, you can ask Claude about your server:

**Server Status:**
```
You: "What's the current TPS on my Minecraft server?"

Claude: I'll check the server stats for you.
[Uses minecraft_server_info]

Your server is performing well:
- TPS: 20.00 (1min), 19.98 (5min), 19.95 (15min)
- Online Players: 5/20
- Version: Paper 1.21.4
- Uptime: 3h 42m
```

**Player Information:**
```
You: "Who's online and where are they?"

Claude: Let me check the online players.
[Uses minecraft_list_players]

Currently online (5 players):
1. Steve - world (x: 123, y: 64, z: -456), Survival, 20.0 HP
2. Alex - world_nether (x: 15, y: 75, z: -57), Creative, 20.0 HP
...
```

**Execute Commands:**
```
You: "Set the time to day and announce it"

Claude: I'll set the time to day and broadcast a message.
[Uses minecraft_execute_command with "time set day"]
[Uses minecraft_send_message with broadcast]

Done! Set time to day and announced to all 5 players.
```

**Monitor Chat:**
```
You: "What have players been talking about in the last hour?"

Claude: Let me check the chat history.
[Uses minecraft_read_chat]

Recent chat (last 50 messages):
- Steve: "Anyone want to go mining?"
- Alex: "Sure, I'll grab some picks"
- Steve: "Meet at spawn"
...
```

### Available Tools

1. **`minecraft_server_info`**
   - Get server health, TPS, player count, uptime
   - No parameters required

2. **`minecraft_list_players`**
   - List all online players with details
   - Optional: `player: "name"` to get specific player

3. **`minecraft_read_console`**
   - Read recent console logs
   - Optional: `limit: 100` (default: 100, max: 1000)

4. **`minecraft_read_chat`**
   - Read player chat history
   - Optional: `limit: 50` (default: 50, max: 500)
   - Optional: `player: "name"` to filter by player

5. **`minecraft_execute_command`**
   - Execute server commands
   - Required: `command: "time set day"`
   - Security: Commands are checked against whitelist/blacklist

6. **`minecraft_send_message`**
   - Send messages to players
   - Required: `message: "Server restarting in 5 minutes"`
   - Optional: `target: "broadcast"` or player name
   - Optional: `prefix: "[Server]"`

### In-Game Commands

As a server operator, you can manage the plugin:

```
/papermcp status
```
Shows WebSocket server status and connected clients.

```
/papermcp clients
```
Lists all connected MCP clients with connection info.

```
/papermcp reload
```
Reloads the configuration file (requires server restart for some changes).

```
/papermcp help
```
Shows help information.

---

## Part 4: Advanced Configuration

### SSL/TLS Setup (Recommended for Production)

For secure connections over the internet, use a reverse proxy with SSL:

**Using Caddy (easiest):**

1. Install Caddy on your server
2. Create `Caddyfile`:

```
mc.yourdomain.com:443 {
    reverse_proxy localhost:25577
}
```

3. Start Caddy: `sudo caddy start`
4. Update client config:

```json
{
  "host": "mc.yourdomain.com",
  "port": 443,
  "token": "your-token",
  "use_ssl": true
}
```

**Using nginx:**

```nginx
server {
    listen 443 ssl http2;
    server_name mc.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:25577;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### IP Whitelisting

Restrict connections to specific IPs:

```yaml
server:
  allowed_ips:
    - "123.456.789.0"        # Your home IP
    - "98.76.54.0/24"        # Your office network
```

### Command Security Tuning

**Read-only mode (safest):**

```yaml
mcp:
  security:
    command_whitelist:
      - "list"
      - "tps"
      - "help"
      - "version"
      - "plugins"

    command_blacklist:
      - "*"  # Block everything except whitelist
```

**Administrative mode (powerful):**

```yaml
mcp:
  security:
    command_whitelist: []  # Allow all non-blacklisted

    command_blacklist:
      - "stop"
      - "restart"
      - "*op*"
      - "ban*"
      - "whitelist"
```

### Multiple Servers

You can connect to multiple servers by running multiple clients:

**Claude Desktop config:**

```json
{
  "mcpServers": {
    "minecraft-survival": {
      "command": "java",
      "args": ["-jar", "C:\\PaperMCP\\client.jar", "C:\\PaperMCP\\survival-config.json"]
    },
    "minecraft-creative": {
      "command": "java",
      "args": ["-jar", "C:\\PaperMCP\\client.jar", "C:\\PaperMCP\\creative-config.json"]
    }
  }
}
```

Claude will see tools prefixed with the server name.

---

## Part 5: Troubleshooting

### Plugin Issues

**Error: "Please set a secure authentication_token"**

Solution: Edit `plugins/PaperMCP/config.yml` and change `CHANGE_ME_TO_A_SECURE_TOKEN` to a random string.

**Error: "Address already in use"**

Solution: Another service is using port 25577. Either:
- Stop the other service
- Change `server.port` in config.yml to a different port

**Plugin not loading**

Check logs at `logs/latest.log` for errors:

```bash
tail -f logs/latest.log | grep PaperMCP
```

Common issues:
- Java version mismatch (need Java 21)
- Missing dependencies (use shadowJar build)
- Permission errors

### Client Issues

**Authentication Failed**

- Verify tokens match exactly (case-sensitive)
- Check server logs for connection attempts
- Ensure WebSocket server is running: `/papermcp status`

**Connection Refused**

```bash
# Test connection
telnet yourserver.com 25577

# Check firewall
ping yourserver.com

# Verify port is open
nmap -p 25577 yourserver.com
```

**Client crashes immediately**

- Check Java version: `java -version` (need 17+)
- Verify config.json syntax (use jsonlint.com)
- Run with debug logging:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json
```

### Claude Desktop Issues

**Tools don't appear**

1. Verify client config path is correct (absolute path)
2. Check Claude logs:
   - Windows: `%APPDATA%\Claude\logs`
   - macOS: `~/Library/Logs/Claude`
3. Restart Claude Desktop completely (not just close window)
4. Test client manually first (see Step 4 in Client Setup)

**"Request timed out"**

- Client may not be connected to server
- Check client is running: Task Manager / Activity Monitor
- Verify server is reachable
- Check server logs for errors

**Tools execute but nothing happens**

- Check server console for command execution
- Verify command is whitelisted in config.yml
- Enable audit logging and check logs
- Test command manually in-game first

---

## Part 6: Monitoring and Maintenance

### View Connected Clients

In-game:
```
/papermcp clients
```

Output:
```
=== Connected Clients (2) ===
- abc123def456: 123.45.67.89 (connected 1h 23m)
- xyz789ghi012: 98.76.54.32 (connected 15m)
```

### Check Logs

**Server-side (plugin):**

```bash
# Live tail
tail -f logs/latest.log | grep PaperMCP

# Search for errors
grep ERROR logs/latest.log | grep PaperMCP

# View audit log (if enabled)
grep "MCP executing command" logs/latest.log
```

**Client-side:**

Client logs to stderr, visible in terminal. Redirect to file:

```bash
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json 2> client.log
```

### Performance Monitoring

The plugin has minimal impact:
- WebSocket server runs on separate thread
- Async I/O for network operations
- Configurable buffer sizes

Monitor with:
```
/papermcp status
/timings report  # Paper's built-in profiler
```

### Security Auditing

Enable audit logging:

```yaml
mcp:
  security:
    audit_logging: true
```

View audit trail:

```bash
grep "MCP executing command" logs/latest.log
grep "MCP sending message" logs/latest.log
```

Example output:
```
[12:34:56 INFO]: [PaperMCP] MCP executing command: time set day
[12:35:12 INFO]: [PaperMCP] MCP sending message to 'broadcast': Server restart in 10 minutes
```

---

## Part 7: Best Practices

### Security

1. **Use strong tokens** (32+ characters, random)
2. **Enable command whitelist** in production
3. **Use SSL/TLS** for remote connections
4. **Enable audit logging** to track AI actions
5. **Regularly rotate tokens** (monthly)
6. **Use IP whitelisting** when possible
7. **Review logs regularly** for suspicious activity

### Operational

1. **Test commands manually first** before adding to whitelist
2. **Backup server before** giving AI write access
3. **Start with read-only tools**, gradually add write access
4. **Monitor TPS impact** (should be negligible)
5. **Keep client running** in background or as service
6. **Document your setup** for team members

### Development

1. **Test in development server** first
2. **Use version control** for config files
3. **Document custom commands** you whitelist
4. **Keep plugin updated** for security patches
5. **Report bugs** at GitHub issues

---

## Quick Reference

### Common Commands

```bash
# Server (in-game)
/papermcp status
/papermcp clients
/papermcp reload

# Server (console)
tail -f logs/latest.log | grep PaperMCP

# Client
java -jar PaperMCP-Client-1.0.0-SNAPSHOT.jar config.json

# Build
./gradlew :plugin:build
./gradlew :client:build
```

### File Locations

```
Server:
  Plugin JAR: /path/to/minecraft/plugins/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar
  Config: /path/to/minecraft/plugins/PaperMCP/config.yml
  Logs: /path/to/minecraft/logs/latest.log

Client:
  JAR: C:\PaperMCP\PaperMCP-Client-1.0.0-SNAPSHOT.jar
  Config: C:\PaperMCP\config.json
  Claude Config: %APPDATA%\Claude\claude_desktop_config.json
```

### Ports

```
Default WebSocket: 25577 (configurable)
Minecraft: 25565 (separate, don't expose if not needed)
```

---

## Getting Help

- **GitHub Issues:** https://github.com/BadgersMC/PaperMCP/issues
- **Pull Requests:** Contributions welcome!

For bug reports, include:
1. Plugin/client version
2. Java version (server and client)
3. Minecraft/Paper version
4. Error logs
5. Configuration (without tokens!)
