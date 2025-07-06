# Minecraft Mage v1.0.8 Changelog

## ✨ New Features

### 🗺️ **Minimap & Navigation**
- **Added Xaero's Minimap** - Vanilla-style rotating minimap with waypoints
- **Added Xaero's World Map** - Full-screen world mapping that integrates with minimap
- **Added custom Xaero's configurations** - Optimized settings for performance and usability

### ⚒️ **New Content Mods**
- **Added Oreganized** - Lead, Silver, and Electrum metals with unique mechanics and tools

## 🔧 **Server Fixes & Improvements**

### 🌞 **Fixed Sun Tilt**
- Enabled "Inclined Sky" in Astrocraft configuration
- Sun now properly tilts throughout the day for realistic sky movement

### 🔥 **Fixed Supplementaries Errors**
- Resolved "Failed to handle entity type entity.supplementaries.falling_ash" spam
- Added configuration to disable problematic falling ash entities
- Improved server stability and log cleanliness

### 🗺️ **Fixed BlueMap Setup**
- Added BlueMap core configuration to accept required file downloads
- BlueMap now initializes properly on first server start
- Web mapping interface will work correctly

## 🛠️ **Build System Improvements**

### 📦 **Fixed .mrpack File Sizes**
- **Massive size reduction**: Client pack now 14KB (was ~200MB+)
- **Server pack now 8.2KB** (was ~150MB+)
- Fixed build script to exclude mod files from .mrpack (uses download URLs instead)
- Proper Modrinth format compliance

### 🔄 **Enhanced Mod Download System**
- Auto-fetch latest compatible versions for mods with missing version info
- Improved error handling for Modrinth API integration
- Better support for directory-based configuration copying

## 📈 **Version Info**

**Version:** 1.0.8  
**Minecraft:** 1.20.1  
**Fabric Loader:** 0.16.10  
**Total Mods:** 26 server mods, 35+ client mods

## 🎯 **Client vs Server**

### **Client Pack Includes:**
- All performance mods (Sodium, Iris, etc.)
- Xaero's Minimap & World Map
- UI/UX improvements (JEI, Jade, Mouse Tweaks, etc.)
- All gameplay content mods

### **Server Pack Includes:**
- Performance optimizations (Lithium, Starlight, etc.)
- Admin tools (LuckPerms, Essential Commands, Carpet)
- Xaero's World Map (for server-side waypoint sync)
- All shared gameplay content mods

## 🚀 **Deployment Notes**

- Server successfully deployed to Apex Hosting
- All configuration files updated and deployed
- Ready for immediate server restart with v1.0.8

---

*Generated for Minecraft Mage Modpack v1.0.8*