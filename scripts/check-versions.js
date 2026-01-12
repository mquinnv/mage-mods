#!/usr/bin/env bun

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');
const fs = require('fs');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-version-checker/1.0.0';
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
  
  try {
    const response = await axios.get(url, { headers });
    return response.data;
  } catch (error) {
    throw new Error(`API request failed: ${error.message}`);
  }
}

async function getLatestVersion(projectId, gameVersion, loader = 'fabric') {
  try {
    const url = `${MODRINTH_API}/project/${projectId}/version?game_versions=["${gameVersion}"]&loaders=["${loader}"]`;
    const versions = await fetchJson(url);
    
    if (!versions.length) {
      return null;
    }
    
    // Return the most recent version
    return versions[0];
  } catch (error) {
    console.warn(`⚠️  Failed to check version for ${projectId}: ${error.message}`);
    return null;
  }
}

async function checkModUpdates(mods, modType) {
  console.log(`\n=== Checking ${modType} Mod Updates ===`);
  const updates = [];
  
  for (const mod of mods) {
    if (mod.manual) {
      console.log(`⚠️  ${mod.name}: Manual mod - skipping version check`);
      continue;
    }
    
    console.log(`🔍 Checking ${mod.name}...`);
    const latestVersion = await getLatestVersion(mod.projectId, packInfo.minecraft);
    
    if (!latestVersion) {
      console.log(`   ❌ No versions found for ${packInfo.minecraft}`);
      continue;
    }
    
    const currentFileId = mod.fileId;
    const latestFileId = latestVersion.id;
    const latestFilename = latestVersion.files[0].filename;
    
    if (currentFileId !== latestFileId) {
      updates.push({
        mod: mod.name,
        projectId: mod.projectId,
        currentVersion: mod.fileId,
        latestVersion: latestFileId,
        currentFilename: mod.filename,
        latestFilename: latestFilename,
        versionNumber: latestVersion.version_number,
        releaseDate: latestVersion.date_published
      });
      
      console.log(`   🆙 Update available: ${latestVersion.version_number}`);
      console.log(`      Current: ${mod.filename}`);
      console.log(`      Latest:  ${latestFilename}`);
    } else {
      console.log(`   ✅ Up to date: ${latestVersion.version_number}`);
    }
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  
  return updates;
}

async function generateUpdateReport(clientUpdates, serverUpdates) {
  const timestamp = new Date().toISOString();
  
  const report = {
    generated: timestamp,
    packInfo: {
      name: packInfo.name,
      version: packInfo.version,
      minecraft: packInfo.minecraft,
      fabric: packInfo.fabric
    },
    updates: {
      client: clientUpdates,
      server: serverUpdates
    },
    summary: {
      clientUpdatesAvailable: clientUpdates.length,
      serverUpdatesAvailable: serverUpdates.length,
      totalUpdatesAvailable: clientUpdates.length + serverUpdates.length
    }
  };
  
  // Save detailed JSON report
  fs.writeFileSync('version-report.json', JSON.stringify(report, null, 2));
  
  // Generate human-readable summary
  let summary = `# Mod Version Report\\n`;
  summary += `Generated: ${new Date(timestamp).toLocaleString()}\\n\\n`;
  summary += `## Pack Information\\n`;
  summary += `- **Pack**: ${packInfo.name} v${packInfo.version}\\n`;
  summary += `- **Minecraft**: ${packInfo.minecraft}\\n`;
  summary += `- **Fabric**: ${packInfo.fabric}\\n\\n`;
  
  if (clientUpdates.length > 0) {
    summary += `## Client Mod Updates Available (${clientUpdates.length})\\n\\n`;
    clientUpdates.forEach(update => {
      summary += `### ${update.mod}\\n`;
      summary += `- **Latest Version**: ${update.versionNumber}\\n`;
      summary += `- **Release Date**: ${new Date(update.releaseDate).toLocaleDateString()}\\n`;
      summary += `- **Current File**: ${update.currentFilename}\\n`;
      summary += `- **Latest File**: ${update.latestFilename}\\n\\n`;
    });
  }
  
  if (serverUpdates.length > 0) {
    summary += `## Server Mod Updates Available (${serverUpdates.length})\\n\\n`;
    serverUpdates.forEach(update => {
      summary += `### ${update.mod}\\n`;
      summary += `- **Latest Version**: ${update.versionNumber}\\n`;
      summary += `- **Release Date**: ${new Date(update.releaseDate).toLocaleDateString()}\\n`;
      summary += `- **Current File**: ${update.currentFilename}\\n`;
      summary += `- **Latest File**: ${update.latestFilename}\\n\\n`;
    });
  }
  
  if (clientUpdates.length === 0 && serverUpdates.length === 0) {
    summary += `## ✅ All mods are up to date!\\n\\n`;
  }
  
  fs.writeFileSync('VERSION_REPORT.md', summary);
  
  return report;
}

async function main() {
  console.log('🔍 Minecraft Mage Version Checker\\n');
  
  if (!MODRINTH_TOKEN) {
    console.warn('⚠️  No MODRINTH_TOKEN found. API requests may be rate-limited.\\n');
  }
  
  try {
    const clientUpdates = await checkModUpdates(clientMods.mods, 'Client');
    const serverUpdates = await checkModUpdates(serverMods.mods, 'Server');
    
    const report = await generateUpdateReport(clientUpdates, serverUpdates);
    
    console.log('\\n=== Version Check Complete ===');
    console.log(`📊 Client updates available: ${clientUpdates.length}`);
    console.log(`📊 Server updates available: ${serverUpdates.length}`);
    console.log(`📄 Reports saved:`);
    console.log(`   - version-report.json (detailed)`);
    console.log(`   - VERSION_REPORT.md (summary)`);
    
    if (report.summary.totalUpdatesAvailable > 0) {
      console.log('\\n💡 To apply updates, manually edit the mod configuration files');
      console.log('   or use the update commands when implemented.');
    }
    
  } catch (error) {
    console.error('❌ Version check failed:', error.message);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

module.exports = { checkModUpdates, generateUpdateReport };