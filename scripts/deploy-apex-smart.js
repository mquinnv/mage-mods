#!/usr/bin/env node

// Smart Apex deployment - only syncs changes instead of re-uploading everything
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Client } = require('basic-ftp');

// Check required FTP credentials
const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

if (!FTP_HOST || !FTP_USER || !FTP_PASSWORD) {
  console.error('❌ Missing FTP credentials in .env file');
  console.error('   Required: FTP_HOST, FTP_USER, FTP_PASSWORD');
  console.error('   Optional: FTP_PORT (defaults to 21)');
  process.exit(1);
}

// Helper function to get file hash for comparison
function getFileHash(filePath) {
  const fileContent = fs.readFileSync(filePath);
  return crypto.createHash('md5').update(fileContent).digest('hex');
}

// Helper function to get file size
function getFileSize(filePath) {
  return fs.statSync(filePath).size;
}

async function smartDeploy() {
  const client = new Client();
  
  try {
    console.log('🚀 Smart Deploying Minecraft Mage to Apex Hosting...');
    console.log('📁 Connecting to Apex FTP server...');
    
    // Connect to FTP server
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    await client.send('TYPE I');
    console.log('✅ Connected to FTP server');
    
    // Ensure directories exist
    console.log('📁 Ensuring directory structure...');
    await client.ensureDir('/default/mods');
    await client.ensureDir('/default/config');
    
    // Get local mod files
    const localModsPath = path.join(process.cwd(), 'build', 'server', 'mods');
    if (!fs.existsSync(localModsPath)) {
      throw new Error(`Server mods not found: ${localModsPath}. Run 'npm run build' first.`);
    }
    
    const localMods = fs.readdirSync(localModsPath)
      .filter(file => file.endsWith('.jar'))
      .map(file => ({
        name: file,
        path: path.join(localModsPath, file),
        size: getFileSize(path.join(localModsPath, file))
      }));
    
    console.log(`📦 Found ${localMods.length} local mod files`);
    
    // Get remote mod files
    console.log('📡 Checking remote mod files...');
    let remoteMods = [];
    try {
      const remoteFiles = await client.list('/default/mods');
      remoteMods = remoteFiles
        .filter(file => file.name.endsWith('.jar'))
        .map(file => ({
          name: file.name,
          size: file.size
        }));
      console.log(`📦 Found ${remoteMods.length} remote mod files`);
    } catch (error) {
      console.log('📦 No remote mods found (clean install)');
    }
    
    // Create lookup maps
    const localModMap = new Map(localMods.map(mod => [mod.name, mod]));
    const remoteModMap = new Map(remoteMods.map(mod => [mod.name, mod]));
    
    // Find files to upload (new or changed)
    const toUpload = [];
    const toKeep = [];
    
    for (const localMod of localMods) {
      const remoteMod = remoteModMap.get(localMod.name);
      
      if (!remoteMod) {
        // File doesn't exist remotely - need to upload
        toUpload.push(localMod);
      } else if (remoteMod.size !== localMod.size) {
        // File size differs - need to upload
        toUpload.push(localMod);
      } else {
        // File appears unchanged - keep it
        toKeep.push(localMod);
      }
    }
    
    // Find files to delete (exist remotely but not locally)
    const toDelete = [];
    for (const remoteMod of remoteMods) {
      if (!localModMap.has(remoteMod.name)) {
        toDelete.push(remoteMod);
      }
    }
    
    // Report sync plan
    console.log('\n📋 Sync Plan:');
    console.log(`  🔄 Upload: ${toUpload.length} files`);
    console.log(`  🗑️  Delete: ${toDelete.length} files`);
    console.log(`  ⚡ Keep: ${toKeep.length} files`);
    
    if (toUpload.length === 0 && toDelete.length === 0) {
      console.log('✅ Server is already up to date!');
      return;
    }
    
    // Delete obsolete files
    if (toDelete.length > 0) {
      console.log('\n🗑️  Removing obsolete files...');
      for (const mod of toDelete) {
        try {
          await client.remove(`/default/mods/${mod.name}`);
          console.log(`  ✅ Deleted ${mod.name}`);
          await new Promise(resolve => setTimeout(resolve, 200));
        } catch (error) {
          console.log(`  ❌ Failed to delete ${mod.name}: ${error.message}`);
        }
      }
    }
    
    // Upload new/changed files
    if (toUpload.length > 0) {
      console.log('\n📤 Uploading new/changed files...');
      let uploadCount = 0;
      
      for (const mod of toUpload) {
        try {
          console.log(`  📤 Uploading ${mod.name}...`);
          await client.uploadFrom(mod.path, `/default/mods/${mod.name}`);
          console.log(`    ✅ ${mod.name} uploaded successfully`);
          uploadCount++;
          await new Promise(resolve => setTimeout(resolve, 1000));
        } catch (error) {
          console.log(`  ❌ Failed to upload ${mod.name}: ${error.message}`);
        }
      }
      
      console.log(`✅ Uploaded ${uploadCount}/${toUpload.length} files`);
    }
    
    // Update server.properties if needed
    console.log('\n⚙️  Checking server.properties...');
    const serverConfigPath = path.join(process.cwd(), 'build', 'server-config', 'server.properties');
    if (fs.existsSync(serverConfigPath)) {
      try {
        const localSize = getFileSize(serverConfigPath);
        let needsUpdate = true;
        
        try {
          const remoteList = await client.list('/default/server.properties');
          if (remoteList.length > 0 && remoteList[0].size === localSize) {
            needsUpdate = false;
            console.log('  ⚡ server.properties unchanged');
          }
        } catch (error) {
          // File doesn't exist remotely
        }
        
        if (needsUpdate) {
          await client.uploadFrom(serverConfigPath, '/default/server.properties');
          console.log('  ✅ server.properties updated');
        }
      } catch (error) {
        console.log(`  ❌ Failed to update server.properties: ${error.message}`);
      }
    }
    
    // Update config files
    console.log('\n⚙️  Checking config files...');
    const configPath = path.join(process.cwd(), 'build', 'server-config', 'config');
    if (fs.existsSync(configPath)) {
      const configFiles = fs.readdirSync(configPath);
      let configUpdates = 0;
      
      for (const configFile of configFiles) {
        const configFilePath = path.join(configPath, configFile);
        const stats = fs.statSync(configFilePath);
        
        if (stats.isFile()) {
          try {
            const localSize = getFileSize(configFilePath);
            let needsUpdate = true;
            
            try {
              const remoteList = await client.list(`/default/config/${configFile}`);
              if (remoteList.length > 0 && remoteList[0].size === localSize) {
                needsUpdate = false;
              }
            } catch (error) {
              // File doesn't exist remotely
            }
            
            if (needsUpdate) {
              await client.uploadFrom(configFilePath, `/default/config/${configFile}`);
              console.log(`  ✅ Updated config/${configFile}`);
              configUpdates++;
            }
          } catch (error) {
            console.log(`  ❌ Failed to update config/${configFile}: ${error.message}`);
          }
        }
      }
      
      if (configUpdates === 0) {
        console.log('  ⚡ All config files unchanged');
      }
    }
    
    console.log('\n🎉 Smart deployment complete!');
    console.log('💡 Tip: Only changed files were transferred, saving time and bandwidth');
    
  } catch (error) {
    console.error('❌ Deployment failed:', error.message);
    process.exit(1);
  } finally {
    client.close();
  }
}

if (require.main === module) {
  smartDeploy();
}

module.exports = { smartDeploy };