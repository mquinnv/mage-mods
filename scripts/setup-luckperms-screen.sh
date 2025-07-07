#!/bin/bash

# LuckPerms setup via screen session
# This script sends commands to your minecraft server running in a screen session

SCREEN_SESSION="minecraft"  # Change this to your screen session name

echo "Setting up LuckPerms via screen session: $SCREEN_SESSION"

# Function to send command to screen
send_command() {
    screen -S "$SCREEN_SESSION" -p 0 -X stuff "$1$(printf \\r)"
    echo "Sent: $1"
    sleep 0.5  # Small delay between commands
}

# Check if screen session exists
if ! screen -list | grep -q "$SCREEN_SESSION"; then
    echo "Error: Screen session '$SCREEN_SESSION' not found!"
    echo "Make sure your Minecraft server is running in a screen session named '$SCREEN_SESSION'"
    echo "Or change the SCREEN_SESSION variable in this script"
    exit 1
fi

echo "Found screen session. Starting LuckPerms setup..."

# Create groups
echo "Creating groups..."
send_command "lp creategroup guest"
send_command "lp creategroup member"
send_command "lp creategroup vip"
send_command "lp creategroup moderator"
send_command "lp creategroup admin"

# Set inheritance
echo "Setting up inheritance..."
send_command "lp group member parent add guest"
send_command "lp group vip parent add member"
send_command "lp group moderator parent add vip"
send_command "lp group admin parent add moderator"

# Set prefixes
echo "Setting prefixes..."
send_command "lp group guest meta setprefix \"&7[Guest] \""
send_command "lp group member meta setprefix \"&a[Member] \""
send_command "lp group vip meta setprefix \"&6[VIP] \""
send_command "lp group moderator meta setprefix \"&c[Mod] \""
send_command "lp group admin meta setprefix \"&4[Admin] \""

# Basic permissions
echo "Setting basic permissions..."
send_command "lp group guest permission set minecraft.command.help true"
send_command "lp group guest permission set minecraft.command.list true"
send_command "lp group guest permission set minecraft.command.me true"

# VIP permissions (for kids)
echo "Setting VIP permissions..."
send_command "lp group vip permission set minecraft.command.gamemode true"
send_command "lp group vip permission set minecraft.command.give true"
send_command "lp group vip permission set minecraft.command.tp true"
send_command "lp group vip permission set minecraft.command.summon true"
send_command "lp group vip permission set minecraft.command.fill true"

# Moderator permissions
echo "Setting moderator permissions..."
send_command "lp group moderator permission set minecraft.command.ban true"
send_command "lp group moderator permission set minecraft.command.kick true"
send_command "lp group moderator permission set minecraft.command.whitelist true"

# Admin permissions
echo "Setting admin permissions..."
send_command "lp group admin permission set \"*\" true"

# Set default group
echo "Setting default group..."
send_command "lp group default parent add guest"

echo ""
echo "✅ LuckPerms setup complete!"
echo ""
echo "To add users to groups, run these commands in your server console:"
echo "  lp user <username> parent add <group>"
echo ""
echo "Examples:"
echo "  lp user kidname1 parent add vip"
echo "  lp user yourusername parent add admin"
echo ""
echo "You can also run this script to add users:"
echo "  screen -S $SCREEN_SESSION -p 0 -X stuff \"lp user USERNAME parent add GROUP\$(printf \\\\r)\""