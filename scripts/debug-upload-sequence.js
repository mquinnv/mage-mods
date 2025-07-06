#!/usr/bin/env node

require('dotenv').config();
const path = require('path');
const fs = require('fs');
const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function testSequentialUpload() {
  const client = new Client();
  
  try {
    console.log('🧪 Testing sequential mod uploads...');
    
    // Enable detailed logging
    client.ftp.verbose = true;
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    const serverModsPath = path.join(process.cwd(), 'build', 'server', 'mods');
    const modFiles = fs.readdirSync(serverModsPath).filter(f => f.endsWith('.jar'));
    
    console.log(`📋 Found ${modFiles.length} mod files to upload`);
    
    // Try uploading just the first 3 files with long delays
    const testFiles = modFiles.slice(0, 3);
    
    for (let i = 0; i < testFiles.length; i++) {
      const modFile = testFiles[i];
      console.log(`\n📤 [${i+1}/${testFiles.length}] Uploading ${modFile}...`);
      
      try {
        const localPath = path.join(serverModsPath, modFile);
        const remotePath = `mods/${modFile}`;
        
        // Get file size for context
        const stats = fs.statSync(localPath);
        console.log(`  File size: ${(stats.size / 1024).toFixed(1)} KB`);
        
        await client.uploadFrom(localPath, remotePath);
        console.log(`  ✅ ${modFile} uploaded successfully`);
        
        // Long delay between uploads
        if (i < testFiles.length - 1) {
          console.log('  ⏳ Waiting 3 seconds before next upload...');
          await new Promise(resolve => setTimeout(resolve, 3000));
        }
        
      } catch (error) {
        console.log(`  ❌ Failed to upload ${modFile}:`);
        console.log(`     Error: ${error.message}`);
        console.log(`     Code: ${error.code}`);
        console.log(`     Full error:`, error);
        
        // Try to continue with next file
        console.log('  🔄 Continuing with next file...');
      }
    }
    
    // Check what's currently in the mods directory
    console.log('\n📁 Final mods directory contents:');
    const finalFiles = await client.list('mods');
    finalFiles.forEach(file => {
      console.log(`  ${file.type === 'd' ? '📁' : '📄'} ${file.name} (${file.size} bytes)`);
    });
    
  } catch (error) {
    console.error('❌ Test failed:', error.message);
    console.error('Full error:', error);
  } finally {
    client.close();
  }
}

testSequentialUpload();