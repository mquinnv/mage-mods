#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const fs = require('fs');
const https = require('https');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.error('❌ MODRINTH_TOKEN is required!');
  console.error('   Set it in your environment or .env file');
  process.exit(1);
}

// Load configurations
const uploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));

// Old project ID to get the icon from
const OLD_PROJECT_ID = 'L1ET1T4t'; // The original client project

async function downloadFile(url, filePath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(filePath);
    https.get(url, (response) => {
      response.pipe(file);
      file.on('finish', () => {
        file.close();
        resolve();
      });
      file.on('error', (err) => {
        fs.unlink(filePath, () => {}); // Delete the file async
        reject(err);
      });
    }).on('error', reject);
  });
}

async function getProjectIcon(projectId) {
  return new Promise((resolve, reject) => {
    const options = {
      method: 'GET',
      hostname: 'api.modrinth.com',
      path: `/v2/project/${projectId}`,
      headers: {
        'Authorization': `Bearer ${MODRINTH_TOKEN}`,
        'User-Agent': 'minecraft-mage-pack-builder/1.0.0'
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200) {
          const project = JSON.parse(data);
          resolve(project.icon_url);
        } else {
          console.error(`❌ Failed to get project info for ${projectId}`);
          console.error(`   Status: ${res.statusCode}`);
          console.error(`   Response: ${data}`);
          reject(new Error(`Failed to get project: ${res.statusCode}`));
        }
      });
    });

    req.on('error', reject);
    req.end();
  });
}

async function updateProjectIcon(projectId, iconPath) {
  console.log(`📤 Updating icon for project ${projectId}...`);
  
  // Determine file extension
  const extension = iconPath.split('.').pop().toLowerCase();
  
  // Map extensions to content types
  const contentTypeMap = {
    'png': 'image/png',
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'bmp': 'image/bmp',
    'gif': 'image/gif',
    'webp': 'image/webp',
    'svg': 'image/svg+xml',
    'svgz': 'image/svg+xml',
    'rgb': 'image/rgb'
  };
  
  const contentType = contentTypeMap[extension] || 'image/png';
  
  return new Promise((resolve, reject) => {
    // Read file data
    const fileData = fs.readFileSync(iconPath);
    
    // Check file size (256 KiB limit)
    if (fileData.length > 256 * 1024) {
      reject(new Error('Icon file too large. Maximum size is 256 KiB.'));
      return;
    }
    
    const options = {
      method: 'PATCH',
      hostname: 'api.modrinth.com',
      path: `/v2/project/${projectId}/icon?ext=${extension}`,
      headers: {
        'Authorization': `Bearer ${MODRINTH_TOKEN}`,
        'User-Agent': 'minecraft-mage-pack-builder/1.0.0',
        'Content-Type': contentType,
        'Content-Length': fileData.length
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200 || res.statusCode === 204) {
          console.log(`✅ Successfully updated icon for project ${projectId}`);
          resolve();
        } else {
          console.error(`❌ Failed to update icon for project ${projectId}`);
          console.error(`   Status: ${res.statusCode}`);
          console.error(`   Response: ${data}`);
          reject(new Error(`Failed to update icon: ${res.statusCode}`));
        }
      });
    });

    req.on('error', reject);
    req.write(fileData);
    req.end();
  });
}

async function main() {
  console.log('🎨 Updating Project Icons\n');
  
  try {
    // Use the local icon file
    const iconPath = './assets/icon.png';
    
    if (!fs.existsSync(iconPath)) {
      console.error('❌ Local icon file not found at assets/icon.png');
      process.exit(1);
    }
    
    console.log('✅ Using local icon file: assets/icon.png');
    
    // Update all new projects
    const newProjects = uploadConfig.projects;
    
    for (const [projectKey, projectId] of Object.entries(newProjects)) {
      console.log(`\n📤 Updating ${projectKey} (${projectId})...`);
      await updateProjectIcon(projectId, iconPath);
      
      // Wait a bit between requests to be polite to the API
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    console.log('\n✅ All project icons updated successfully!');
    
  } catch (error) {
    console.error('\n❌ Failed to update icons:', error.message);
    
    // Clean up the temporary icon file if it exists
    try {
      fs.unlinkSync('./temp-icon.webp');
      fs.unlinkSync('./temp-icon.png');
    } catch (e) {
      // File might not exist, that's fine
    }
    
    process.exit(1);
  }
}

main();