# LuckPerms Automation Guide

## Option 1: RCON Script (Recommended)
**Best for:** Servers with RCON enabled

1. **Enable RCON in your server.properties:**
   ```properties
   enable-rcon=true
   rcon.port=25575
   rcon.password=your_secure_password
   ```

2. **Edit the RCON password in the script:**
   ```bash
   nano scripts/setup-luckperms-rcon.js
   # Change: const RCON_PASSWORD = 'your_rcon_password';
   ```

3. **Run the script:**
   ```bash
   cd /Users/michael/Projects/minecraft.mage.net
   node scripts/setup-luckperms-rcon.js
   ```

## Option 2: Screen Session Script
**Best for:** Servers running in screen/tmux

1. **Make sure your server runs in a screen session named 'minecraft':**
   ```bash
   screen -S minecraft
   # Start your minecraft server here
   ```

2. **Run the automation script:**
   ```bash
   cd /Users/michael/Projects/minecraft.mage.net
   ./scripts/setup-luckperms-screen.sh
   ```

## Option 3: Manual (Last Resort)
Use the simplified commands from `scripts/luckperms-quick-setup.txt`

## What Gets Set Up

### Groups Created:
- **Guest** (default) - Basic chat only
- **Member** - Trusted players  
- **VIP** - Kids & close friends (creative powers)
- **Moderator** - Server helpers
- **Admin** - Full control

### Key VIP Permissions:
- Creative/Survival/Spectator gamemode
- /give items
- /tp teleport
- /summon mobs
- /fill blocks
- /effect potions

## Adding Users After Setup

```bash
# Add kids to VIP
lp user kidname1 parent add vip
lp user kidname2 parent add vip

# Add friends to Member
lp user friendname parent add member

# Make yourself admin
lp user yourusername parent add admin
```

## Troubleshooting

### RCON Issues:
- Server not responding: Check if RCON is enabled
- Authentication failed: Verify RCON password
- Port issues: Make sure port 25575 is open

### Screen Issues:
- Session not found: Check screen session name with `screen -list`
- Permission denied: Make sure script is executable with `chmod +x`

The RCON method is fastest - takes about 30 seconds total!