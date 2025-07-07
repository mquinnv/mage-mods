#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const https = require('https');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.error('❌ MODRINTH_TOKEN is required!');
  console.error('   Set it in your environment or .env file');
  process.exit(1);
}

// Old project IDs to delete
const OLD_PROJECTS = [
  { id: 'L1ET1T4t', name: 'Original Client Project' },
  { id: 'LHpIsjjB', name: 'Original Server Project' }
];

async function deleteProject(projectId, projectName) {
  console.log(`🗑️  Deleting ${projectName} (${projectId})...`);
  
  return new Promise((resolve, reject) => {
    const options = {
      method: 'DELETE',
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
        if (res.statusCode === 204 || res.statusCode === 200) {
          console.log(`✅ Successfully deleted ${projectName}`);
          resolve();
        } else {
          console.error(`❌ Failed to delete ${projectName}`);
          console.error(`   Status: ${res.statusCode}`);
          console.error(`   Response: ${data}`);
          reject(new Error(`Failed to delete project: ${res.statusCode}`));
        }
      });
    });

    req.on('error', reject);
    req.end();
  });
}

async function main() {
  console.log('🗑️  Deleting Old Modrinth Projects\n');
  
  console.log('⚠️  WARNING: This will permanently delete the following projects:');
  OLD_PROJECTS.forEach(project => {
    console.log(`   - ${project.name} (${project.id})`);
  });
  
  console.log('\n🔄 Proceeding with deletion in 3 seconds...');
  await new Promise(resolve => setTimeout(resolve, 3000));
  
  try {
    for (const project of OLD_PROJECTS) {
      await deleteProject(project.id, project.name);
      
      // Wait a bit between requests to be polite to the API
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    console.log('\n✅ All old projects deleted successfully!');
    console.log('\n📝 The new separate projects are now the only ones remaining:');
    console.log('   - Mage.net Client Base (Tnp9M66F)');
    console.log('   - Mage.net Client Enhanced (mbNTw2kH)');
    console.log('   - Mage.net Server (xnQPgl1j)');
    
  } catch (error) {
    console.error('\n❌ Failed to delete projects:', error.message);
    process.exit(1);
  }
}

main();