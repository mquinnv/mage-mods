# Deploy Minecraft Mage v1.0.7 to Apex Hosting

## Automatic Config Deployment ✅ COMPLETED
- server.properties has been uploaded via FTP automatically
- Server is configured for v1.0.7 with optimized settings

## Manual Mod Deployment Required

### Option 1: Upload .mrpack via Apex Control Panel (RECOMMENDED)
1. Download: `build/minecraft-mage-server-1.0.7.mrpack` (74MB)
2. Go to Apex Control Panel → File Manager
3. Upload the .mrpack file
4. Use Apex's "Import Modpack" feature (if available)

### Option 2: Manual Mod Upload via File Manager
1. Go to Apex Control Panel → File Manager
2. Navigate to `/mods/` directory  
3. Delete old mod files (if any)
4. Upload all files from `build/server/mods/` (24 files):

**Critical New Files for v1.0.7:**
- `tooltipfix-1.1.1-1.20.jar` (NEW - fixes Enchancement dependency)
- `enchancement-1.20-26.jar` (enhanced enchantments)
- `dye_depot-1.0.3-fabric.jar` (color customization)
- All other existing mods

### Option 3: Use Apex's Modpack Import
Some Apex plans support direct modpack import:
1. Upload `minecraft-mage-server-1.0.7.mrpack`
2. Use "Import from .mrpack" option in control panel

## Server Configuration (Already Applied)
✅ Server type: Fabric 1.20.1  
✅ server.properties updated for v1.0.7  
✅ JVM arguments configured for performance  

## What's New in v1.0.7
- ✅ **FIXED:** Added TooltipFix dependency (resolves Enchancement crash)
- ✅ EMI → JEI transition complete
- ✅ Enchancement mod working properly
- ✅ Dye Depot + Supplementaries compatibility
- ✅ 24 server mods total

## Testing Checklist
- [ ] Server starts without dependency errors
- [ ] All 24 mods load successfully  
- [ ] Enchancement features work
- [ ] JEI recipes display correctly
- [ ] Client can connect to server
- [ ] No mod conflicts in console

## Files Ready for Upload
- **Server Modpack**: `build/minecraft-mage-server-1.0.7.mrpack`
- **Individual Mods**: `build/server/mods/` (24 .jar files)
- **Config**: Already uploaded via FTP ✅