# Minecraft Mage Server Configuration Notes

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
