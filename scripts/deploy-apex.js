#!/usr/bin/env node

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
    
    // Connect to FTP server
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // Check if the server config file exists
    const serverConfigPath = path.join(process.cwd(), 'build', 'server-config', 'server.properties');
    if (!fs.existsSync(serverConfigPath)) {
      throw new Error(`Server config not found: ${serverConfigPath}. Run 'npm run generate-config' first.`);
    }
    
    // Upload optimized server.properties
    console.log('📤 Uploading server.properties...');
    await client.uploadFrom(serverConfigPath, 'server.properties');
    console.log('✅ server.properties uploaded successfully');
    
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