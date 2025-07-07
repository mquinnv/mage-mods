# LuckPerms Permission Groups Guide

## Group Structure

The permission system has 5 groups with inheritance (higher groups get permissions from lower groups):

- **Guest** (default) → **Member** → **VIP** → **Moderator** → **Admin**

## Group Descriptions

### 🟢 Guest (Default)
- **Purpose**: New players, temporary access
- **Permissions**: Basic chat, help commands, server info
- **Prefix**: `[Guest]` (gray)
- **Can do**: Chat, use /help, /list, /tps, private messages

### 🟡 Member (Trusted Players)
- **Purpose**: Regular players you trust
- **Permissions**: Basic exploration commands, world info
- **Prefix**: `[Member]` (green)
- **Can do**: Everything guests can + check seed, time, weather, locate biomes
- **Cannot do**: Give items, change gamemode, teleport

### 🟠 VIP (Kids & Close Friends)
- **Purpose**: Your kids and close friends who should have creative freedom
- **Permissions**: Creative building, teleportation, item spawning
- **Prefix**: `[VIP]` (gold)
- **Can do**: Everything members can + creative mode, /give, /tp, /fill, /summon, effects
- **Cannot do**: Ban players, change server settings, stop server

### 🔴 Moderator (Server Helpers)
- **Purpose**: Trusted players who help moderate the server
- **Permissions**: Player management, server maintenance
- **Prefix**: `[Mod]` (red)
- **Can do**: Everything VIP can + ban/kick players, manage whitelist, world borders
- **Cannot do**: Stop/reload server, manage operators

### 🟣 Admin (Full Access)
- **Purpose**: Server owners and co-administrators
- **Permissions**: Full server control
- **Prefix**: `[Admin]` (dark red)
- **Can do**: Everything - complete server control

## Setup Instructions

1. **Run the setup script**: Copy commands from `scripts/setup-luckperms-groups.sh` into your server console
2. **Add users to groups**:
   ```
   lp user <username> parent add <group>
   ```

## Common Commands

```bash
# Add users to groups
lp user kidname1 parent add vip
lp user friendname parent add member
lp user helpername parent add moderator
lp user yourusername parent add admin

# Check user permissions
lp user <username> info

# List all groups
lp listgroups

# Check group permissions
lp group <groupname> info

# Remove user from group
lp user <username> parent remove <group>

# Temporary permissions (1 hour example)
lp user <username> permission set <permission> true 1h
```

## Permission Philosophy

- **Least Privilege**: Users get minimum permissions needed for their role
- **Inheritance**: Higher groups automatically get lower group permissions
- **Safety First**: Dangerous commands (stop, reload, op) restricted to admins only
- **Family Friendly**: VIP group perfect for kids who want creative freedom without server control

## Troubleshooting

- **New players not getting permissions**: Check if default group is set correctly
- **Commands not working**: Verify LuckPerms is loaded with `/lp info`
- **Permission conflicts**: Use `/lp user <username> permission check <permission>` to debug