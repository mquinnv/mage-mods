#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

// Load pack info
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));

// Function to copy shared config files
function copySharedConfigs(destDir) {
  const sharedConfigDir = 'src/shared/config';
  if (fs.existsSync(sharedConfigDir)) {
    const configDir = path.join(destDir, 'config');
    fs.mkdirSync(configDir, { recursive: true });
    
    const configFiles = fs.readdirSync(sharedConfigDir);
    configFiles.forEach(file => {
      const srcPath = path.join(sharedConfigDir, file);
      const destPath = path.join(configDir, file);
      const stats = fs.statSync(srcPath);
      
      if (stats.isDirectory()) {
        // Copy directory recursively
        fs.mkdirSync(destPath, { recursive: true });
        const files = fs.readdirSync(srcPath);
        files.forEach(subFile => {
          const subSrcPath = path.join(srcPath, subFile);
          const subDestPath = path.join(destPath, subFile);
          fs.copyFileSync(subSrcPath, subDestPath);
        });
        console.log(`  ✓ Copied config directory: ${file}`);
      } else {
        // Copy file
        fs.copyFileSync(srcPath, destPath);
        console.log(`  ✓ Copied config: ${file}`);
      }
    });
  }
}

// Default server.properties for optimized Fabric server
const serverProperties = {
  // Basic server settings
  'server-name': 'Minecraft Mage Server',
  'server-port': 25565,
  'gamemode': 'survival',
  'difficulty': 'normal',
  'max-players': 20,
  'motd': `${packInfo.name} v${packInfo.version} - Fabric ${packInfo.minecraft}`,
  
  // World settings
  'level-name': 'world',
  'level-type': 'minecraft:normal',
  'generate-structures': true,
  'spawn-protection': 16,
  'allow-nether': true,
  'spawn-npcs': true,
  'spawn-animals': true,
  'spawn-monsters': true,
  
  // Performance optimizations for Apex Hosting
  'view-distance': 10,
  'simulation-distance': 10,
  'entity-broadcast-range-percentage': 100,
  'network-compression-threshold': 256,
  
  // Security and admin settings
  'online-mode': true,
  'enable-whitelist': false,
  'enforce-whitelist': false,
  'pvp': true,
  'enable-command-block': true,
  'op-permission-level': 4,
  'enable-rcon': false,
  
  // Resource pack and data pack settings
  'resource-pack': '',
  'resource-pack-sha1': '',
  'require-resource-pack': false,
  
  // Performance settings for Apex Hosting
  'sync-chunk-writes': true,
  'use-native-transport': true,
  'enable-jmx-monitoring': false,
  'enable-status': true,
  'max-tick-time': 60000,
  
  // Additional optimization
  'hide-online-players': false,
  'max-world-size': 29999984
};

// Fabric server startup script for Apex Hosting
const startupScript = `#!/bin/bash
# Minecraft Mage Server Startup Script for Apex Hosting
# Fabric ${packInfo.minecraft} with Fabric Loader ${packInfo.fabric}

# JVM Arguments optimized for Fabric and performance mods
JVM_ARGS="-Xms2G -Xmx4G \\
  -XX:+UseG1GC \\
  -XX:+ParallelRefProcEnabled \\
  -XX:MaxGCPauseMillis=200 \\
  -XX:+UnlockExperimentalVMOptions \\
  -XX:+DisableExplicitGC \\
  -XX:+AlwaysPreTouch \\
  -XX:G1NewSizePercent=30 \\
  -XX:G1MaxNewSizePercent=40 \\
  -XX:G1HeapRegionSize=8M \\
  -XX:G1ReservePercent=20 \\
  -XX:G1HeapWastePercent=5 \\
  -XX:G1MixedGCCountTarget=4 \\
  -XX:InitiatingHeapOccupancyPercent=15 \\
  -XX:G1MixedGCLiveThresholdPercent=90 \\
  -XX:G1RSetUpdatingPauseTimePercent=5 \\
  -XX:SurvivorRatio=32 \\
  -XX:+PerfDisableSharedMem \\
  -XX:MaxTenuringThreshold=1"

# Additional flags for better performance with Fabric mods
FABRIC_ARGS="-Dfabric.systemLibraries \\
  -Dfabric.skipMcProvider=true \\
  -Djava.awt.headless=true"

# Start the server
echo "Starting ${packInfo.name} v${packInfo.version}"
echo "Minecraft ${packInfo.minecraft} with Fabric ${packInfo.fabric}"
echo "Optimized for Apex Hosting"

java $JVM_ARGS $FABRIC_ARGS -jar fabric-server-mc.${packInfo.minecraft}-loader.${packInfo.fabric}.jar nogui
`;

