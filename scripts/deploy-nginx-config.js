#!/usr/bin/env node

// Combined script to generate and deploy nginx config
const { exec } = require('child_process');
const path = require('path');

async function runScript(scriptPath) {
  return new Promise((resolve, reject) => {
    exec(`node ${scriptPath}`, (error, stdout, stderr) => {
      if (error) {
        reject(error);
        return;
      }
      console.log(stdout);
      if (stderr) {
        console.error(stderr);
      }
      resolve();
    });
  });
}

async function deployNginxConfig() {
  try {
    console.log('🚀 Deploying nginx configuration for modpack redirects...\n');
    
    // Step 1: Generate the config
    console.log('📝 Step 1: Generating nginx configuration...');
    await runScript('scripts/generate-nginx-config.js');
    
    console.log('\n📤 Step 2: Uploading to app server...');
    await runScript('scripts/upload-nginx-config.js');
    
    console.log('\n🎉 Deployment completed successfully!');
    console.log('\n🔗 Your modpack URLs are now live:');
    console.log('   https://minecraft.mage.net/client/base');
    console.log('   https://minecraft.mage.net/client/enhanced');
    console.log('   https://minecraft.mage.net/server');
    console.log('   https://minecraft.mage.net/modpack-info');
    
  } catch (error) {
    console.error('❌ Deployment failed:', error.message);
    process.exit(1);
  }
}

deployNginxConfig();