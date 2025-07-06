#!/usr/bin/env node

require('dotenv').config();
const path = require('path');
const fs = require('fs');
const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function uploadAllServerMods() {
  const client = new Client();
  
  try {
    console.log('🚀 Uploading ALL v1.0.7 server mods to Apex...');
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // Get current files
    console.log('📋 Current mods directory contents:');
    const currentFiles = await client.list('mods');
    const currentJars = currentFiles.filter(f => f.name.endsWith('.jar'));
    console.log(`  Found ${currentJars.length} existing mod files`);
    currentJars.forEach(file => {
      console.log(`    📄 ${file.name}`);
    });
    
    // Get all server mods that should be there
    const serverModsPath = path.join(process.cwd(), 'build', 'server', 'mods');
    const targetMods = fs.readdirSync(serverModsPath).filter(f => f.endsWith('.jar'));
    console.log(`\n🎯 Target: ${targetMods.length} server mods for v1.0.7`);
    
    // Determine what needs to be uploaded
    const currentModNames = new Set(currentJars.map(f => f.name));
    const modsToUpload = targetMods.filter(mod => !currentModNames.has(mod));
    const modsAlreadyPresent = targetMods.filter(mod => currentModNames.has(mod));
    
    console.log(`\n📊 Upload Status:`);
    console.log(`  ✅ Already present: ${modsAlreadyPresent.length} mods`);
    console.log(`  📤 Need to upload: ${modsToUpload.length} mods`);
    
    if (modsAlreadyPresent.length > 0) {
      console.log(`\n✅ Already uploaded:`);
      modsAlreadyPresent.forEach(mod => console.log(`    📄 ${mod}`));
    }
    
    if (modsToUpload.length === 0) {
      console.log('\n🎉 All mods are already uploaded! No work needed.');
      
      // Quick verification of critical mods
      const hasTooltipFix = currentModNames.has('tooltipfix-1.1.1-1.20.jar');
      const hasEnchancement = currentModNames.has('enchancement-1.20-26.jar');
      console.log(`\n🔍 Critical mod check:`);
      console.log(`  ✅ TooltipFix: ${hasTooltipFix ? 'Present' : 'MISSING'}`);
      console.log(`  ✅ Enchancement: ${hasEnchancement ? 'Present' : 'MISSING'}`);
      
      return;
    }
    
    console.log(`\n📤 Uploading ${modsToUpload.length} remaining mods...`);
    
    let uploadedCount = 0;
    let failedUploads = [];
    
    for (let i = 0; i < modsToUpload.length; i++) {
      const modFile = modsToUpload[i];
      const localPath = path.join(serverModsPath, modFile);
      
      try {
        console.log(`\n[${i+1}/${modsToUpload.length}] 📤 Uploading ${modFile}...`);
        
        // Get file size for progress indication
        const stats = fs.statSync(localPath);
        console.log(`  Size: ${(stats.size / 1024 / 1024).toFixed(1)} MB`);
        
        // Upload the file
        await client.uploadFrom(localPath, `mods/${modFile}`);
        console.log(`  ✅ ${modFile} uploaded successfully`);
        uploadedCount++;
        
        // Progress indicator
        const progress = Math.round((uploadedCount / modsToUpload.length) * 100);
        console.log(`  📊 Progress: ${uploadedCount}/${modsToUpload.length} (${progress}%)`);
        
        // Rate limiting delay (proven to work)
        if (i < modsToUpload.length - 1) {
          console.log(`  ⏳ Waiting 2 seconds before next upload...`);
          await new Promise(resolve => setTimeout(resolve, 2000));
        }
        
      } catch (error) {
        console.log(`  ❌ Failed to upload ${modFile}: ${error.message}`);
        failedUploads.push(modFile);
      }
    }
    
    // Final summary
    console.log(`\n📊 Upload Summary:`);
    console.log(`  ✅ Successfully uploaded: ${uploadedCount} mods`);
    console.log(`  ❌ Failed uploads: ${failedUploads.length} mods`);
    
    if (failedUploads.length > 0) {
      console.log(`\n⚠️  Failed mods:`);
      failedUploads.forEach(mod => console.log(`    📄 ${mod}`));
    }
    
    // Final verification
    console.log(`\n🔍 Final verification...`);
    const finalFiles = await client.list('mods');
    const finalJars = finalFiles.filter(f => f.name.endsWith('.jar'));
    
    console.log(`📋 Final mods directory: ${finalJars.length} total mods`);
    
    // Check critical mods
    const hasTooltipFix = finalJars.some(f => f.name.includes('tooltipfix'));
    const hasEnchancement = finalJars.some(f => f.name.includes('enchancement'));
    const hasDyeDepot = finalJars.some(f => f.name.includes('dye_depot'));
    
    console.log(`\n🎯 Critical v1.0.7 mods check:`);
    console.log(`  ✅ TooltipFix: ${hasTooltipFix ? 'Present' : 'MISSING'}`);
    console.log(`  ✅ Enchancement: ${hasEnchancement ? 'Present' : 'MISSING'}`);
    console.log(`  ✅ Dye Depot: ${hasDyeDepot ? 'Present' : 'MISSING'}`);
    
    if (finalJars.length === targetMods.length) {
      console.log(`\n🎉 SUCCESS! All ${targetMods.length} server mods are deployed!`);
      console.log(`Ready to test Minecraft Mage v1.0.7 server.`);
    } else {
      console.log(`\n⚠️  Expected ${targetMods.length} mods, found ${finalJars.length}`);
      console.log(`Some mods may be missing. Check server startup logs.`);
    }
    
  } catch (error) {
    console.error('❌ Upload failed:', error.message);
    process.exit(1);
  } finally {
    client.close();
  }
}

uploadAllServerMods();