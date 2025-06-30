# Deployment Guide - Minecraft Mage Modpack

This guide documents the complete deployment process for releasing new versions of the Minecraft Mage modpack.

## Prerequisites

1. **Environment Setup**
   - Node.js installed
   - All dependencies installed (`npm install`)
   - `.env` file configured with:
     - `MODRINTH_TOKEN` - Personal Access Token for Modrinth API
     - `APEX_FTP_HOST` - Apex hosting FTP server
     - `APEX_FTP_USER` - Apex hosting FTP username
     - `APEX_FTP_PASS` - Apex hosting FTP password

2. **Project Structure**
   - `/config/pack-info.json` - Main modpack metadata and version
   - `/config/upload-config.json` - Upload configuration and changelog
   - `/config/mods-client.json` - Client mod configuration
   - `/config/mods-server.json` - Server mod configuration

## Deployment Process

### 1. Version Management

**Update Version Numbers:**
```bash
# Edit version in pack-info.json
vim config/pack-info.json
# Change: "version": "1.0.9" → "version": "1.1.0"

# Update package.json (build system version)
vim package.json  
# Change: "version": "1.0.0" → "version": "1.1.0"
```

**Update Changelog:**
```bash
# Edit upload-config.json with new version's changes
vim config/upload-config.json
# Update the changelog field with new features/changes
```

### 2. Mod Configuration

**Adding New Mods:**
1. Research mods on Modrinth for correct project IDs and file IDs
2. Add to both `/config/mods-client.json` and `/config/mods-server.json`:
   ```json
   {
     "name": "Mod Name",
     "projectId": "modrinth-project-id", 
     "fileId": "modrinth-file-id",
     "filename": "mod-filename.jar",
     "side": "both|client|server"
   }
   ```

**Mod Configuration Files:**
- Store in `/src/shared/config/` 
- Files are automatically copied to build directories
- Example: `/src/shared/config/enchancement.json`

### 3. Build Process

**Build All Packs:**
```bash
npm run build
```

This creates:
- `build/minecraft-mage-client-1.x.x.mrpack` - Standard client pack
- `build/minecraft-mage-client-1.x.x-enhanced.mrpack` - Client with shaders/resource packs
- `build/minecraft-mage-server-1.x.x.mrpack` - Server pack
- `build/minecraft-mage-client-1.x.x-prism.zip` - Prism Launcher format
- `build/minecraft-mage-server-1.x.x-prism.zip` - Server Prism format

**Individual Builds:**
```bash
npm run build:client   # Client only
npm run build:server   # Server only
```

### 4. Deployment to Apex Hosting

**Deploy Server:**
```bash
npm run deploy
```

This automatically:
- Connects to Apex FTP server
- Clears old mod files
- Uploads new mods (30+ files)
- Uploads server.properties
- Uploads config files
- Provides manual steps for Apex control panel

**Manual Apex Steps After Deploy:**
1. Set server type to: **Fabric 1.20.1** (use Apex's built-in option)
2. Add these JVM arguments:
   ```
   -Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dfabric.systemLibraries -Dfabric.skipMcProvider=true -Djava.awt.headless=true
   ```
3. Restart server

### 5. Upload to Modrinth

**Upload Packs:**
```bash
npm run upload          # Upload both client and server
npm run upload:client   # Client only
npm run upload:server   # Server only
```

### 6. Git Version Control

**Create Release:**
```bash
git add .
git commit -m "v1.1.0: Add new mods and features

🤖 Generated with [Claude Code](https://claude.ai/code)

Co-Authored-By: Claude <noreply@anthropic.com>"
git tag v1.1.0
git push origin main --tags
```

## Troubleshooting

### Common Issues

**Mod Download Failures:**
- Check Modrinth project IDs and file IDs are correct
- Verify mod compatibility with Minecraft 1.20.1 Fabric
- Use Modrinth API to get latest file IDs

**Build Failures:**
- Ensure all dependencies are installed
- Check mod configuration JSON syntax
- Verify file paths in mod configs

**Deployment Failures:**
- Check FTP credentials in .env
- Verify Apex server directory structure
- Ensure sufficient disk space on server

### File Locations

- **Version Control**: `config/pack-info.json`
- **Changelog**: `config/upload-config.json`  
- **Mod Lists**: `config/mods-client.json`, `config/mods-server.json`
- **Mod Configs**: `src/shared/config/`
- **Build Output**: `build/`
- **Environment**: `.env`

### Scripts Available

```bash
npm run build           # Build all packs
npm run build:client    # Build client only  
npm run build:server    # Build server only
npm run upload          # Upload to Modrinth
npm run upload:client   # Upload client to Modrinth
npm run upload:server   # Upload server to Modrinth
npm run deploy          # Deploy to Apex hosting
npm run clean           # Clean build artifacts
npm run clean:all       # Clean all build directories
```

## Complete Deployment Checklist

- [ ] Update version in `config/pack-info.json`
- [ ] Update version in `package.json`
- [ ] Update changelog in `config/upload-config.json`
- [ ] Add/update mod configurations if needed
- [ ] Add/update mod config files in `src/shared/config/`
- [ ] Run `npm run build` and verify success
- [ ] Run `npm run deploy` to Apex hosting
- [ ] Complete manual Apex control panel steps
- [ ] Test server startup
- [ ] Run `npm run upload` to publish to Modrinth
- [ ] Test client download and connection
- [ ] Create git commit and tag
- [ ] Push to repository

This process ensures consistent, reliable deployments of the Minecraft Mage modpack across all platforms.