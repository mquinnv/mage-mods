# Modrinth Migration Guide

This document explains how to migrate from the old single-project approach to the new multi-project structure as required by Modrinth.

## What Changed

Based on Modrinth feedback, we've split the modpack into separate projects:

1. **Mage.net Client Base** - The base client modpack
2. **Mage.net Client Enhanced** - Enhanced client with shaders and resource packs
3. **Mage.net Server** - The server modpack

## Migration Steps

### 1. Create New Modrinth Projects

First, create the new separate projects on Modrinth:

```bash
npm run create-projects
```

This will:
- Create three separate Modrinth projects
- Update `config/upload-config.json` with the new project IDs
- Set projects as unlisted initially

### 2. Upload to New Projects

Upload your modpacks to the new separate projects:

```bash
# Upload all variants
npm run upload

# Or upload specific variants
npm run upload:client-base
npm run upload:client-enhanced  
npm run upload:server
```

### 3. Update Project Descriptions

The project descriptions have been rewritten to be plain text without formatting, complying with Modrinth's section 5.3 requirements.

## New Project Structure

- `config/modrinth-projects.json` - Project configurations for each variant
- `scripts/create-modrinth-projects.js` - Script to create new projects
- Updated `scripts/upload-packs.js` - Now handles multiple projects

## Benefits

- **Modrinth Compliance**: Each variant is now a separate project as required
- **Better User Experience**: Users can easily find and update the specific variant they want
- **Proper Versioning**: Each project has its own linear version history
- **Launcher Compatibility**: Modrinth App and other launchers can properly update each variant

## Notes

- Projects are initially created as unlisted
- Once descriptions are finalized, request listing through Modrinth
- Legacy upload commands still work during transition period