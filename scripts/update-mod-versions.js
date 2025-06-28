#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');
const fs = require('fs');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-version-updater/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

// Load configurations
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));
const clientMods = JSON.parse(fs.readFileSync('config/mods-client.json', 'utf8'));
const serverMods = JSON.parse(fs.readFileSync('config/mods-server.json', 'utf8'));

async function fetchJson(url) {
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }
  
  console.log(`🔍 Fetching: ${url}`);
  
  try {
    const response = await axios.get(url, { headers });
    console.log(`📡 Status: ${response.status}`);
    return response.data;
  } catch (error) {
    if (error.response) {
      throw new Error(`HTTP ${error.response.status}: ${error.response.statusText}`);
    } else {
      throw new Error(`Network error: ${error.message}`);
    }
  }
}

async function getLatestVersion(projectId, gameVersion, loader = 'fabric') {
  try {
    const url = `${MODRINTH_API}/project/${projectId}/version?game_versions=["${gameVersion}"]&loaders=["${loader}"]`;
    const versions = await fetchJson(url);
    
    if (!versions.length) {
      console.log(`❌ No versions found for ${projectId} on ${gameVersion}`);
      return null;
    }
    
    const latestVersion = versions[0];
    console.log(`✅ ${projectId}: Found ${latestVersion.version_number} (${latestVersion.id})`);
    
    return {
      fileId: latestVersion.id,
      filename: latestVersion.files[0].filename,
      versionNumber: latestVersion.version_number
    };
  } catch (error) {
    console.error(`❌ Failed to get version for ${projectId}: ${error.message}`);
    return null;
  }
}

async function updateModConfigs() {
  console.log('🔄 Updating mod configurations for Minecraft 1.20.1\\n');
  
  // Update client mods
  console.log('=== Updating Client Mods ===');
  for (let i = 0; i < clientMods.mods.length; i++) {
    const mod = clientMods.mods[i];
    
    if (mod.manual) {
      console.log(`⚠️  ${mod.name}: Manual mod - skipping`);
      continue;
    }
    
    console.log(`\\n🔄 Updating ${mod.name}...`);
    const newVersion = await getLatestVersion(mod.projectId, packInfo.minecraft);
    
    if (newVersion) {
      clientMods.mods[i] = {
        ...mod,
        fileId: newVersion.fileId,
        filename: newVersion.filename
      };
      console.log(`   ✅ Updated to ${newVersion.versionNumber}`);
    }
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  
  // Update server mods
  console.log('\\n=== Updating Server Mods ===');
  for (let i = 0; i < serverMods.mods.length; i++) {
    const mod = serverMods.mods[i];
    
    if (mod.manual) {
      console.log(`⚠️  ${mod.name}: Manual mod - skipping`);
      continue;
    }
    
    console.log(`\\n🔄 Updating ${mod.name}...`);
    const newVersion = await getLatestVersion(mod.projectId, packInfo.minecraft);
    
    if (newVersion) {
      serverMods.mods[i] = {
        ...mod,
        fileId: newVersion.fileId,
        filename: newVersion.filename
      };
      console.log(`   ✅ Updated to ${newVersion.versionNumber}`);
    }
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  
  // Save updated configurations
  fs.writeFileSync('config/mods-client.json', JSON.stringify(clientMods, null, 2));
  fs.writeFileSync('config/mods-server.json', JSON.stringify(serverMods, null, 2));
  
  console.log('\\n✅ Mod configurations updated!');
  console.log('📄 Files saved:');
  console.log('   - config/mods-client.json');
  console.log('   - config/mods-server.json');
}

if (require.main === module) {
  updateModConfigs().catch(console.error);
}

module.exports = { updateModConfigs };