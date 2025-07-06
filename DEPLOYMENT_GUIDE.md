# Minecraft Mage v1.0.6 Deployment Guide

## Files Ready for Deployment

### Server Deployment (Apex Hosting)
- **Modpack File**: `build/minecraft-mage-server-1.0.6.mrpack` (74MB)
- **Config File**: `build/server-config/server.properties` (optimized settings)
- **Startup Script**: `build/server-config/start-server.sh` (JVM optimized)

### Client Distribution
- **Modrinth**: `build/minecraft-mage-client-1.0.6.mrpack` (73MB)
- **Prism Launcher**: `build/minecraft-mage-client-1.0.6-prism.zip` (73MB)

## Apex Hosting Manual Deployment Steps

1. **Log into Apex Control Panel**
   - Go to your server dashboard
   - Stop the server if running

2. **Upload Server Modpack**
   - Go to "File Manager" 
   - Upload `minecraft-mage-server-1.0.6.mrpack`
   - Extract/import the modpack (Apex should have an import option)

3. **Server Configuration**
   - Upload `server.properties` from `build/server-config/server.properties`
   - Set server type to: **Fabric 1.20.1**
   - Add JVM arguments:
     ```
     -Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dfabric.systemLibraries -Dfabric.skipMcProvider=true -Djava.awt.headless=true
     ```

4. **Start Server**
   - Start the server and monitor console for any errors
   - First startup may take 2-3 minutes to generate world

## What's New in v1.0.6

### Replaced
- ❌ EMI (Enough Items) → ✅ JEI (Just Enough Items)

### Added
- ✅ Just Enough Resources (JEI addon for ore/mob data)
- ✅ Enchancement - Enhanced enchantment system
- ✅ Dye Depot - Advanced color customization (compatible with Supplementaries)

### Server Mods (25 total)
- Performance: Lithium, Starlight, FerriteCore, ServerCore
- Admin: LuckPerms, Essential Commands, Carpet, Carpet Extra
- Monitoring: Spark, BlueMap
- Content: Create, Supplementaries, Bountiful Fares, Spelunkery, Astrocraft, Enchancement, Dye Depot

### Client Mods (32 total)
- Performance: Sodium, Iris, Indium, Entity Culling, FerriteCore
- UI: JEI, Just Enough Resources, Jade, Mod Menu, Inventory Profiles Next
- Quality of Life: AutoSwitch, Mouse Tweaks, AppleSkin, Continuity
- Content: Same as server + Better Clouds

## Testing Instructions

### Client Testing
1. Install Modrinth launcher or Prism Launcher
2. Import `build/minecraft-mage-client-1.0.6.mrpack` or `build/minecraft-mage-client-1.0.6-prism.zip`
3. Test JEI functionality (should replace EMI)
4. Verify Enchancement and Dye Depot features work
5. Connect to server for multiplayer testing

### Server Testing
1. Verify all 25 mods loaded without conflicts
2. Test JEI recipe viewing works
3. Test Enchancement features
4. Test Dye Depot with Supplementaries blocks
5. Performance monitoring with Spark: `/spark profiler start`