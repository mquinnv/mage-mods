#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

// Load environment variables from .env file
require('dotenv').config();

// SSH server hostname - defaults to 'app' if not specified
const APP_SSH_HOST = process.env.APP_SSH_HOST || 'app';

async function uploadNginxConfig() {
  const configPath = path.join(process.cwd(), 'nginx-modpack-redirects.conf');
  
  if (!fs.existsSync(configPath)) {
    console.error('❌ nginx-modpack-redirects.conf not found');
    console.error('   Run "node scripts/generate-nginx-config.js" first');
    process.exit(1);
  }

  const remoteConfigPath = '/etc/nginx/minecraft-mage-modpack-redirects.conf';
  const remoteTempPath = '/tmp/minecraft-mage-modpack-redirects.conf';
  
  console.log(`🔗 Uploading nginx config to ${APP_SSH_HOST}...`);
  
  try {
    // Step 1: Upload file to temp location
    console.log('📤 Uploading config file...');
    await runCommand(`scp ${configPath} ${APP_SSH_HOST}:${remoteTempPath}`);
    console.log('✅ Config uploaded to temp location');
    
    // Step 2: Move to nginx directory with sudo
    console.log('📂 Moving config to nginx directory...');
    await runCommand(`ssh ${APP_SSH_HOST} "sudo mv ${remoteTempPath} ${remoteConfigPath}"`);
    console.log('✅ Config moved to nginx directory');
    
    // Step 3: Test nginx configuration
    console.log('🧪 Testing nginx configuration...');
    await runCommand(`ssh ${APP_SSH_HOST} "sudo nginx -t"`);
    console.log('✅ Nginx configuration test passed');
    
    // Step 4: Reload nginx
    console.log('🔄 Reloading nginx...');
    await runCommand(`ssh ${APP_SSH_HOST} "sudo nginx -s reload"`);
    console.log('✅ Nginx reloaded successfully');
    
    console.log('\n🎉 Nginx configuration updated!');
    console.log('\n💡 Test the URLs:');
    console.log('   curl -I https://minecraft.mage.net/client/base');
    console.log('   curl -I https://minecraft.mage.net/client/enhanced');
    console.log('   curl -I https://minecraft.mage.net/server');
    
  } catch (error) {
    console.error('❌ Upload failed:', error.message);
    process.exit(1);
  }
}

function runCommand(command) {
  return new Promise((resolve, reject) => {
    console.log(`🔍 Running: ${command}`);
    exec(command, (error, stdout, stderr) => {
      if (error) {
        console.error(`❌ Command failed: ${command}`);
        console.error(`Error: ${error.message}`);
        reject(error);
        return;
      }
      if (stdout) console.log(stdout);
      if (stderr) console.error(stderr);
      resolve();
    });
  });
}

uploadNginxConfig();