// Generate mod configuration hints for Apex Hosting
const modConfigNotes = `# Minecraft Mage Server Configuration Notes

## Recommended Apex Hosting Plan
- **Minimum**: 4GB RAM plan
- **Recommended**: 6GB+ RAM plan for 10+ players
- **CPU**: High frequency preferred for single-threaded performance

## Pre-installed Performance Mods
- **Lithium**: Server-side optimization
- **Starlight**: Lighting engine optimization  
- **FerriteCore**: Memory usage optimization
- **Krypton**: Network stack optimization
- **ServerCore**: Various server optimizations

## Administrative Mods Included
- **LuckPerms**: Advanced permission management
  - Web editor: https://editor.luckperms.net/
  - Config: /plugins/LuckPerms/
- **Essential Commands**: Teleportation, homes, warps
  - Config: /config/essential_commands.json
- **Spark**: Performance profiler
  - Command: /spark profiler start
  - Web viewer: https://spark.lucko.me/

## Monitoring and Debugging
- **BlueMap**: Live web map at http://your-server:8100
  - Config: /config/bluemap/
- **Carpet**: Debug and optimization features
  - /carpet list - Show available rules
  - /carpet setDefault optimizedTNT true
- **Carpet Extra**: Additional carpet features

## AutoSwitch Configuration
- Both client and server have AutoSwitch for consistent tool switching
- Config location: /config/autoswitch.json

## Recommended server.properties tweaks for Apex Hosting:
view-distance=10          # Balance performance vs render distance
simulation-distance=10    # Keep mob/redstone simulation reasonable  
network-compression-threshold=256  # Good for most connections
max-players=20           # Adjust based on your RAM plan
`;

function generateServerProperties() {
  const buildDir = 'build/server-config';
  fs.mkdirSync(buildDir, { recursive: true });
  
  // Generate server.properties
  let propertiesContent = '# server.properties\n';
  propertiesContent += `# Generated for ${packInfo.name} v${packInfo.version}\n`;
  propertiesContent += `# Minecraft ${packInfo.minecraft} with Fabric ${packInfo.fabric}\n\n`;
  
  for (const [key, value] of Object.entries(serverProperties)) {
    propertiesContent += `${key}=${value}\n`;
  }
  
  fs.writeFileSync(path.join(buildDir, 'server.properties'), propertiesContent);
  
  // Generate startup script
  fs.writeFileSync(path.join(buildDir, 'start-server.sh'), startupScript);
  fs.chmodSync(path.join(buildDir, 'start-server.sh'), '755');
  
  // Generate configuration notes
  fs.writeFileSync(path.join(buildDir, 'APEX_HOSTING_SETUP.md'), modConfigNotes);
  
  // Copy shared config files
  copySharedConfigs(buildDir);
  
  console.log('✅ Server configuration files generated:');
  console.log('   - server.properties (optimized for Apex Hosting)');
  console.log('   - start-server.sh (JVM-optimized startup script)');
  console.log('   - APEX_HOSTING_SETUP.md (configuration guide)');
  console.log('   - Shared mod config files copied');
}

if (require.main === module) {
  generateServerProperties();
}

module.exports = { generateServerProperties };