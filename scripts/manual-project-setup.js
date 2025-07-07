#!/usr/bin/env node

const fs = require('fs');
const projectConfigs = JSON.parse(fs.readFileSync('config/modrinth-projects.json', 'utf8'));

console.log('🚀 Manual Modrinth Project Setup Guide\n');
console.log('Due to API limitations, please create these projects manually on Modrinth:\n');

Object.entries(projectConfigs.projects).forEach(([key, config]) => {
  console.log(`📦 ${config.name} (${key})`);
  console.log(`   Title: ${config.name}`);
  console.log(`   Slug: ${config.slug}`);
  console.log(`   Description: ${config.description}`);
  console.log(`   Categories: ${projectConfigs.metadata.categories.join(', ')}`);
  console.log(`   License: ${projectConfigs.metadata.license}`);
  console.log(`   Client Side: ${config.packType === 'client' ? 'Required' : 'Optional'}`);
  console.log(`   Server Side: ${config.packType === 'server' ? 'Required' : 'Optional'}`);
  console.log(`   Project Type: Modpack`);
  console.log(`   Status: Draft (until first version uploaded)`);
  console.log('');
});

console.log('After creating the projects manually, update this script with the project IDs:');
console.log('');

const templateConfig = {
  "clientProjectId": "YOUR_CLIENT_BASE_PROJECT_ID",
  "serverProjectId": "YOUR_SERVER_PROJECT_ID", 
  "versionType": "release",
  "changelog": "Version 1.1.0 brings exciting new world generation and exploration content...",
  "projects": {
    "client-base": "YOUR_CLIENT_BASE_PROJECT_ID",
    "client-enhanced": "YOUR_CLIENT_ENHANCED_PROJECT_ID",
    "server": "YOUR_SERVER_PROJECT_ID"
  }
};

console.log('Then update config/upload-config.json with:');
console.log(JSON.stringify(templateConfig, null, 2));