# Minecraft Mage Modpack Manager

Automated modpack builder for Minecraft Mage server and client packs on Modrinth.

## Quick Start

```bash
# Install dependencies
npm install

# Set up your Modrinth PAT (optional but recommended)
export MODRINTH_TOKEN=mrp_your_token_here

# Build both modpacks
npm run build

# Generate server configuration for Apex Hosting
npm run generate-config

# Check for mod updates
npm run check-versions
```

## Environment Setup

1. Get your PAT at: https://modrinth.com/settings/pats
2. Add it to `.env` file (recommended):
   ```bash
   MODRINTH_TOKEN=mrp_your_token_here
   ```
   Or set as environment variable: `export MODRINTH_TOKEN=mrp_your_token_here`

Using a PAT provides:
- Higher API rate limits
- Access to private projects
- Faster downloads

## Available Commands

```bash
npm run build              # Build both client and server packs
npm run build:client       # Build client pack only
npm run build:server       # Build server pack only
npm run check-versions     # Check for mod updates
npm run update-versions    # Auto-update mod configurations to latest versions
npm run update-descriptions # Update project descriptions on Modrinth
npm run generate-config    # Generate server.properties and startup scripts
npm run setup-server      # Generate config + build server pack
npm run upload            # Upload both packs to Modrinth
npm run release           # Build and upload both packs
npm run clean             # Remove generated .mrpack files
npm run clean:all         # Remove entire build directory
```

## Project Structure

- `config/` - Mod lists and pack metadata
- `scripts/` - Build automation and utilities
- `build/` - Generated files (gitignored)
  - `client/` - Client pack build directory
  - `server/` - Server pack build directory  
  - `server-config/` - Generated server configuration files
- `*.mrpack` - Output modpacks

## Included Mods

### Client Pack (24 mods)

**Performance & Optimization:**
- [Fabric API](https://modrinth.com/mod/fabric-api) - Essential Fabric mod loader API
- [Sodium](https://modrinth.com/mod/sodium) - Modern rendering engine for improved FPS
- [Iris Shaders](https://modrinth.com/mod/iris) - Shader pack support with Sodium compatibility
- [Entity Culling](https://modrinth.com/mod/entityculling) - Skip rendering entities behind walls
- [FerriteCore](https://modrinth.com/mod/ferrite-core) - Memory usage optimizations
- [Krypton](https://modrinth.com/mod/krypton) - Network stack optimizations
- [LazyDFU](https://modrinth.com/mod/lazydfu) - Faster startup times
- [Memory Leak Fix](https://modrinth.com/mod/memoryleakfix) - Fixes several memory leaks

**User Interface & Quality of Life:**
- [Mod Menu](https://modrinth.com/mod/modmenu) - In-game mod configuration menu
- [Jade](https://modrinth.com/mod/jade) - Advanced tooltips and block information
- [EMI](https://modrinth.com/mod/emi) - Recipe viewer and item search
- [AppleSkin](https://modrinth.com/mod/appleskin) - Food/hunger information display
- [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) - Inventory sorting and management
- [Continuity](https://modrinth.com/mod/continuity) - Connected textures support
- [Cloth Config](https://modrinth.com/mod/cloth-config) - Configuration API for mods
- [AutoSwitch](https://modrinth.com/mod/autoswitch) - Automatic tool switching

**Content & Features:**
- [Spelunkery](https://modrinth.com/mod/spelunkery) - Enhanced cave exploration and mining
- [Supplementaries](https://modrinth.com/mod/supplementaries) - Decorative blocks and utilities

**Dependencies:**
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) - Kotlin support for Fabric mods
- [libIPN](https://modrinth.com/mod/libipn) - Inventory Profiles Next library

### Server Pack (14 mods)

**Core & Performance:**
- [Fabric API](https://modrinth.com/mod/fabric-api) - Essential Fabric mod loader API
- [Lithium](https://modrinth.com/mod/lithium) - Server-side performance optimizations
- [Starlight](https://modrinth.com/mod/starlight) - Lighting engine rewrite for better performance
- [FerriteCore](https://modrinth.com/mod/ferrite-core) - Memory usage optimizations
- [Krypton](https://modrinth.com/mod/krypton) - Network stack optimizations
- [ServerCore](https://modrinth.com/mod/servercore) - Server optimization features

**Administration & Monitoring:**
- [Spark](https://modrinth.com/mod/spark) - Performance profiler
- [Carpet](https://modrinth.com/mod/carpet) - Technical utilities and debugging
- [Carpet Extra](https://modrinth.com/mod/carpet-extra) - Additional Carpet features
- [Essential Commands](https://modrinth.com/mod/essential-commands) - Essential server commands
- [LuckPerms](https://modrinth.com/mod/luckperms) - Advanced permissions system
- [BlueMap](https://modrinth.com/mod/bluemap) - 3D web-based world map

**Content & Features:**
- [AutoSwitch](https://modrinth.com/mod/autoswitch) - Automatic tool switching
- [Spelunkery](https://modrinth.com/mod/spelunkery) - Enhanced cave exploration and mining
- [Supplementaries](https://modrinth.com/mod/supplementaries) - Decorative blocks and utilities

**Dependencies:**
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) - Kotlin support for Fabric mods

## Managing Mods

Edit mod lists in:
- `config/mods-client.json` - Client-side mods
- `config/mods-server.json` - Server-side mods

All mods are automatically downloaded from Modrinth. For manual mods:
1. Place .jar files in `src/client/` or `src/server/`
2. Mark as `"manual": true` in config
3. Build normally

## Server Deployment (Apex Hosting)

The `npm run generate-config` command creates:
- **server.properties** - Optimized settings for Fabric + performance mods
- **start-server.sh** - JVM-optimized startup script
- **APEX_HOSTING_SETUP.md** - Detailed configuration guide

### Quick Apex Setup:
1. Run `npm run setup-server`
2. Upload generated `minecraft-mage-server-*.mrpack` to your server
3. Copy `build/server-config/server.properties` to your server root
4. Use the startup script from `build/server-config/start-server.sh`

## Version Management

Check for mod updates with:
```bash
npm run check-versions
```

This generates:
- `version-report.json` - Detailed update information
- `VERSION_REPORT.md` - Human-readable summary

## Uploading to Modrinth

### Automatic Upload (Recommended)

1. Add your project IDs to `config/upload-config.json`:
   ```json
   {
     "clientProjectId": "your-client-project-id",
     "serverProjectId": "your-server-project-id"
   }
   ```

2. Run the release command:
   ```bash
   npm run release  # Builds and uploads both packs
   ```

   Or individually:
   ```bash
   npm run upload:client  # Upload client pack only
   npm run upload:server  # Upload server pack only
   ```

### Manual Upload

Alternatively, upload the generated `.mrpack` files manually through the Modrinth website.

## Contributing

1. Fork the repository
2. Make your changes
3. Test with `npm run build`
4. Submit a pull request

## License

MIT License - see LICENSE file for details.