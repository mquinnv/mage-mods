# Minecraft Mage Modpack Changelog

## v1.0.5 - 2025-06-29

### Fixed
- **Eliminated duplicate mod versions** that were causing conflicts:
  - Removed duplicate Indium versions (1.0.27 + 1.0.36)
  - Removed duplicate Jade versions (11.12.3 + 11.13.1)
- **Updated mods to latest versions**:
  - Jade updated to 11.13.1 for improved compatibility
  - All mods verified to use latest stable releases for 1.20.1

### Improved
- **Runtime stability and performance**:
  - Fixed mod loading conflicts that caused warnings
  - Cleaner startup with fewer error messages
  - Better mod compatibility across the pack

### Technical
- Investigated and documented known mod issues:
  - Missing texture warnings are confirmed mod bugs (cosmetic only)
  - Missing sound warnings are expected cross-mod compatibility references
  - Farmer's Delight recipe errors are intentional (using Bountiful Fares instead)
- All identified issues are either fixed or documented as harmless

## v1.0.2 - 2025-06-28

### Added
- **Server configuration now works in .mrpack format!**
  - minecraft.mage.net automatically appears in multiplayer server list
  - Server connection info and instructions included
- High-performance JVM arguments for better client performance:
  - Optimized G1 garbage collector settings
  - Reduced stuttering and improved FPS
  - 4GB RAM allocation with smart memory management

### Enhanced
- Both .mrpack and Prism Launcher formats include server auto-configuration
- Improved launcher compatibility across all platforms
- Better mod loading timeouts and error handling

### Technical
- Added overrides folder with servers.dat, options.txt, and info files
- Implemented NBT format server list for cross-launcher compatibility

## v1.0.1 - 2025-06-28

### Fixed
- Updated Fabric Loader from 0.15.7 to 0.16.10 for compatibility
- Added missing dependencies for Inventory Profiles Next:
  - fabric-language-kotlin 1.13.4+kotlin.2.2.0
  - libIPN 4.0.2
- Fixed Java version configuration to properly use Java 17
- Removed outdated PermSize JVM argument
- Added optimized G1GC JVM arguments for better performance

### Added
- Server configuration for minecraft.mage.net in Prism Launcher format
- Automatic server list entry for Minecraft Mage Server
- Server connection instructions and info files

### Technical
- Fixed AutoSwitch mod project ID for proper download URLs
- Optimized icon sizes for better launcher compatibility
- Both .mrpack and Prism Launcher .zip formats now available

## v1.0.0 - 2025-06-28

### Initial Release
- Client modpack with performance mods, shaders, and AutoSwitch
- Server modpack with admin tools and performance optimizations
- 18 client mods, 14 server mods
- Full Fabric 1.20.1 compatibility