#!/usr/bin/env node

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

async function uploadVersion(projectId, packType, variant = '') {
  const variantSuffix = variant ? `-${variant}` : '';
  const fileName = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${packType}-${packInfo.version}${variantSuffix}.mrpack`;
  const filePath = path.join(process.cwd(), 'build', fileName);
  
  if (!fs.existsSync(filePath)) {
    throw new Error(`Pack file not found: ${fileName}. Run 'npm run build' first.`);
  }
  
  console.log(`📤 Uploading ${fileName} to project ${projectId}...`);
  
  // Prepare version data
  const variantDisplayName = variant ? ` - ${variant.charAt(0).toUpperCase() + variant.slice(1)}` : '';
  const versionData = {
    name: `${packInfo.version} (${packType}${variantDisplayName})`,
    version_number: variant ? `${packInfo.version}-${variant}` : packInfo.version,
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
          console.log(`✅ Successfully uploaded version ${version.version_number}`);
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
  if (!uploadConfig.clientProjectId || !uploadConfig.serverProjectId) {
    console.error('❌ Missing project IDs in config/upload-config.json');
    console.error('   Please add clientProjectId and serverProjectId');
    process.exit(1);
  }
  
  const args = process.argv.slice(2);
  const uploadClient = !args.length || args.includes('--client');
  const uploadServer = !args.length || args.includes('--server');
  const uploadEnhanced = !args.length || args.includes('--enhanced');
  
  try {
    if (uploadClient) {
      await uploadVersion(uploadConfig.clientProjectId, 'client');
    }
    
    if (uploadEnhanced) {
      await uploadVersion(uploadConfig.clientProjectId, 'client', 'enhanced');
    }
    
    if (uploadServer) {
      await uploadVersion(uploadConfig.serverProjectId, 'server');
    }
    
    console.log('\n✅ Upload complete!');
  } catch (error) {
    console.error('\n❌ Upload failed:', error.message);
    process.exit(1);
  }
}

main();