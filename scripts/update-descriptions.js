#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');
const fs = require('fs');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-description-updater/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

// Load configurations
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));
const uploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));
const clientMods = JSON.parse(fs.readFileSync('config/mods-client.json', 'utf8'));
const serverMods = JSON.parse(fs.readFileSync('config/mods-server.json', 'utf8'));

// Client pack description
const clientDescription = `# 🧙‍♂️ Minecraft Mage - Client Pack

**A carefully curated client-side modpack designed for optimal performance, stunning visuals, and enhanced gameplay experience on the Minecraft Mage server.**

## ✨ Key Features

### 🚀 Performance Optimization
- **Sodium** - Dramatically improves rendering performance and FPS
- **Iris Shaders** - Beautiful shader support with excellent performance
- **FerriteCore** - Reduces memory usage for smoother gameplay  
- **LazyDFU** - Faster game startup times
- **Memory Leak Fix** - Prevents memory-related crashes
- **Entity Culling** - Improves FPS by culling non-visible entities

### 🎮 Enhanced Gameplay
- **EMI (Easy Mod Integration)** - Advanced recipe viewer and item search
- **Jade** - Detailed block and entity information on hover
- **AutoSwitch** - Automatically switches to the best tool for the job
- **Inventory Profiles Next** - Smart inventory management and sorting
- **AppleSkin** - Shows hunger and saturation information

### 🎨 Visual Improvements  
- **Continuity** - Connected textures and better resource pack support
- **Indium** - Rendering compatibility layer for Sodium
- **Mod Menu** - Clean configuration interface for all mods
- **Cloth Config** - Unified configuration system

## 🔧 Technical Details
- **Minecraft Version:** ${packInfo.minecraft}
- **Fabric Loader:** ${packInfo.fabric}
- **Total Mods:** ${clientMods.mods.length}
- **Optimized for:** Client-side gameplay enhancement

## 🌐 Server Compatibility
This client pack is specifically designed to work with the **Minecraft Mage Server Pack**. All mods are carefully selected to ensure:
- No gameplay advantages over vanilla players
- Perfect compatibility with server-side optimizations
- Enhanced experience without breaking server rules

## 📥 Installation
1. Download using any Modrinth-compatible launcher (Modrinth App, Prism Launcher, etc.)
2. Launch and enjoy enhanced Minecraft!
3. Connect to the Minecraft Mage server for the best experience

## 🤝 Community
Join our community to report issues, suggest improvements, or get help:
- Report bugs and suggest features on our GitHub repository
- Compatible with the Minecraft Mage server ecosystem

---
*Crafted with care for the Minecraft Mage community* ✨`;

// Server pack description  
const serverDescription = `# 🧙‍♂️ Minecraft Mage - Server Pack

**A powerful server-side modpack engineered for maximum performance, comprehensive administration tools, and enhanced technical gameplay on Fabric servers.**

## ⚡ Performance & Optimization

### 🚀 Core Performance Mods
- **Lithium** - Comprehensive server-side optimizations
- **Starlight** - Complete lighting engine rewrite for better performance
- **FerriteCore** - Memory usage optimization 
- **Krypton** - Network stack optimizations
- **ServerCore** - Additional server-specific optimizations

### 📊 Monitoring & Profiling
- **Spark** - Advanced performance profiler with web interface
- **BlueMap** - Live 3D web map of your server world

## 🛠️ Administration & Management

### 👑 Permissions & Commands
- **LuckPerms** - Advanced permission management system
- **Essential Commands** - Teleportation, homes, warps, and utilities

### 🔧 Technical & Debugging
- **Carpet** - Technical Minecraft features and debugging tools
- **Carpet Extra** - Additional carpet features and optimizations
- **AutoSwitch** - Server-side tool switching support

## 🏗️ Perfect for Server Hosts

### 💯 Apex Hosting Optimized
This pack includes automated configuration generation for:
- Optimized \`server.properties\` settings
- JVM-tuned startup scripts  
- Performance monitoring setup
- Admin tool configurations

### 📈 Scalability Features
- **Multi-world support** with BlueMap
- **Advanced permissions** with LuckPerms
- **Performance monitoring** with Spark
- **Technical player tools** with Carpet

## 🔧 Technical Specifications
- **Minecraft Version:** ${packInfo.minecraft}
- **Fabric Loader:** ${packInfo.fabric}
- **Total Mods:** ${serverMods.mods.length}
- **Server Type:** High-performance Fabric server
- **Recommended RAM:** 4GB minimum, 6GB+ for 10+ players

## 🎮 Player Experience
- **AutoSwitch** - Consistent tool switching with client pack
- **Enhanced gameplay** through Carpet technical features
- **Live map** access via BlueMap web interface
- **Smooth performance** with optimized tick rates

## 📥 Server Setup
1. Download the server pack
2. Use generated configuration files from the build system
3. Follow the included Apex Hosting setup guide
4. Configure admin permissions and world settings

## 🤝 Companion Client Pack
Pairs perfectly with the **Minecraft Mage Client Pack** for:
- Synchronized AutoSwitch functionality  
- Compatible performance optimizations
- Balanced gameplay experience

---
*Engineered for performance, built for community* ⚡`;

async function updateProjectDescription(projectId, description, packType) {
  const headers = {
    'Authorization': `Bearer ${MODRINTH_TOKEN}`,
    'User-Agent': USER_AGENT,
    'Content-Type': 'application/json'
  };

  try {
    console.log(`📝 Updating ${packType} pack description...`);
    
    const response = await axios.patch(`${MODRINTH_API}/project/${projectId}`, {
      body: description
    }, { headers });

    console.log(`✅ Successfully updated ${packType} pack description`);
    console.log(`   View at: https://modrinth.com/project/${projectId}`);
    
    return response.data;
    
  } catch (error) {
    if (error.response) {
      console.error(`❌ Failed to update ${packType} description: ${error.response.status}`);
      console.error(`   Response: ${JSON.stringify(error.response.data, null, 2)}`);
    } else {
      console.error(`❌ Failed to update ${packType} description: ${error.message}`);
    }
    throw error;
  }
}

async function main() {
  console.log('📋 Minecraft Mage - Description Updater\\n');
  
  if (!MODRINTH_TOKEN) {
    console.error('❌ MODRINTH_TOKEN is required!');
    process.exit(1);
  }
  
  try {
    // Update client pack description
    await updateProjectDescription(
      uploadConfig.clientProjectId, 
      clientDescription, 
      'client'
    );
    
    // Small delay between requests
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    // Update server pack description
    await updateProjectDescription(
      uploadConfig.serverProjectId, 
      serverDescription, 
      'server'
    );
    
    console.log('\\n✅ All descriptions updated successfully!');
    console.log('\\n🔗 Project Links:');
    console.log(`   Client: https://modrinth.com/project/${uploadConfig.clientProjectId}`);
    console.log(`   Server: https://modrinth.com/project/${uploadConfig.serverProjectId}`);
    
  } catch (error) {
    console.error('\\n❌ Description update failed:', error.message);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

module.exports = { updateProjectDescription };