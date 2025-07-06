#!/usr/bin/env node

require('dotenv').config();
const path = require('path');
const fs = require('fs');
const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function uploadNewMods() {
  const client = new Client();
  
  try {
    console.log('🚀 Uploading v1.0.7 changes to Apex...');
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // First, let's see what's currently there
    console.log('📋 Current mods directory contents:');
    const currentFiles = await client.list('mods');
    currentFiles.forEach(file => {
      if (file.name.endsWith('.jar')) {
        console.log(`  📄 ${file.name}`);
      }
    });
    
    // Critical new files for v1.0.7
    const newMods = [
      'tooltipfix-1.1.1-1.20.jar',  // Critical dependency fix
      'enchancement-1.20-26.jar',   // Already there but ensure it's there
      'dye_depot-1.0.3-fabric.jar' // New mod
    ];
    
    console.log('\n🎯 Uploading critical v1.0.7 mods...');
    
    const serverModsPath = path.join(process.cwd(), 'build', 'server', 'mods');
    
    for (const modFile of newMods) {
      const localPath = path.join(serverModsPath, modFile);
      
      if (!fs.existsSync(localPath)) {
        console.log(`  ⚠️  ${modFile} not found locally, skipping`);
        continue;
      }
      
      try {
        // Delete if exists
        try {
          await client.remove(`mods/${modFile}`);
          console.log(`  🗑️  Deleted existing ${modFile}`);
          await new Promise(resolve => setTimeout(resolve, 1000));
        } catch (error) {
          // File might not exist, that's ok
        }
        
        // Upload new version
        console.log(`  📤 Uploading ${modFile}...`);
        await client.uploadFrom(localPath, `mods/${modFile}`);
        console.log(`  ✅ ${modFile} uploaded successfully`);
        
        // Delay between uploads
        await new Promise(resolve => setTimeout(resolve, 2000));
        
      } catch (error) {
        console.log(`  ❌ Failed to upload ${modFile}: ${error.message}`);
      }
    }
    
    // Final check
    console.log('\n📋 Final mods directory contents:');
    const finalFiles = await client.list('mods');
    const jarFiles = finalFiles.filter(f => f.name.endsWith('.jar'));
    console.log(`  Found ${jarFiles.length} mod files total`);
    
    // Check for critical files
    const hasTooltipFix = jarFiles.some(f => f.name.includes('tooltipfix'));
    const hasEnchancement = jarFiles.some(f => f.name.includes('enchancement'));
    
    console.log(`  ✅ TooltipFix present: ${hasTooltipFix}`);
    console.log(`  ✅ Enchancement present: ${hasEnchancement}`);
    
    if (hasTooltipFix && hasEnchancement) {
      console.log('\n🎉 Critical v1.0.7 dependencies are in place!');
      console.log('The server should now start without the dependency error.');
    } else {
      console.log('\n⚠️  Some critical mods may be missing. Check server startup.');
    }
    
  } catch (error) {
    console.error('❌ Upload failed:', error.message);
  } finally {
    client.close();
  }
}

uploadNewMods();