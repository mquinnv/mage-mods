#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const axios = require('axios');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-texture-investigator/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

const modsToInvestigate = [
  { 
    name: 'BountifulFares', 
    projectId: 'YgrSDjlb',
    currentVersion: '1.2.1-1.20.1',
    missingTextures: [
      'bountifulfares:block/scorchkin_bottom',
      'bountifulfares:block/scorchkin_stem_0',
      'bountifulfares:block/scorchkin_stem_1', 
      'bountifulfares:block/scorchkin_stem_2',
      'bountifulfares:block/scorchkin_stem_3'
    ]
  },
  { 
    name: 'Supplementaries', 
    projectId: 'fFEIiSDQ',
    currentVersion: '3.1.31',
    missingTextures: [
      'supplementaries:item/globes/globe_arzinist',
      'supplementaries:item/globes/globe_looxond'
    ]
  }
];

async function investigateModVersions(mod) {
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }

  console.log(`\n🔍 Investigating ${mod.name} (${mod.projectId})`);
  console.log(`Current version: ${mod.currentVersion}`);
  console.log(`Missing textures: ${mod.missingTextures.join(', ')}`);
  
  try {
    // Get project information
    const projectUrl = `${MODRINTH_API}/project/${mod.projectId}`;
    const projectResponse = await axios.get(projectUrl, { headers });
    const project = projectResponse.data;
    
    console.log(`✅ Project: ${project.title}`);
    console.log(`   Description: ${project.description.substring(0, 150)}...`);
    
    // Get recent versions for 1.20.1
    const versionsUrl = `${MODRINTH_API}/project/${mod.projectId}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
    const versionsResponse = await axios.get(versionsUrl, { headers });
    const versions = versionsResponse.data;
    
    if (versions.length === 0) {
      console.log(`❌ No 1.20.1 Fabric versions found`);
      return;
    }
    
    console.log(`\n📦 Available 1.20.1 versions:`);
    versions.slice(0, 5).forEach((version, index) => {
      const isCurrent = version.version_number === mod.currentVersion;
      const marker = isCurrent ? '👉 ' : '   ';
      console.log(`${marker}${version.version_number} (${version.id}) - ${new Date(version.date_published).toLocaleDateString()}`);
      if (version.changelog) {
        const changelog = version.changelog.substring(0, 200).replace(/\n/g, ' ');
        console.log(`     Changelog: ${changelog}...`);
      }
    });
    
    // Check if there are newer versions than current
    const currentVersionIndex = versions.findIndex(v => v.version_number === mod.currentVersion);
    if (currentVersionIndex > 0) {
      console.log(`\n🆕 There are ${currentVersionIndex} newer versions available!`);
      console.log(`   Latest: ${versions[0].version_number} (${new Date(versions[0].date_published).toLocaleDateString()})`);
      
      // Show changelog of latest version
      if (versions[0].changelog) {
        console.log(`   Latest changelog: ${versions[0].changelog.substring(0, 300)}...`);
      }
    } else if (currentVersionIndex === 0) {
      console.log(`\n✅ Using the latest version!`);
    } else {
      console.log(`\n⚠️  Current version not found in available versions list`);
    }
    
  } catch (error) {
    if (error.response?.status === 404) {
      console.log(`❌ Project not found - invalid project ID`);
    } else {
      console.log(`❌ Error: ${error.message}`);
      console.log(`   Status: ${error.response?.status}`);
    }
  }
}

async function checkKnownIssues(mod) {
  console.log(`\n🐛 Checking for known issues with ${mod.name}...`);
  
  // Check GitHub issues if possible
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }

  try {
    const projectUrl = `${MODRINTH_API}/project/${mod.projectId}`;
    const projectResponse = await axios.get(projectUrl, { headers });
    const project = projectResponse.data;
    
    if (project.issues_url) {
      console.log(`   Issues URL: ${project.issues_url}`);
    }
    if (project.source_url) {
      console.log(`   Source URL: ${project.source_url}`);
    }
    if (project.wiki_url) {
      console.log(`   Wiki URL: ${project.wiki_url}`);
    }
    
  } catch (error) {
    console.log(`   Could not fetch project details`);
  }
}

async function main() {
  console.log('🕵️ Investigating missing texture issues\n');
  console.log('This will check for:');
  console.log('1. Current mod versions vs latest available');
  console.log('2. Recent changelogs mentioning texture fixes');
  console.log('3. Known issues and resource links');
  
  for (const mod of modsToInvestigate) {
    await investigateModVersions(mod);
    await checkKnownIssues(mod);
    console.log('\n' + '='.repeat(60));
    
    // Rate limiting
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  
  console.log('\n📋 SUMMARY:');
  console.log('The missing textures could be due to:');
  console.log('1. 🆕 Outdated mod versions - newer versions may have fixed these issues');
  console.log('2. 🔧 Missing resource pack or configuration');
  console.log('3. 🐛 Actual missing assets in the mod (known bug)');
  console.log('4. ⚙️  Mod compatibility issues with other mods');
  console.log('\n✅ Investigation complete!');
}

if (require.main === module) {
  main().catch(console.error);
}