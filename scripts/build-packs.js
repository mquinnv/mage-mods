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
    
    // Get download URL for the mod
    let downloadUrl = '';
    if (!mod.manual) {
      try {
        downloadUrl = await getModDownloadUrl(mod.projectId, mod.fileId);
      } catch (error) {
        console.warn(`⚠️  Could not get download URL for ${mod.name}: ${error.message}`);
      }
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
      downloads: downloadUrl ? [downloadUrl] : [],
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
  if (fs.existsSync('assets/icon-64.png')) {
    fs.copyFileSync('assets/icon-64.png', path.join(buildDir, 'icon.png'));
  } else if (fs.existsSync('assets/icon-128.png')) {
    fs.copyFileSync('assets/icon-128.png', path.join(buildDir, 'icon.png'));
  } else if (fs.existsSync('assets/icon.png')) {
    fs.copyFileSync('assets/icon.png', path.join(buildDir, 'icon.png'));
  }
  
  // Create overrides folder with server configuration for client packs
  if (packType === 'client') {
    const overridesDir = path.join(buildDir, 'overrides');
    fs.mkdirSync(overridesDir, { recursive: true });
    
    // Create server info file
    const serverInfo = `Minecraft Mage Server Information:

Server Address: minecraft.mage.net
Server Name: Minecraft Mage Server

To connect:
1. Open Minecraft and go to Multiplayer
2. Click "Add Server"
3. Enter "Minecraft Mage Server" as the name
4. Enter "minecraft.mage.net" as the address
5. Click Done and join!

The server is compatible with this modpack.
`;
    fs.writeFileSync(path.join(overridesDir, 'SERVER_INFO.txt'), serverInfo);
    
    // Add lastServer and JVM args to options.txt
    const optionsContent = `lastServer:minecraft.mage.net
javaArgs:-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled -Dfml.readTimeout=180 -Dfml.queryResult=confirm
maxMemory:4096
`;
    fs.writeFileSync(path.join(overridesDir, 'options.txt'), optionsContent);
    
    // Create servers.dat NBT file
    const serversNbt = Buffer.from([
      0x0A, 0x00, 0x00, // TAG_Compound (root)
      0x09, 0x00, 0x07, 0x73, 0x65, 0x72, 0x76, 0x65, 0x72, 0x73, // TAG_List "servers"
      0x0A, // TAG_Compound element type
      0x00, 0x00, 0x00, 0x01, // 1 element
      // First server entry
      0x08, 0x00, 0x04, 0x6E, 0x61, 0x6D, 0x65, // TAG_String "name"
      0x00, 0x14, 0x4D, 0x69, 0x6E, 0x65, 0x63, 0x72, 0x61, 0x66, 0x74, 0x20, 0x4D, 0x61, 0x67, 0x65, 0x20, 0x53, 0x65, 0x72, 0x76, 0x65, 0x72, // "Minecraft Mage Server"
      0x08, 0x00, 0x02, 0x69, 0x70, // TAG_String "ip"
      0x00, 0x11, 0x6D, 0x69, 0x6E, 0x65, 0x63, 0x72, 0x61, 0x66, 0x74, 0x2E, 0x6D, 0x61, 0x67, 0x65, 0x2E, 0x6E, 0x65, 0x74, // "minecraft.mage.net"
      0x08, 0x00, 0x04, 0x69, 0x63, 0x6F, 0x6E, // TAG_String "icon"
      0x00, 0x00, // empty string
      0x00, // End of compound
      0x00 // End of root compound
    ]);
    
    fs.writeFileSync(path.join(overridesDir, 'servers.dat'), serversNbt);
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
  const outputPath = `build/${outputFile}`;
  console.log(`\n📦 Creating ${outputPath}...`);
  
  execSync(`cd ${buildDir} && zip -r ../${outputFile} *`, { stdio: 'inherit' });
  console.log(`✅ Created ${outputPath}`);
}

async function createPrismPack(packType, mods, index) {
  const buildDir = `build/${packType}`;
  const prismDir = `build/${packType}-prism`;
  
  // Create Prism pack directory structure
  fs.mkdirSync(prismDir, { recursive: true });
  fs.mkdirSync(path.join(prismDir, '.minecraft'), { recursive: true });
  fs.mkdirSync(path.join(prismDir, '.minecraft', 'mods'), { recursive: true });
  
  // Copy mods to .minecraft/mods
  for (const mod of mods) {
    const sourcePath = path.join(buildDir, 'mods', mod.filename);
    if (fs.existsSync(sourcePath)) {
      fs.copyFileSync(sourcePath, path.join(prismDir, '.minecraft', 'mods', mod.filename));
    }
  }
  
  // Create server info for client packs
  if (packType === 'client') {
    // Create a README with server info
    const serverInfo = `Minecraft Mage Server Information:

Server Address: minecraft.mage.net
Server Name: Minecraft Mage Server

To connect:
1. Open Minecraft and go to Multiplayer
2. Click "Add Server"
3. Enter "Minecraft Mage Server" as the name
4. Enter "minecraft.mage.net" as the address
5. Click Done and join!

The server is compatible with this modpack.
`;
    fs.writeFileSync(path.join(prismDir, '.minecraft', 'SERVER_INFO.txt'), serverInfo);
    
    // Also add to the options.txt file for direct connect memory
    const optionsContent = `lastServer:minecraft.mage.net
`;
    fs.writeFileSync(path.join(prismDir, '.minecraft', 'options.txt'), optionsContent);
    
    // Create a basic servers.dat NBT file
    // This is a minimal NBT structure for one server
    const serversNbt = Buffer.from([
      0x0A, 0x00, 0x00, // TAG_Compound (root)
      0x09, 0x00, 0x07, 0x73, 0x65, 0x72, 0x76, 0x65, 0x72, 0x73, // TAG_List "servers"
      0x0A, // TAG_Compound element type
      0x00, 0x00, 0x00, 0x01, // 1 element
      // First server entry
      0x08, 0x00, 0x04, 0x6E, 0x61, 0x6D, 0x65, // TAG_String "name"
      0x00, 0x14, 0x4D, 0x69, 0x6E, 0x65, 0x63, 0x72, 0x61, 0x66, 0x74, 0x20, 0x4D, 0x61, 0x67, 0x65, 0x20, 0x53, 0x65, 0x72, 0x76, 0x65, 0x72, // "Minecraft Mage Server"
      0x08, 0x00, 0x02, 0x69, 0x70, // TAG_String "ip"
      0x00, 0x11, 0x6D, 0x69, 0x6E, 0x65, 0x63, 0x72, 0x61, 0x66, 0x74, 0x2E, 0x6D, 0x61, 0x67, 0x65, 0x2E, 0x6E, 0x65, 0x74, // "minecraft.mage.net"
      0x08, 0x00, 0x04, 0x69, 0x63, 0x6F, 0x6E, // TAG_String "icon"
      0x00, 0x00, // empty string
      0x00, // End of compound
      0x00 // End of root compound
    ]);
    
    fs.writeFileSync(path.join(prismDir, '.minecraft', 'servers.dat'), serversNbt);
  }
  
  // Copy icon to root
  if (fs.existsSync('assets/icon-64.png')) {
    fs.copyFileSync('assets/icon-64.png', path.join(prismDir, 'icon.png'));
  } else if (fs.existsSync('assets/icon-128.png')) {
    fs.copyFileSync('assets/icon-128.png', path.join(prismDir, 'icon.png'));
  } else if (fs.existsSync('assets/icon.png')) {
    fs.copyFileSync('assets/icon.png', path.join(prismDir, 'icon.png'));
  }
  
  // Create mmc-pack.json
  const mmcPack = {
    components: [
      {
        uid: "net.minecraft",
        version: packInfo.minecraft
      },
      {
        uid: "net.fabricmc.fabric-loader",
        version: packInfo.fabric
      }
    ],
    formatVersion: 1
  };
  
  fs.writeFileSync(
    path.join(prismDir, 'mmc-pack.json'),
    JSON.stringify(mmcPack, null, 2)
  );
  
  // Create instance.cfg
  const instanceCfg = `InstanceType=OneSix
name=${packInfo.name} ${packType.charAt(0).toUpperCase() + packType.slice(1)}
iconKey=icon
JavaVersion=17
MinMemAlloc=512
MaxMemAlloc=4096
JvmArgs=-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M
`;
  
  fs.writeFileSync(path.join(prismDir, 'instance.cfg'), instanceCfg);
  
  // Create the zip
  const outputFile = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${packType}-${packInfo.version}-prism.zip`;
  const outputPath = `build/${outputFile}`;
  console.log(`\n📦 Creating ${outputPath}...`);
  
  execSync(`cd ${prismDir} && zip -r ../${outputFile} . -x "*.DS_Store"`, { stdio: 'inherit' });
  console.log(`✅ Created ${outputPath}`);
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
  
  // Create Prism packs
  console.log('\n=== Generating Prism Packs ===');
  await createPrismPack('client', clientMods.mods, clientIndex);
  await createPrismPack('server', serverMods.mods, serverIndex);
  
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