#!/usr/bin/env bun

// Load environment variables from .env file
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const https = require('https');
const axios = require('axios');
const FormData = require('form-data');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.error('❌ MODRINTH_TOKEN is required for uploading!');
  console.error('   Set it in your environment or .env file');
  process.exit(1);
}

// Load configurations
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));
const uploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));
const projectConfigs = JSON.parse(fs.readFileSync('config/modrinth-projects.json', 'utf8'));

async function uploadVersion(projectKey, projectId) {
  const config = projectConfigs.projects[projectKey];
  if (!config) {
    throw new Error(`Unknown project key: ${projectKey}`);
  }
  
  const variantSuffix = config.variant ? `-${config.variant}` : '';
  const fileName = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${config.packType}-${packInfo.version}${variantSuffix}.mrpack`;
  const filePath = path.join(process.cwd(), 'build', fileName);
  
  if (!fs.existsSync(filePath)) {
    throw new Error(`Pack file not found: ${fileName}. Run 'npm run build' first.`);
  }
  
  console.log(`📤 Uploading ${fileName} to ${config.name} (${projectId})...`);
  
  // Prepare version data
  const versionData = {
    name: `${packInfo.version}`,
    version_number: packInfo.version,
    changelog: uploadConfig.changelog || `Release ${packInfo.version}`,
    dependencies: [],
    game_versions: [packInfo.minecraft],
    version_type: uploadConfig.versionType || 'release',
    loaders: ['fabric'],
    featured: false,
    status: 'listed',
    requested_status: 'listed',
    project_id: projectId,
    file_parts: ['file'],
    primary_file: 'file'
  };
  
  // Create form data
  const form = new FormData();
  form.append('data', JSON.stringify(versionData));
  form.append('file', fs.createReadStream(filePath), {
    filename: fileName,
    contentType: 'application/x-modrinth-modpack+zip'
  });
  
  return new Promise((resolve, reject) => {
    const options = {
      method: 'POST',
      hostname: 'api.modrinth.com',
      path: '/v2/version',
      headers: {
        'Authorization': `Bearer ${MODRINTH_TOKEN}`,
        'User-Agent': 'minecraft-mage-pack-builder/1.0.0',
        ...form.getHeaders()
      }
    };
    
    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200 || res.statusCode === 201) {
          const version = JSON.parse(data);
          console.log(`✅ Successfully uploaded version ${version.version_number} to ${config.name}`);
          console.log(`   View at: https://modrinth.com/project/${projectId}/version/${version.id}`);
          resolve(version);
        } else {
          console.error(`❌ Upload failed with status ${res.statusCode}`);
          console.error(`   Response: ${data}`);
          reject(new Error(`Upload failed: ${res.statusCode}`));
        }
      });
    });
    
    req.on('error', reject);
    form.pipe(req);
  });
}

async function main() {
  console.log('🚀 Modrinth Pack Uploader\n');
  
  // Check for required configuration
  if (!uploadConfig.projects) {
    console.error('❌ Missing project configurations in config/upload-config.json');
    console.error('   Please run "node scripts/create-modrinth-projects.js" first');
    process.exit(1);
  }
  
  const args = process.argv.slice(2);
  const projectsToUpload = [];
  
  if (args.length === 0) {
    // Upload all projects if no specific arguments
    projectsToUpload.push(...Object.keys(uploadConfig.projects));
  } else {
    // Handle legacy and new argument formats
    if (args.includes('--client')) {
      projectsToUpload.push('client-base');
    }
    if (args.includes('--enhanced')) {
      projectsToUpload.push('client-enhanced');
    }
    if (args.includes('--server')) {
      projectsToUpload.push('server');
    }
    
    // Handle direct project keys
    args.forEach(arg => {
      if (!arg.startsWith('--') && uploadConfig.projects[arg]) {
        projectsToUpload.push(arg);
      }
    });
  }
  
  if (projectsToUpload.length === 0) {
    console.error('❌ No valid projects specified');
    console.error('   Available projects:', Object.keys(uploadConfig.projects).join(', '));
    console.error('   Usage: bun scripts/upload-packs.js [--client] [--enhanced] [--server] [project-key]');
    process.exit(1);
  }
  
  try {
    for (const projectKey of projectsToUpload) {
      const projectId = uploadConfig.projects[projectKey];
      if (!projectId) {
        console.error(`❌ No project ID found for ${projectKey}`);
        continue;
      }
      
      await uploadVersion(projectKey, projectId);
      
      // Wait a bit between uploads to be polite to the API
      if (projectsToUpload.length > 1) {
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
    }
    
    console.log('\n✅ Upload complete!');
  } catch (error) {
    console.error('\n❌ Upload failed:', error.message);
    process.exit(1);
  }
}

main();