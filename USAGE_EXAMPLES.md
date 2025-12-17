# PaperMCP Usage Examples

Real-world examples of what you can do with AI connected to your Minecraft server.

## Table of Contents

- [Monitoring & Diagnostics](#monitoring--diagnostics)
- [Player Management](#player-management)
- [Server Administration](#server-administration)
- [Automation & Workflows](#automation--workflows)
- [Interactive Chat](#interactive-chat)
- [Advanced Use Cases](#advanced-use-cases)

---

## Monitoring & Diagnostics

### Check Server Health

```
You: "How's my Minecraft server doing?"

Claude: I'll check the server status for you.
[Uses minecraft_server_info]

Your server is performing well:
- TPS: 20.00 / 19.98 / 19.95 (1/5/15 min averages)
- Players: 12/50 online
- Uptime: 2 days, 5 hours
- Version: Paper 1.21.4
- View Distance: 10 chunks

Everything looks healthy! TPS is solid at 20.
```

### Investigate Performance Issues

```
You: "Players are complaining about lag. Can you diagnose the issue?"

Claude: Let me check the server health and recent logs.
[Uses minecraft_server_info]
[Uses minecraft_read_console with limit: 200]

I found the issue:

1. TPS has dropped to 15.2 (should be 20)
2. Console shows: "[WARN] Can't keep up! Did the system time change?"
3. Multiple chunk generation warnings in the logs

Recommendations:
- Players are exploring new areas, causing chunk generation
- Consider pre-generating chunks with Chunky plugin
- Current entity count is normal
- No plugin errors detected

Would you like me to send a message to players about the temporary lag?
```

### Monitor Specific Player

```
You: "Is Steve still online? Where is he and what's he doing?"

Claude: Let me check on Steve.
[Uses minecraft_list_players with player: "Steve"]

Yes, Steve is online:
- Location: world_nether (x: 245, y: 78, z: -892)
- Health: 14.0/20.0 HP (took damage recently)
- Game Mode: Survival
- Level: 42
- Ping: 45ms (good connection)

He's in the Nether and has taken some damage. Should I check recent chat to see if he needs help?
```

---

## Player Management

### Greet New Players

```
You: "Watch for new players joining and send them a welcome message with server rules"

Claude: I'll monitor for new players and welcome them.
[Uses minecraft_read_console]
[Detects: "[JOIN] Bob joined the server"]
[Uses minecraft_send_message]

Done! I sent Bob a welcome message:

"Welcome to the server, Bob! 🎮
- Read /rules for server guidelines
- Ask questions in chat anytime
- Type /spawn to get started
Have fun!"
```

### Check Player Activity

```
You: "Who are the most active players today based on chat?"

Claude: Let me analyze today's chat activity.
[Uses minecraft_read_chat with limit: 500]

Most active players (by messages sent):
1. Steve - 47 messages
2. Alex - 32 messages
3. Bob - 28 messages
4. Emma - 19 messages
5. Dave - 12 messages

Steve has been very chatty today! Mostly discussing their mega build project.
```

### Respond to Player Questions

```
You: "Check if anyone in chat is asking questions and try to help them"

Claude: Let me check recent chat.
[Uses minecraft_read_chat with limit: 50]

I found several questions:

1. Steve asked: "How do I claim land?"
   [Uses minecraft_send_message to Steve]
   Sent: "Hi Steve! Use /claim while holding a golden shovel. Type /help claim for more info."

2. Alex asked: "What's the server IP?"
   [Uses minecraft_send_message to Alex]
   Sent: "The server IP is: play.yourserver.com"

3. Bob asked: "Can someone TP me to spawn?"
   [Uses minecraft_execute_command with "tp Bob spawn"]
   Teleported Bob to spawn and sent confirmation.

All questions answered!
```

---

## Server Administration

### Scheduled Maintenance Announcements

```
You: "Announce to all players that the server will restart in 10 minutes for maintenance"

Claude: I'll send the announcement.
[Uses minecraft_send_message]

Announcement sent to all 12 online players:
"⚠️ Server Restart in 10 minutes!
The server will restart for scheduled maintenance.
- Save your progress
- Return to safe locations
- Restart will take ~5 minutes"

Would you like me to send reminder announcements at 5 minutes and 1 minute?
```

### Weather and Time Control

```
You: "It's night time and raining. Set it to day and clear weather, then announce the change"

Claude: I'll fix the weather and time.
[Uses minecraft_execute_command with "time set day"]
[Uses minecraft_execute_command with "weather clear"]
[Uses minecraft_send_message]

Done!
✅ Time set to day
✅ Weather cleared
✅ Announced to all players: "Time set to day and weather cleared for better visibility!"
```

### Teleport Players

```
You: "Steve and Alex want to meet up. Teleport Alex to Steve's location"

Claude: I'll teleport Alex to Steve.
[Uses minecraft_list_players to get Steve's location]
[Uses minecraft_execute_command with "tp Alex Steve"]
[Uses minecraft_send_message]

Done!
- Teleported Alex to Steve at world (123, 64, -456)
- Sent message to both: "Alex has been teleported to Steve!"
```

### Game Mode Management

```
You: "Bob is stuck in a wall. Set him to spectator mode temporarily so he can get out"

Claude: I'll help Bob get unstuck.
[Uses minecraft_execute_command with "gamemode spectator Bob"]
[Uses minecraft_send_message to Bob]

Done!
- Set Bob to Spectator mode
- Sent message: "You're in spectator mode to get unstuck. Let me know when you're in a safe spot and I'll set you back to Survival."

When Bob confirms he's safe, just ask and I'll switch him back!
```

---

## Automation & Workflows

### Automated Lag Monitoring

```
You: "Monitor TPS continuously. If it drops below 18, investigate logs and alert me with a summary"

Claude: I'll set up TPS monitoring.
[Checks every few minutes via minecraft_server_info]

*15 minutes later...*

🚨 LAG ALERT! TPS dropped to 16.8

Investigation:
[Used minecraft_read_console]

Findings:
- Large redstone contraption running at world (234, 64, 567)
- 1,200 entities in that chunk (extremely high)
- Console shows: "Redstone tick took 125ms"

Recommendation: Check the redstone build in that area. May need to limit or optimize it.

Players affected: Steve and Alex (in that area)

Should I message them about the lag source?
```

### Event Management

```
You: "We're starting a PvP event at 8 PM. At that time: teleport all online players to the arena, set them to adventure mode, and announce the event start"

Claude: I'll help manage the event. Please let me know when it's time (or tell me to wait until 8 PM and execute automatically).

*At 8 PM...*

Starting PvP Event!
[Uses minecraft_list_players to get all players]
[Uses minecraft_execute_command for each player: "tp PlayerName arena"]
[Uses minecraft_execute_command for each player: "gamemode adventure PlayerName"]
[Uses minecraft_send_message with broadcast]

✅ Event Started!
- Teleported 12 players to arena
- All players set to Adventure mode
- Announced: "🎮 PVP EVENT STARTED! Fight to win! Last player standing wins!"

Good luck with the event! Let me know when it's over and I can restore everyone's game modes.
```

### Backup Reminders

```
You: "Check uptime. If it's been more than 24 hours since last restart, remind me to do a backup"

Claude: Checking server uptime...
[Uses minecraft_server_info]

⚠️ Backup Reminder!
- Server uptime: 2 days, 14 hours
- Last restart: Over 24 hours ago

Recommendation: Schedule a maintenance window for:
1. Backup world files
2. Server restart
3. Plugin updates if needed

Current player count: 3 (good time for maintenance)

Would you like me to announce a restart in X minutes?
```

---

## Interactive Chat

### Chat Moderation

```
You: "Monitor chat for inappropriate language or spam. Warn players if needed"

Claude: I'll monitor the chat.
[Uses minecraft_read_chat periodically]

*Detects spam...*

⚠️ Moderation Alert:
Player "Bob" sent 10 messages in 30 seconds (possible spam)

Messages: "free diamonds!", "at spawn!", "hurry!" (repeated)

Actions taken:
[Uses minecraft_send_message to Bob]
- Sent warning: "Please avoid spamming chat. Use one message instead of many short ones."

Logged incident for admin review.
```

### FAQ Bot

```
You: "Act as a helpful FAQ bot. Answer common player questions about server rules, commands, and features"

Claude: I'll help players with their questions!
[Uses minecraft_read_chat to monitor questions]

*Detects question...*

Alex asked: "What's the best way to protect my build?"

[Uses minecraft_send_message to Alex]
Responded: "Hi Alex! You can protect your build by:
1. Using /claim with a golden shovel to claim land
2. Type /trust <player> to allow friends to build
3. Type /help claim for more options
Need help claiming? Let me know!"

*Another question...*

Steve asked: "Where can I find diamonds?"

[Uses minecraft_send_message to Steve]
Responded: "Diamonds spawn at Y-levels -64 to 16, with most found around Y=-59. Try branch mining or cave exploring! Good luck! ⛏️"
```

### Community Engagement

```
You: "Analyze chat sentiment over the last hour. Are players happy, frustrated, or having issues?"

Claude: Let me analyze recent chat.
[Uses minecraft_read_chat with limit: 200]

Chat Sentiment Analysis (last hour):

😊 Positive (65%):
- Players excited about new builds
- Compliments on server performance
- Collaborative projects discussed

😐 Neutral (25%):
- General conversation
- Questions about gameplay
- Trade discussions

😟 Negative (10%):
- 2 players mentioned lag during chunk generation
- 1 player couldn't find their items after death

Overall: Community mood is very positive!

Actionable items:
1. Address the lag issue (chunk generation)
2. Help player find lost items (check death location)

Would you like me to help with either of these?
```

---

## Advanced Use Cases

### Multi-Server Coordination

```
You: "I have a survival and creative server. Check TPS on both. If survival TPS is good, announce creative players can join survival for an event"

Claude: Checking both servers...
[Uses minecraft_server_info on survival-server]
[Uses minecraft_server_info on creative-server]

Results:
✅ Survival Server: TPS 19.8, 5 players online
✅ Creative Server: TPS 20.0, 8 players online

Survival is performing well!

[Uses minecraft_send_message on creative-server]
Announced to creative players:
"📢 Join us on the Survival server for a building competition event! /server survival"

Cross-promotion sent! 8 creative players notified.
```

### Data-Driven Insights

```
You: "Analyze the last 1000 chat messages and tell me what players are most interested in building or doing"

Claude: Analyzing player interests...
[Uses minecraft_read_chat with limit: 500]
[Multiple calls to get full dataset]

Player Interest Analysis (1000 messages):

Top Topics:
1. **Redstone Projects (28%)** - Steve, Alex, Dave
   - Automated farms most discussed
   - Sorting systems popular
   - Door mechanisms

2. **Building Projects (24%)** - Bob, Emma
   - Medieval castle (Emma's project)
   - Modern city (collaborative)
   - Underwater base (Bob planning)

3. **Mining & Resources (18%)**
   - Diamond hunting
   - Ancient city raiding
   - Netherite farming

4. **PvP Interest (15%)**
   - Requests for PvP events
   - Combat discussions

5. **Trading (15%)**
   - Shop establishment
   - Item exchanges

Recommendations:
- Host redstone tutorial session (high interest)
- Organize building competition (castle theme?)
- Schedule weekly PvP events
- Create trading district at spawn
```

### Smart Resource Management

```
You: "Check if anyone is asking for items in chat. If they are, see if another player mentioned having those items recently, and connect them"

Claude: I'll help facilitate trades!
[Uses minecraft_read_chat]

Trade Match Found!

Bob said 10 minutes ago: "Looking for dark oak wood, anyone selling?"
Steve said 5 minutes ago: "I have tons of dark oak if anyone needs"

[Uses minecraft_send_message to Bob]
"Hey Bob! Steve mentioned he has dark oak wood. Check with him!"

[Uses minecraft_send_message to Steve]
"Bob is looking for dark oak wood. You mentioned you have some!"

Trade connection made! 🤝
```

### Automated Server Reports

```
You: "Generate a daily server summary: uptime, player activity, notable events, and performance"

Claude: Generating daily server report...
[Uses minecraft_server_info]
[Uses minecraft_list_players]
[Uses minecraft_read_chat with limit: 500]
[Uses minecraft_read_console with limit: 500]

📊 DAILY SERVER REPORT
Date: December 16, 2025

**Performance:**
- Uptime: 23 hours, 45 minutes
- Average TPS: 19.7 (excellent)
- Peak Players: 15/50
- Current Players: 8

**Player Activity:**
- Total unique logins: 23 players
- Most active: Steve (6 hours), Alex (5 hours)
- New players: 2 (Bob, Emma)

**Notable Events:**
- Build competition announced
- Nether fortress raided (Steve, Alex, Dave)
- Community farm completed
- 3 player deaths (PvP at arena)

**Chat Highlights:**
- 456 messages sent
- Most discussed: New build projects (32%)
- Positive sentiment: 78%

**Issues:**
- Brief lag spike at 14:30 (chunk generation)
- 1 player reported missing items (resolved)

**Recommendations:**
- Continue current performance
- Consider expanding server slots (consistently 12+ players)
- Schedule next PvP event (high interest)

Server is healthy! 🟢
```

---

## Prompt Templates

Here are some useful prompts you can use:

### Monitoring Templates

```
"Check server health and notify me if anything is wrong"

"Monitor TPS every 10 minutes and alert if it drops below 18"

"Watch for players joining/leaving and keep me updated"

"Tell me if any errors appear in the console"
```

### Player Management Templates

```
"Welcome new players and give them a quick tour guide message"

"If anyone asks a question in chat, try to answer it"

"Check who's been online longest and congratulate them"

"Find inactive players (not in chat for 30+ minutes) and check on them"
```

### Administration Templates

```
"Announce [message] to all players with a warning prefix"

"Set time to [time] and weather to [clear/rain/thunder]"

"Teleport [player1] to [player2] and notify them"

"Check if anyone is in the end/nether and bring them back to overworld"
```

### Automation Templates

```
"Every hour, check TPS and report if below 19"

"If players mention lag in chat, investigate and respond"

"Watch for players asking for help and provide assistance"

"Monitor chat for trade requests and match buyers with sellers"
```

---

## Tips for Effective Usage

### 1. Be Specific
❌ "Check the server"
✅ "Check server TPS, player count, and any recent errors in the logs"

### 2. Use Natural Language
You don't need to know exact commands:
- "Make it daytime" instead of "execute time set day command"
- "Who's online?" instead of "list players tool"

### 3. Chain Actions
"Check TPS. If below 19, read the last 100 console lines and summarize any issues"

### 4. Ask for Analysis
"What are players talking about most in chat today?"
"Summarize server activity over the last hour"

### 5. Delegate Routine Tasks
"Monitor chat for questions and help players while I'm AFK"
"Watch for new players and welcome them automatically"

### 6. Use Context
Claude remembers the conversation:
```
You: "Who's online?"
Claude: Steve, Alex, and Bob are online
You: "Teleport them all to spawn"
Claude: [teleports the 3 players mentioned]
```

---

## Safety Reminders

1. **Test First**: Try commands manually before automating
2. **Monitor Initially**: Watch what AI does until you trust the setup
3. **Use Whitelist**: Restrict which commands AI can execute
4. **Review Logs**: Check audit logs regularly
5. **Player Privacy**: Don't share player IPs or personal info
6. **Backup Often**: Always backup before giving write access

---

## Getting Creative

The real power is combining tools creatively:

```
You: "Create a treasure hunt! Hide a chest at random coords, then give players cryptic clues through broadcast messages every 5 minutes"

You: "Monitor player deaths. If someone dies more than 3 times in 10 minutes, offer to teleport them to safety"

You: "Analyze building patterns - which biomes are players building in most? Suggest unexplored biomes for new builds"

You: "Create a 'server newspaper' - summarize the day's events in a fun, dramatic way"
```

Your creativity is the limit! 🚀
