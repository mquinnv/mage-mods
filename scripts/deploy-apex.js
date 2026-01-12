#!/usr/bin/env bun

// Load environment variables from .env file
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const { Client } = require('basic-ftp');

// Check required FTP credentials
const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

if (!FTP_HOST || !FTP_USER || !FTP_PASSWORD) {
  console.error('❌ Missing FTP credentials in .env file');
  console.error('   Required: FTP_HOST, FTP_USER, FTP_PASSWORD');
  console.error('   Optional: FTP_PORT (defaults to 21)');
  process.exit(1);
}

// Load pack info
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));

async function deployToApex() {
  const client = new Client();
  
  try {
    console.log('🚀 Deploying Minecraft Mage to Apex Hosting...');
    console.log('📁 Connecting to Apex FTP server...');
    console.log(`   Host: ${FTP_HOST}`);
    console.log(`   User: ${FTP_USER}`);
    console.log(`   Port: ${FTP_PORT}`);
    
    // Connect to FTP server
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    // Set binary mode for file transfers
    await client.send('TYPE I');
    
    console.log('✅ Connected to FTP server');
    
    // Debug: Show current working directory
    const pwd = await client.pwd();
    console.log(`   Current directory: ${pwd}`);
    
    // Debug: Test basic upload capability
    console.log('🧪 Testing basic upload capability...');
    try {
      const testContent = 'test deployment ' + new Date().toISOString();
      fs.writeFileSync('/tmp/test-upload.txt', testContent);
      await client.uploadFrom('/tmp/test-upload.txt', 'test-upload.txt');
      console.log('✅ Basic upload test passed');
      await client.remove('test-upload.txt'); // Clean up
    } catch (error) {
      console.log(`❌ Basic upload test failed: ${error.message}`);
      console.log(`   Error code: ${error.code}`);
    }
    
    // Check if the server build exists
    const serverModsPath = path.join(process.cwd(), 'build', 'server', 'mods');
    const serverConfigPath = path.join(process.cwd(), 'build', 'server-config', 'server.properties');
    
    if (!fs.existsSync(serverModsPath)) {
      throw new Error(`Server mods not found: ${serverModsPath}. Run 'npm run build' first.`);
    }
    
    if (!fs.existsSync(serverConfigPath)) {
      throw new Error(`Server config not found: ${serverConfigPath}. Run 'npm run generate-config' first.`);
    }
    
    // Create all necessary directories first
    console.log('📁 Creating directory structure...');
    try {
      await client.ensureDir('/default');
      await client.ensureDir('/default/jar');
      await client.ensureDir('/default/mods');
      await client.ensureDir('/default/config');
      console.log('✅ Directory structure created');
    } catch (error) {
      console.log(`⚠️  Directory creation failed: ${error.message}`);
    }

    // Skip jar file uploads - use Apex's built-in Fabric setup
    console.log('✅ Using Apex built-in Fabric installation')

    // Clear old mod files
    console.log('🧹 Clearing old mod files...');
    try {
      const existingFiles = await client.list('/default/mods');
      let deletedCount = 0;
      
      for (const file of existingFiles) {
        if (file.name.endsWith('.jar')) {
          try {
            await client.remove(`/default/mods/${file.name}`);
            console.log(`  🗑️  Deleted ${file.name}`);
            deletedCount++;
            // Small delay between deletions
            await new Promise(resolve => setTimeout(resolve, 500));
          } catch (error) {
            console.log(`  ⚠️  Could not delete ${file.name}: ${error.message}`);
          }
        }
      }
      console.log(`✅ Cleared ${deletedCount} old mod files`);
    } catch (error) {
      console.log(`⚠️  Could not clear old files: ${error.message}`);
      console.log('  Continuing with upload...');
    }
    
    // Upload all server mods
    console.log('📤 Uploading server mods...');
    const modFiles = fs.readdirSync(serverModsPath);
    let uploadedCount = 0;
    let failedUploads = [];
    
    for (const modFile of modFiles) {
      if (modFile.endsWith('.jar')) {
        try {
          console.log(`  Uploading ${modFile}...`);
          await client.uploadFrom(
            path.join(serverModsPath, modFile),
            `/default/mods/${modFile}`
          );
          uploadedCount++;
          console.log(`    ✅ ${modFile} uploaded successfully`);
          
          // Longer delay between uploads to prevent rate limiting
          await new Promise(resolve => setTimeout(resolve, 2000));
        } catch (error) {
          console.log(`  ❌ Failed to upload ${modFile}: ${error.message}`);
          console.log(`     Error code: ${error.code}`);
          console.log(`     Full error:`, error);
          failedUploads.push(modFile);
        }
      }
    }
    
    console.log(`✅ Uploaded ${uploadedCount} mod files`);
    if (failedUploads.length > 0) {
      console.log(`⚠️  Failed uploads: ${failedUploads.join(', ')}`);
    }
    
    // Upload server configuration files
    console.log('📤 Uploading server.properties...');
    try {
      await client.uploadFrom(serverConfigPath, '/default/server.properties');
      console.log('✅ server.properties uploaded successfully');
    } catch (error) {
      console.log(`❌ Failed to upload server.properties: ${error.message}`);
    }
    
    // Upload shared config files
    const configPath = path.join(process.cwd(), 'build', 'server-config', 'config');
    if (fs.existsSync(configPath)) {
      console.log('📤 Uploading mod config files...');
      try {
        const configFiles = fs.readdirSync(configPath);
        let configUploadedCount = 0;
        
        for (const configFile of configFiles) {
          const configFilePath = path.join(configPath, configFile);
          const stats = fs.statSync(configFilePath);
          
          if (stats.isDirectory()) {
            // Handle directory upload
            try {
              console.log(`  Uploading config directory ${configFile}...`);
              await client.ensureDir(`/default/config/${configFile}`);
              
              const dirFiles = fs.readdirSync(configFilePath);
              for (const subFile of dirFiles) {
                const subFilePath = path.join(configFilePath, subFile);
                const subStats = fs.statSync(subFilePath);
                
                if (subStats.isFile()) {
                  console.log(`    Uploading ${configFile}/${subFile}...`);
                  await client.uploadFrom(
                    subFilePath,
                    `/default/config/${configFile}/${subFile}`
                  );
                  console.log(`    ✅ ${configFile}/${subFile} uploaded successfully`);
                }
              }
              configUploadedCount++;
            } catch (error) {
              console.log(`  ❌ Failed to upload directory ${configFile}: ${error.message}`);
            }
          } else {
            // Handle file upload
            try {
              console.log(`  Uploading config/${configFile}...`);
              await client.uploadFrom(
                configFilePath,
                `/default/config/${configFile}`
              );
              configUploadedCount++;
            } catch (error) {
              console.log(`  ❌ Failed to upload ${configFile}: ${error.message}`);
            }
          }
        }
        console.log(`✅ Uploaded ${configUploadedCount}/${configFiles.length} config files/directories`);
      } catch (error) {
        console.log(`❌ Config directory upload failed: ${error.message}`);
      }
    }
    
    console.log('✅ Deployment complete!');
    console.log('');
    console.log('📋 Next steps on Apex control panel:');
    console.log('1. Set server type to: Fabric 1.20.1 (use Apex\'s built-in option)');
    console.log('2. Add JVM arguments:');
    console.log('   -Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dfabric.systemLibraries -Dfabric.skipMcProvider=true -Djava.awt.headless=true');
    console.log('3. Restart server');
    
  } catch (error) {
    console.error('❌ Deployment failed:', error.message);
    process.exit(1);
  } finally {
    client.close();
  }
}

deployToApex();