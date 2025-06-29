#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');
const fs = require('fs');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-forward-updater/1.0.0';
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

async function getBestVersion(projectId, gameVersion, loader = 'fabric') {
  try {
    const url = `${MODRINTH_API}/project/${projectId}/version?game_versions=["${gameVersion}"]&loaders=["${loader}"]`;
    const versions = await fetchJson(url);
    
    if (!versions.length) {
      console.log(`❌ No versions found for ${projectId} on ${gameVersion}`);
      return null;
    }
    
    // Forward-looking approach: prefer newer versions but be conservative
    // Sort by date and prefer release versions over beta/alpha
    const sortedVersions = versions.sort((a, b) => {
      // First priority: release type (release > beta > alpha)
      const releaseOrder = { release: 3, beta: 2, alpha: 1 };
      const aOrder = releaseOrder[a.version_type] || 0;
      const bOrder = releaseOrder[b.version_type] || 0;
      
      if (aOrder !== bOrder) {
        return bOrder - aOrder; // Higher order first
      }
      
      // Second priority: newer date
      return new Date(b.date_published) - new Date(a.date_published);
    });
    
    const bestVersion = sortedVersions[0];
    console.log(`✅ ${projectId}: Selected ${bestVersion.version_number} (${bestVersion.version_type}, ${bestVersion.id})`);
    console.log(`   Published: ${new Date(bestVersion.date_published).toLocaleDateString()}`);
    
    // Check dependencies
    if (bestVersion.dependencies.length > 0) {
      console.log(`   Dependencies: ${bestVersion.dependencies.length}`);
      for (const dep of bestVersion.dependencies) {
        if (dep.dependency_type === 'required') {
          console.log(`     Required: ${dep.project_id}`);
        }
      }
    }
    
    return {
      fileId: bestVersion.id,
      filename: bestVersion.files[0].filename,
      versionNumber: bestVersion.version_number,
      versionType: bestVersion.version_type,
      datePublished: bestVersion.date_published,
      dependencies: bestVersion.dependencies
    };
  } catch (error) {
    console.error(`❌ Failed to get version for ${projectId}: ${error.message}`);
    return null;
  }
}

async function updateModConfigs() {
  console.log('🔄 Updating mod configurations for Minecraft 1.20.1 with forward-looking approach\n');
  
  // Update client mods
  console.log('=== Updating Client Mods ===');
  for (let i = 0; i < clientMods.mods.length; i++) {
    const mod = clientMods.mods[i];
    
    if (mod.manual) {
      console.log(`⚠️  ${mod.name}: Manual mod - skipping`);
      continue;
    }
    
    console.log(`\n🔄 Updating ${mod.name}...`);
    const newVersion = await getBestVersion(mod.projectId, packInfo.minecraft);
    
    if (newVersion) {
      const wasUpdated = mod.fileId !== newVersion.fileId;
      clientMods.mods[i] = {
        ...mod,
        fileId: newVersion.fileId,
        filename: newVersion.filename
      };
      
      if (wasUpdated) {
        console.log(`   ✅ Updated to ${newVersion.versionNumber} (${newVersion.versionType})`);
      } else {
        console.log(`   ℹ️  Already at latest version ${newVersion.versionNumber}`);
      }
    }
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  
  // Update server mods
  console.log('\n=== Updating Server Mods ===');
  for (let i = 0; i < serverMods.mods.length; i++) {
    const mod = serverMods.mods[i];
    
    if (mod.manual) {
      console.log(`⚠️  ${mod.name}: Manual mod - skipping`);
      continue;
    }
    
    console.log(`\n🔄 Updating ${mod.name}...`);
    const newVersion = await getBestVersion(mod.projectId, packInfo.minecraft);
    
    if (newVersion) {
      const wasUpdated = mod.fileId !== newVersion.fileId;
      serverMods.mods[i] = {
        ...mod,
        fileId: newVersion.fileId,
        filename: newVersion.filename
      };
      
      if (wasUpdated) {
        console.log(`   ✅ Updated to ${newVersion.versionNumber} (${newVersion.versionType})`);
      } else {
        console.log(`   ℹ️  Already at latest version ${newVersion.versionNumber}`);
      }
    }
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  
  // Save updated configurations
  fs.writeFileSync('config/mods-client.json', JSON.stringify(clientMods, null, 2));
  fs.writeFileSync('config/mods-server.json', JSON.stringify(serverMods, null, 2));
  
  console.log('\n✅ Mod configurations updated with forward-looking approach!');
  console.log('📄 Files saved:');
  console.log('   - config/mods-client.json');
  console.log('   - config/mods-server.json');
  console.log('\n💡 Forward-looking strategy: Prioritized release versions over beta/alpha');
  console.log('   and selected the most recent stable versions available.');
}

if (require.main === module) {
  updateModConfigs().catch(console.error);
}

module.exports = { updateModConfigs };