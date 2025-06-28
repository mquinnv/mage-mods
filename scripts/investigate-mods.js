#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-mod-investigator/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

const failingMods = [
  { name: 'FerriteCore', projectId: 'mQqR7dCM' },
  { name: 'LazyDFU', projectId: 'lzChZsVZ' },
  { name: 'Memory Leak Fix', projectId: 'gKPYqLZv' },
  { name: 'Jade', projectId: 'zC8G8uC6' },
  { name: 'EMI', projectId: 'Rc7dENYv' },
  { name: 'Inventory Profiles Next', projectId: 'UOqAfnXU' },
  { name: 'Starlight', projectId: '3wYzX1gZ' },
  { name: 'ServerCore', projectId: 'KoBvN3wO' },
  { name: 'Spark', projectId: '1fRufpZL' },
  { name: 'Carpet', projectId: 'DmjA0xBJ' },
  { name: 'Carpet Extra', projectId: 'uBSk9WkY' },
  { name: 'Essential Commands', projectId: 'OpTae4nq' },
  { name: 'LuckPerms', projectId: 'uQmGfJNR' },
  { name: 'BlueMap', projectId: 'QwQv7MqN' }
];

async function investigateMod(mod) {
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }

  console.log(`\n🔍 Investigating ${mod.name} (${mod.projectId})`);
  
  try {
    // First, check if the project exists
    const projectUrl = `${MODRINTH_API}/project/${mod.projectId}`;
    const projectResponse = await axios.get(projectUrl, { headers });
    const project = projectResponse.data;
    
    console.log(`✅ Project exists: ${project.title}`);
    console.log(`   Description: ${project.description.substring(0, 100)}...`);
    console.log(`   Categories: ${project.categories.join(', ')}`);
    console.log(`   Client/Server: ${project.client_side}/${project.server_side}`);
    
    // Check for 1.20.1 versions
    const versionsUrl = `${MODRINTH_API}/project/${mod.projectId}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
    const versionsResponse = await axios.get(versionsUrl, { headers });
    const versions = versionsResponse.data;
    
    if (versions.length === 0) {
      console.log(`❌ No 1.20.1 Fabric versions found`);
      
      // Check what versions are available
      const allVersionsUrl = `${MODRINTH_API}/project/${mod.projectId}/version?loaders=["fabric"]`;
      const allVersionsResponse = await axios.get(allVersionsUrl, { headers });
      const allVersions = allVersionsResponse.data;
      
      if (allVersions.length > 0) {
        const gameVersions = [...new Set(allVersions.flatMap(v => v.game_versions))];
        console.log(`   Available game versions: ${gameVersions.sort().join(', ')}`);
        
        // Try 1.20 versions
        const v120Url = `${MODRINTH_API}/project/${mod.projectId}/version?game_versions=["1.20"]&loaders=["fabric"]`;
        try {
          const v120Response = await axios.get(v120Url, { headers });
          if (v120Response.data.length > 0) {
            const latest120 = v120Response.data[0];
            console.log(`💡 Found 1.20 version: ${latest120.version_number} (${latest120.id})`);
            console.log(`   File: ${latest120.files[0].filename}`);
          }
        } catch (e) {
          console.log(`   No 1.20 versions either`);
        }
      } else {
        console.log(`   No Fabric versions found at all`);
      }
    } else {
      const latest = versions[0];
      console.log(`✅ Found 1.20.1 version: ${latest.version_number}`);
      console.log(`   Version ID: ${latest.id}`);
      console.log(`   File: ${latest.files[0].filename}`);
      console.log(`   Published: ${new Date(latest.date_published).toLocaleDateString()}`);
    }
    
  } catch (error) {
    if (error.response?.status === 404) {
      console.log(`❌ Project not found - invalid project ID`);
    } else {
      console.log(`❌ Error: ${error.message}`);
    }
  }
}

async function main() {
  console.log('🕵️ Investigating mods with invalid version IDs\n');
  
  for (const mod of failingMods) {
    await investigateMod(mod);
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  
  console.log('\n✅ Investigation complete!');
}

if (require.main === module) {
  main().catch(console.error);
}