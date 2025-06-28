
# 📦 Minecraft Mage Modpack Manager

This project manages two Minecraft Fabric modpacks for Modrinth:
- **Server Pack**: For Apex Hosting deployment with performance and admin mods
- **Client Pack**: For players connecting to the server with UI/visual enhancements

---

## 🎯 Project Structure

```
minecraft.mage.net/
├── config/                   # Configuration files
│   ├── pack-info.json       # Pack metadata (version, MC version, etc.)
│   ├── mods-client.json     # Client-side mod list
│   └── mods-server.json     # Server-side mod list
├── scripts/                 # Build and automation scripts
│   └── build-packs.js       # Main build script
├── assets/                  # Shared assets
│   └── icon.png            # Pack icon
├── build/                   # Build output (gitignored)
│   ├── client/             # Client pack build directory
│   └── server/             # Server pack build directory
├── src/                     # Source mod files (for manual mods)
│   ├── client/
│   └── server/
└── *.mrpack                # Generated modpack files (gitignored)
```

---

## 🚀 Usage

### Building Modpacks

```bash
# Build both client and server packs
npm run build

# Clean build artifacts
npm run clean

# Clean everything including downloaded mods
npm run clean:all
```

### Adding/Updating Mods

1. Edit `config/mods-client.json` or `config/mods-server.json`
2. Add mod entry with:
   - `name`: Display name
   - `projectId`: Modrinth project ID
   - `fileId`: Modrinth file/version ID
   - `filename`: Expected jar filename
   - `side`: "client", "server", or "both"
   - `manual`: true for mods not on Modrinth

3. Run `npm run build` to regenerate packs

---

## 📋 Key Features

### Automated Build Process
- Downloads mods from Modrinth API
- Calculates SHA-1 and SHA-512 hashes
- Generates proper `modrinth.index.json`
- Creates valid `.mrpack` files

### Server vs Client Separation
- **Server mods**: Performance (Lithium, Starlight), admin tools (LuckPerms, Essential Commands), utilities (Spark, Carpet)
- **Client mods**: Visual (Sodium, Iris), UI (EMI, Jade), QoL (Inventory Profiles Next)
- **Shared mods**: Core APIs and cross-side utilities

### Manual Mod Support
- For mods not available on Modrinth (e.g., custom AutoSwitch mod)
- Place in appropriate `src/` directory before building

---

## 🔧 Configuration

### pack-info.json
Central metadata for both packs:
```json
{
  "name": "Minecraft Mage",
  "version": "1.0.0",
  "minecraft": "1.20.1",
  "fabric": "0.15.7",
  "fabricApi": "0.91.0+1.20.1"
}
```

### Mod List Format
```json
{
  "name": "Mod Display Name",
  "projectId": "modrinth-project-id",
  "fileId": "modrinth-file-id",
  "filename": "exact-jar-filename.jar",
  "side": "client|server|both",
  "manual": false
}
```

---

## 📝 Notes

- AutoSwitch mod requires manual placement in `src/` directories
- Build script follows Modrinth API rate limits
- Generated packs are compatible with:
  - Modrinth launcher
  - Prism Launcher
  - ATLauncher
  - MultiMC

---

## ✅ Implemented Features

- [x] **Automatic version checking** - `npm run check-versions` generates detailed reports
- [x] **Auto-update mod versions** - `npm run update-versions` updates to latest compatible versions
- [x] **Config file generation** - Optimized `server.properties` and startup scripts for Apex Hosting
- [x] **Server startup script generation** - JVM-optimized scripts included
- [x] **Modrinth upload automation** - `npm run upload` and `npm run release` commands
- [x] **Project description management** - `npm run update-descriptions` updates Modrinth project pages
- [x] **AutoSwitch integration** - Proper Modrinth project integration (no longer manual)
- [x] **Axios HTTP client** - Modern async/await API requests with proper authentication
- [x] **Environment variable support** - `.env` file loading with dotenv

## 🚧 Future Enhancements

- [ ] **Dependency resolution and validation** - Automatic mod dependency checking
- [ ] **GitHub Actions for automated releases** - CI/CD pipeline for version updates  
- [ ] **Multi-version support** - Support for multiple Minecraft versions
- [ ] **Mod conflict detection** - Automatic detection of incompatible mods
- [ ] **Resource pack integration** - Support for including resource packs
- [ ] **Custom mod support** - Enhanced workflow for non-Modrinth mods
