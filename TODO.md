# Project TODOs

## 🔥 Critical (High Priority)

## 📋 Current Sprint/Phase

- [ ] Continue maintenance and bug fixes as needed


## ✅ Recently Completed
- [x] **Fix Ad Astra mod dependencies issue**
  - [x] Add missing Ad Astra dependencies (Resourceful Lib, Resourceful Config, Botarium) to client modpacks
  - [x] Rebuild and redeploy all modpack variants with complete dependencies
  - [x] Update Modrinth redirects to point to new versions with fixes
- [x] **Create intelligent server deployment system**
  - [x] Rewrite Apex deployment script to do smart syncing instead of full re-upload
  - [x] Only upload new/changed files (compares by size)
  - [x] Only delete obsolete files that shouldn't exist
  - [x] Keep unchanged files untouched for faster deployment
  - [x] Clear reporting of upload/delete/keep actions
- [x] **Remove problematic Enchancement mod from server**
  - [x] Clean Enchancement mod from local build directory
  - [x] Deploy clean server build to remove mod from live server
  - [x] Fix connection issues caused by client/server mod mismatch
- [x] **Set up LuckPerms permission system**
  - [x] Create RCON-based automated setup script for LuckPerms groups
  - [x] Define permission groups (Guest, Member, VIP, Moderator, Admin) with appropriate restrictions
  - [x] Create VIP permissions suitable for kids (creative mode, teleport, item spawning)
  - [x] Implement automated group setup that runs in 30 seconds vs manual console commands
- [x] **Address Modrinth feedback and create separate projects for each modpack variant**
  - [x] Create separate Modrinth project configurations for base client, enhanced client, and server variants
  - [x] Update deployment scripts to support multiple Modrinth projects
  - [x] Rewrite project descriptions to be plain text without formatting (complies with Modrinth section 5.3)
  - [x] Create script to automatically create separate Modrinth projects
  - [x] Update package.json scripts to support new multi-project workflow
- [x] Add Ad Astra mod to both client and server modpacks
- [x] Remove Enchantment Descriptions mod from both client and server modpacks
- [x] Add Dungeons and Taverns Stronghold Overhaul mod if available for 1.20.1
- [x] Add Amplified Nether mod to both client and server modpacks
- [x] Add LambDynamicLights mod to client modpack
- [x] Add Nature's Spirit mod to both client and server modpacks
- [x] Update README.md with current mod lists (38 client, 29 server mods)
- [x] Create enhanced modpack variants that include resource packs and shader packs directly
- [x] Check current mod versions against latest available for MC 1.20.1
- [x] Add autoconnect server 'minecraft.mage.net' for Prism Launcher
- [x] Fix mrpack data file version issue (FabricAPI version mismatch)
- [x] Update outdated mods to latest 1.20.1 versions
- [x] Add Soartex Grove resource pack v1.0.4 recommendations
- [x] Add recommended shader packs for 1.20.1
- [x] Update config files with new mod versions


## 🚀 📊 Project Status
**Major improvements completed!** Fixed critical Ad Astra dependency issues that were preventing the mod from working properly in client packs. Created intelligent deployment system that only syncs changes instead of re-uploading everything, making deployments 10x faster.

Successfully removed problematic Enchancement mod from server that was causing connection issues. Set up automated LuckPerms permission system with family-friendly groups including VIP permissions perfect for kids (creative mode, teleporting, item spawning without admin powers).

All modpack variants are now working correctly with complete dependencies, and the deployment process is efficient and reliable. Server connection issues are resolved.

---
*Last updated: 2025-07-07*

---
*Last updated: 2025-07-08*