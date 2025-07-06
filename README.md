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

### Client Pack (38 mods)

**Performance & Optimization:**
- [Fabric API](https://modrinth.com/mod/fabric-api) - Essential Fabric mod loader API
- [Sodium](https://modrinth.com/mod/sodium) - Modern rendering engine for improved FPS
- [Iris Shaders](https://modrinth.com/mod/iris) - Shader pack support with Sodium compatibility
- [Indium](https://modrinth.com/mod/indium) - Sodium addon for Fabric Rendering API support
- [Entity Culling](https://modrinth.com/mod/entityculling) - Skip rendering entities behind walls
- [FerriteCore](https://modrinth.com/mod/ferrite-core) - Memory usage optimizations
- [Krypton](https://modrinth.com/mod/krypton) - Network stack optimizations
- [LazyDFU](https://modrinth.com/mod/lazydfu) - Faster startup times
- [Memory Leak Fix](https://modrinth.com/mod/memoryleakfix) - Fixes several memory leaks
- [Better Clouds](https://modrinth.com/mod/better-clouds) - Improved cloud rendering

**User Interface & Quality of Life:**
- [Mod Menu](https://modrinth.com/mod/modmenu) - In-game mod configuration menu
- [Jade](https://modrinth.com/mod/jade) - Advanced tooltips and block information
- [JEI](https://modrinth.com/mod/jei) - Recipe viewer and item search
- [Just Enough Resources](https://modrinth.com/mod/just-enough-resources) - Resource information for JEI
- [AppleSkin](https://modrinth.com/mod/appleskin) - Food/hunger information display
- [Inventory Profiles Next](https://modrinth.com/mod/inventory-profiles-next) - Inventory sorting and management
- [Continuity](https://modrinth.com/mod/continuity) - Connected textures support
- [Cloth Config](https://modrinth.com/mod/cloth-config) - Configuration API for mods
- [AutoSwitch](https://modrinth.com/mod/autoswitch) - Automatic tool switching
- [Mouse Tweaks](https://modrinth.com/mod/mouse-tweaks) - Enhanced mouse controls for inventory
- [ToolTipFix](https://modrinth.com/mod/tooltipfix) - Fixes tooltip rendering issues
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) - In-game minimap
- [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) - Full world map

**Content & Features:**
- [Create](https://modrinth.com/mod/create) - Mechanical contraptions and automation
- [Spelunkery](https://modrinth.com/mod/spelunkery) - Enhanced cave exploration and mining
- [Supplementaries](https://modrinth.com/mod/supplementaries) - Decorative blocks and utilities
- [Bountiful Fares](https://modrinth.com/mod/bountiful-fares) - Enhanced farming and food
- [Enchancement](https://modrinth.com/mod/enchancement) - Enhanced enchanting mechanics
- [Dye Depot](https://modrinth.com/mod/dye-depot) - More dyes and colorful blocks
- [Astrocraft](https://modrinth.com/mod/astrocraft) - Space exploration content
- [Amendments](https://modrinth.com/mod/amendments) - Supplementaries addon
- [Oreganized](https://modrinth.com/mod/oreganized) - New ores and mining content

**World Generation:**
- [Lithostitched](https://modrinth.com/mod/lithostitched) - World generation enhancements
- [Tectonic](https://modrinth.com/mod/tectonic) - Terrain generation overhaul
- [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) - Structure generation
- [Gardens of the Dead](https://modrinth.com/mod/gardens-of-the-dead) - Nether biome enhancements

**Dependencies:**
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) - Kotlin support for Fabric mods
- [libIPN](https://modrinth.com/mod/libipn) - Inventory Profiles Next library
- [Moonlight Lib](https://modrinth.com/mod/moonlight-lib) - Library for various mods
- [Architectury API](https://modrinth.com/mod/architectury-api) - Multi-platform mod library

### Server Pack (29 mods)

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
- [Create](https://modrinth.com/mod/create) - Mechanical contraptions and automation
- [AutoSwitch](https://modrinth.com/mod/autoswitch) - Automatic tool switching
- [Spelunkery](https://modrinth.com/mod/spelunkery) - Enhanced cave exploration and mining
- [Supplementaries](https://modrinth.com/mod/supplementaries) - Decorative blocks and utilities
- [Bountiful Fares](https://modrinth.com/mod/bountiful-fares) - Enhanced farming and food
- [Enchancement](https://modrinth.com/mod/enchancement) - Enhanced enchanting mechanics
- [Dye Depot](https://modrinth.com/mod/dye-depot) - More dyes and colorful blocks
- [Astrocraft](https://modrinth.com/mod/astrocraft) - Space exploration content
- [Amendments](https://modrinth.com/mod/amendments) - Supplementaries addon
- [ToolTipFix](https://modrinth.com/mod/tooltipfix) - Fixes tooltip rendering issues
- [Oreganized](https://modrinth.com/mod/oreganized) - New ores and mining content
- [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) - Full world map

**World Generation:**
- [Lithostitched](https://modrinth.com/mod/lithostitched) - World generation enhancements
- [Tectonic](https://modrinth.com/mod/tectonic) - Terrain generation overhaul
- [Dungeons and Taverns](https://modrinth.com/mod/dungeons-and-taverns) - Structure generation
- [Gardens of the Dead](https://modrinth.com/mod/gardens-of-the-dead) - Nether biome enhancements

**Dependencies:**
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) - Kotlin support for Fabric mods
- [Moonlight Lib](https://modrinth.com/mod/moonlight-lib) - Library for various mods
- [Architectury API](https://modrinth.com/mod/architectury-api) - Multi-platform mod library

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