#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const axios = require('axios');
const { execSync } = require('child_process');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const USER_AGENT = 'minecraft-mage-pack-builder/1.0.0';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.warn('⚠️  No MODRINTH_TOKEN found in environment variables.');
  console.warn('   API requests will be rate-limited. Set MODRINTH_TOKEN for better access.\n');
}

// Load configurations
const packInfo = JSON.parse(fs.readFileSync('config/pack-info.json', 'utf8'));
const clientMods = JSON.parse(fs.readFileSync('config/mods-client.json', 'utf8'));
const serverMods = JSON.parse(fs.readFileSync('config/mods-server.json', 'utf8'));

// Ensure directories exist
const dirs = ['build/client/mods', 'build/server/mods', 'src/client', 'src/server'];
dirs.forEach(dir => fs.mkdirSync(dir, { recursive: true }));

function calculateSHA1(filePath) {
  const fileBuffer = fs.readFileSync(filePath);
  const hash = crypto.createHash('sha1');
  hash.update(fileBuffer);
  return hash.digest('hex');
}

function getFileSize(filePath) {
  return fs.statSync(filePath).size;
}

async function downloadFile(url, destPath) {
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }
  
  try {
    const response = await axios({
      url,
      method: 'GET',
      headers,
      responseType: 'stream'
    });
    
    const writer = fs.createWriteStream(destPath);
    response.data.pipe(writer);
    
    return new Promise((resolve, reject) => {
      writer.on('finish', resolve);
      writer.on('error', reject);
    });
  } catch (error) {
    fs.unlink(destPath, () => {});
    throw error;
  }
}

async function getModDownloadUrl(projectId, fileId) {
  const url = `${MODRINTH_API}/project/${projectId}/version/${fileId}`;
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }
  
  try {
    const response = await axios.get(url, { headers });
    const versionData = response.data;
    const primaryFile = versionData.files.find(f => f.primary) || versionData.files[0];
    return primaryFile.url;
  } catch (error) {
    if (error.response) {
      throw new Error(`API Error ${error.response.status}: ${error.response.statusText} for ${projectId}/${fileId}`);
    } else {
      throw new Error(`Network error for ${projectId}: ${error.message}`);
    }
  }
}

async function downloadMod(mod, destDir) {
  const destPath = path.join(destDir, mod.filename);
  
  if (mod.manual) {
    console.log(`⚠️  Manual mod: ${mod.name} - Please add ${mod.filename} to ${destDir}`);
    return null;
  }
  
  if (fs.existsSync(destPath)) {
    console.log(`✓ ${mod.name} already downloaded`);
    return destPath;
  }
  
  console.log(`📥 Downloading ${mod.name}...`);
  try {
    const downloadUrl = await getModDownloadUrl(mod.projectId, mod.fileId);
    await downloadFile(downloadUrl, destPath);
    console.log(`✓ Downloaded ${mod.name}`);
    return destPath;
  } catch (error) {
    console.error(`❌ Failed to download ${mod.name}:`, error.message);
    return null;
  }
}

async function buildModrinthIndex(mods, packType, downloadedFiles) {
  const files = [];
  
  for (const mod of mods) {
    const filePath = downloadedFiles[mod.filename];
    if (!filePath || !fs.existsSync(filePath)) {
      if (!mod.manual) {
        console.warn(`⚠️  Skipping ${mod.name} - file not found`);
      }
      continue;
    }
    
    files.push({
      path: `mods/${mod.filename}`,
      hashes: {
        sha1: calculateSHA1(filePath),
        sha512: calculateSHA512(filePath)
      },
      env: {
        client: mod.side === 'client' || mod.side === 'both' ? 'required' : 'unsupported',
        server: mod.side === 'server' || mod.side === 'both' ? 'required' : 'unsupported'
      },
      downloads: [],
      fileSize: getFileSize(filePath)
    });
  }
  
  return {
    formatVersion: 1,
    game: 'minecraft',
    versionId: `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${packInfo.version}-${packType}`,
    name: `${packInfo.name} ${packType.charAt(0).toUpperCase() + packType.slice(1)}`,
    summary: packInfo.description[packType],
    files,
    dependencies: {
      minecraft: packInfo.minecraft,
      'fabric-loader': packInfo.fabric
    }
  };
}

function calculateSHA512(filePath) {
  const fileBuffer = fs.readFileSync(filePath);
  const hash = crypto.createHash('sha512');
  hash.update(fileBuffer);
  return hash.digest('hex');
}

async function createMrpack(packType, mods, index) {
  const buildDir = `build/${packType}`;
  
  // Write modrinth.index.json
  fs.writeFileSync(
    path.join(buildDir, 'modrinth.index.json'),
    JSON.stringify(index, null, 2)
  );
  
  // Copy icon
  if (fs.existsSync('assets/icon.png')) {
    fs.copyFileSync('assets/icon.png', path.join(buildDir, 'icon.png'));
  }
  
  // Create README
  fs.writeFileSync(
    path.join(buildDir, 'README.txt'),
    `${packInfo.name} - ${packType.charAt(0).toUpperCase() + packType.slice(1)} Pack\n` +
    `Version: ${packInfo.version}\n` +
    `Minecraft: ${packInfo.minecraft}\n` +
    `Fabric Loader: ${packInfo.fabric}\n\n` +
    packInfo.description[packType]
  );
  
  // Create the mrpack
  const outputFile = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${packType}-${packInfo.version}.mrpack`;
  console.log(`\n📦 Creating ${outputFile}...`);
  
  execSync(`cd ${buildDir} && zip -r ../../${outputFile} *`, { stdio: 'inherit' });
  console.log(`✅ Created ${outputFile}`);
}

async function main() {
  console.log('🚀 Minecraft Mage Pack Builder\n');
  
  // Download client mods
  console.log('=== Building Client Pack ===');
  const clientDownloaded = {};
  for (const mod of clientMods.mods) {
    const filePath = await downloadMod(mod, 'build/client/mods');
    if (filePath) {
      clientDownloaded[mod.filename] = filePath;
    }
  }
  
  // Download server mods
  console.log('\n=== Building Server Pack ===');
  const serverDownloaded = {};
  for (const mod of serverMods.mods) {
    const filePath = await downloadMod(mod, 'build/server/mods');
    if (filePath) {
      serverDownloaded[mod.filename] = filePath;
    }
  }
  
  // Build indexes
  console.log('\n=== Generating Modrinth Indexes ===');
  const clientIndex = await buildModrinthIndex(clientMods.mods, 'client', clientDownloaded);
  const serverIndex = await buildModrinthIndex(serverMods.mods, 'server', serverDownloaded);
  
  // Create mrpacks
  await createMrpack('client', clientMods.mods, clientIndex);
  await createMrpack('server', serverMods.mods, serverIndex);
  
  console.log('\n✅ Build complete!');
  
  // List manual mods that need to be added
  const manualMods = [
    ...clientMods.mods.filter(m => m.manual),
    ...serverMods.mods.filter(m => m.manual)
  ];
  
  if (manualMods.length > 0) {
    console.log('\n⚠️  Manual mods need to be added:');
    manualMods.forEach(mod => {
      console.log(`  - ${mod.name}: Add to appropriate mods directory`);
    });
  }
}

main().catch(console.error);