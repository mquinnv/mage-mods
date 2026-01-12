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
  console.error('❌ MODRINTH_TOKEN is required for creating projects!');
  console.error('   Set it in your environment or .env file');
  process.exit(1);
}

// Load project configurations
const projectConfigs = JSON.parse(fs.readFileSync('config/modrinth-projects.json', 'utf8'));
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));
const uploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));

async function createProject(projectKey, config) {
  console.log(`📦 Creating project: ${config.name}...`);
  
  // Check if the pack file exists
  const variantSuffix = config.variant ? `-${config.variant}` : '';
  const fileName = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${config.packType}-${packInfo.version}${variantSuffix}.mrpack`;
  const filePath = path.join(process.cwd(), 'build', fileName);
  
  if (!fs.existsSync(filePath)) {
    throw new Error(`Pack file not found: ${fileName}. Run 'npm run build' first.`);
  }
  
  // Prepare initial version data
  const initialVersionData = {
    name: packInfo.version,
    version_number: packInfo.version,
    changelog: uploadConfig.changelog || `Initial release ${packInfo.version}`,
    dependencies: [],
    game_versions: [packInfo.minecraft],
    version_type: uploadConfig.versionType || 'release',
    loaders: ['fabric'],
    featured: false,
    status: 'listed',
    requested_status: 'listed',
    file_parts: ['file'],
    primary_file: 'file'
  };
  
  const projectData = {
    title: config.name,
    slug: config.slug,
    description: config.description,
    body: `# ${config.name}\n\n${config.description}\n\nThis modpack is part of the minecraft.mage.net server ecosystem.`,
    categories: projectConfigs.metadata.categories,
    client_side: config.packType === 'client' ? 'required' : 'optional',
    server_side: config.packType === 'server' ? 'required' : 'optional',
    license_id: projectConfigs.metadata.license,
    project_type: 'modpack',
    initial_versions: [initialVersionData]
  };

  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append('data', JSON.stringify(projectData));
    form.append('file', fs.createReadStream(filePath), {
      filename: fileName,
      contentType: 'application/x-modrinth-modpack+zip'
    });
    
    const options = {
      method: 'POST',
      hostname: 'api.modrinth.com',
      path: '/v2/project',
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
          const project = JSON.parse(data);
          console.log(`✅ Created project: ${project.title}`);
          console.log(`   Project ID: ${project.id}`);
          console.log(`   URL: https://modrinth.com/modpack/${project.slug}`);
          console.log(`   Initial version: ${packInfo.version} uploaded`);
          resolve({ key: projectKey, project });
        } else {
          console.error(`❌ Failed to create project ${config.name}`);
          console.error(`   Status: ${res.statusCode}`);
          console.error(`   Response: ${data}`);
          reject(new Error(`Failed to create project: ${res.statusCode}`));
        }
      });
    });

    req.on('error', reject);
    form.pipe(req);
  });
}

async function main() {
  console.log('🚀 Creating Modrinth Projects with Initial Versions\n');
  
  // Check if build directory exists and has pack files
  const buildDir = path.join(process.cwd(), 'build');
  if (!fs.existsSync(buildDir)) {
    console.error('❌ Build directory not found. Run "npm run build" first.');
    process.exit(1);
  }
  
  // Check if all required pack files exist
  const projects = projectConfigs.projects;
  const missingFiles = [];
  
  for (const [projectKey, config] of Object.entries(projects)) {
    const variantSuffix = config.variant ? `-${config.variant}` : '';
    const fileName = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${config.packType}-${packInfo.version}${variantSuffix}.mrpack`;
    const filePath = path.join(buildDir, fileName);
    
    if (!fs.existsSync(filePath)) {
      missingFiles.push(fileName);
    }
  }
  
  if (missingFiles.length > 0) {
    console.error('❌ Missing pack files:');
    missingFiles.forEach(file => console.error(`   ${file}`));
    console.error('\n   Run "npm run build" first to create all pack files.');
    process.exit(1);
  }
  
  console.log('✅ All required pack files found. Creating projects...\n');
  
  const createdProjects = {};
  
  try {
    for (const [projectKey, config] of Object.entries(projects)) {
      const result = await createProject(projectKey, config);
      createdProjects[result.key] = result.project.id;
      
      // Wait a bit between requests to be polite to the API
      await new Promise(resolve => setTimeout(resolve, 3000));
    }
    
    // Update upload-config.json with new project IDs
    const currentUploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));
    currentUploadConfig.projects = createdProjects;
    
    // Keep the old format for backward compatibility during transition
    currentUploadConfig.clientProjectId = createdProjects['client-base'];
    currentUploadConfig.serverProjectId = createdProjects['server'];
    
    fs.writeFileSync('config/upload-config.json', JSON.stringify(currentUploadConfig, null, 2));
    
    console.log('\n✅ All projects created successfully with initial versions!');
    console.log('\nProject IDs:');
    Object.entries(createdProjects).forEach(([key, id]) => {
      console.log(`  ${key}: ${id}`);
    });
    
    console.log('\n📝 Updated upload-config.json with new project IDs');
    console.log('\n🎉 Projects are ready! Initial versions have been uploaded.');
    
  } catch (error) {
    console.error('\n❌ Failed to create projects:', error.message);
    process.exit(1);
  }
}

main();