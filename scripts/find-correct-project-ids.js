#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-mod-finder/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

// Known mod slugs/names to search for
const modNames = [
  'ferrite-core',
  'lazydfu', 
  'memoryleakfix',
  'jade',
  'emi',
  'inventory-profiles-next',
  'starlight',
  'servercore',
  'spark',
  'carpet',
  'carpet-extra',
  'essential-commands',
  'luckperms',
  'bluemap'
];

async function searchForMod(modName) {
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }

  console.log(`\n🔍 Searching for "${modName}"`);
  
  try {
    // Try direct project lookup first (most efficient)
    try {
      const directUrl = `${MODRINTH_API}/project/${modName}`;
      const response = await axios.get(directUrl, { headers });
      const project = response.data;
      
      console.log(`✅ Found via direct lookup: ${project.title}`);
      console.log(`   Project ID: ${project.id}`);
      console.log(`   Slug: ${project.slug}`);
      console.log(`   Description: ${project.description.substring(0, 80)}...`);
      
      // Check for 1.20.1 versions
      const versionsUrl = `${MODRINTH_API}/project/${project.id}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
      const versionsResponse = await axios.get(versionsUrl, { headers });
      
      if (versionsResponse.data.length > 0) {
        const latest = versionsResponse.data[0];
        console.log(`   ✅ Has 1.20.1 version: ${latest.version_number} (${latest.id})`);
        console.log(`   File: ${latest.files[0].filename}`);
        return {
          name: project.title,
          projectId: project.id,
          slug: project.slug,
          fileId: latest.id,
          filename: latest.files[0].filename,
          version: latest.version_number
        };
      } else {
        console.log(`   ❌ No 1.20.1 versions available`);
        return { name: project.title, projectId: project.id, slug: project.slug, no121: true };
      }
      
    } catch (directError) {
      // If direct lookup fails, try search
      const searchUrl = `${MODRINTH_API}/search?query=${encodeURIComponent(modName)}&facets=[["categories:fabric"],["versions:1.20.1"]]`;
      const searchResponse = await axios.get(searchUrl, { headers });
      const results = searchResponse.data.hits;
      
      if (results.length === 0) {
        console.log(`❌ No search results found`);
        return null;
      }
      
      const match = results[0]; // Take best match
      console.log(`✅ Found via search: ${match.title}`);
      console.log(`   Project ID: ${match.project_id}`);
      console.log(`   Slug: ${match.slug}`);
      console.log(`   Downloads: ${match.downloads.toLocaleString()}`);
      
      // Get latest version for this project
      const versionsUrl = `${MODRINTH_API}/project/${match.project_id}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
      const versionsResponse = await axios.get(versionsUrl, { headers });
      
      if (versionsResponse.data.length > 0) {
        const latest = versionsResponse.data[0];
        console.log(`   ✅ Latest 1.20.1 version: ${latest.version_number} (${latest.id})`);
        return {
          name: match.title,
          projectId: match.project_id,
          slug: match.slug,
          fileId: latest.id,
          filename: latest.files[0].filename,
          version: latest.version_number
        };
      }
    }
    
  } catch (error) {
    console.log(`❌ Error searching for ${modName}: ${error.message}`);
    return null;
  }
}

async function main() {
  console.log('🔍 Finding correct project IDs for missing mods\n');
  
  const results = [];
  
  for (const modName of modNames) {
    const result = await searchForMod(modName);
    if (result) {
      results.push(result);
    }
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  
  console.log('\n📋 SUMMARY - Copy these into your config files:');
  console.log('='.repeat(60));
  
  results.forEach(mod => {
    if (!mod.no121) {
      console.log(`{`);
      console.log(`  "name": "${mod.name}",`);
      console.log(`  "projectId": "${mod.projectId}",`);
      console.log(`  "fileId": "${mod.fileId}",`);
      console.log(`  "filename": "${mod.filename}",`);
      console.log(`  "side": "both"`);
      console.log(`},`);
      console.log('');
    }
  });
  
  console.log('\n✅ Search complete!');
}

if (require.main === module) {
  main().catch(console.error);
}