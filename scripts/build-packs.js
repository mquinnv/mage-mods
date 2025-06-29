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
const resourcePacks = JSON.parse(fs.readFileSync('config/resource-packs.json', 'utf8'));
const shaderPacks = JSON.parse(fs.readFileSync('config/shader-packs.json', 'utf8'));

function cleanupOldAssets() {
  console.log('🧹 Cleaning up old assets...');
  
  const currentVersion = packInfo.version;
  const buildDir = 'build';
  
  if (!fs.existsSync(buildDir)) {
    console.log('  No build directory found, skipping cleanup');
    return;
  }
  
  // Remove old .mrpack and .zip files, keeping only current version
  const files = fs.readdirSync(buildDir);
  let cleanedCount = 0;
  
  files.forEach(file => {
    const filePath = path.join(buildDir, file);
    const stat = fs.lstatSync(filePath);
    
    // Remove old symlinks
    if (stat.isSymbolicLink()) {
      console.log(`  Removing symlink: ${file}`);
      fs.unlinkSync(filePath);
      cleanedCount++;
    }
    // Remove old version files
    else if (file.endsWith('.mrpack') || file.endsWith('.zip')) {
      // Keep current version and "latest" files
      if (!file.includes(currentVersion) && !file.includes('latest')) {
        console.log(`  Removing old version file: ${file}`);
        fs.unlinkSync(filePath);
        cleanedCount++;
      }
    }
  });
  
  // Clean up old build subdirectories that might be left over
  const potentialDirs = ['client-prism', 'server-prism'];
  potentialDirs.forEach(dir => {
    const dirPath = path.join(buildDir, dir);
    if (fs.existsSync(dirPath)) {
      console.log(`  Removing old build directory: ${dir}`);
      fs.rmSync(dirPath, { recursive: true, force: true });
      cleanedCount++;
    }
  });
  
  if (cleanedCount > 0) {
    console.log(`✅ Cleaned up ${cleanedCount} old assets`);
  } else {
    console.log('  No old assets to clean up');
  }
}

// Ensure directories exist
const dirs = [
  'build/client/mods', 'build/server/mods', 
  'build/client/resourcepacks', 'build/client/shaderpacks',
  'src/client', 'src/server'
];
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

async function getLatestModVersion(projectId) {
  const url = `${MODRINTH_API}/project/${projectId}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }
  
  try {
    const response = await axios.get(url, { headers });
    
    if (!response.data || response.data.length === 0) {
      throw new Error('No compatible versions found for Minecraft 1.20.1 Fabric');
    }
    
    const latestVersion = response.data[0];
    const primaryFile = latestVersion.files.find(f => f.primary) || latestVersion.files[0];
    
    return {
      filename: primaryFile.filename,
      fileId: latestVersion.id
    };
  } catch (error) {
    if (error.response) {
      throw new Error(`API Error ${error.response.status}: ${error.response.statusText} for ${projectId}`);
    } else {
      throw new Error(`Network error for ${projectId}: ${error.message}`);
    }
  }
}

async function getLatestResourcePackVersion(packId) {
  const url = `${MODRINTH_API}/project/${packId}/version?game_versions=["1.20.1"]`;
  const headers = { 'User-Agent': USER_AGENT };
  if (MODRINTH_TOKEN) {
    headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
  }
  
  try {
    const response = await axios.get(url, { headers });
    
    if (!response.data || response.data.length === 0) {
      throw new Error('No compatible versions found for Minecraft 1.20.1');
    }
    
    const latestVersion = response.data[0];
    const primaryFile = latestVersion.files.find(f => f.primary) || latestVersion.files[0];
    
    return {
      filename: primaryFile.filename,
      fileId: latestVersion.id
    };
  } catch (error) {
    if (error.response) {
      throw new Error(`API Error ${error.response.status}: ${error.response.statusText} for ${packId}`);
    } else {
      throw new Error(`Network error for ${packId}: ${error.message}`);
    }
  }
}

async function downloadMod(mod, destDir) {
  if (mod.manual) {
    console.log(`⚠️  Manual mod: ${mod.name} - Please add ${mod.filename} to ${destDir}`);
    return null;
  }
  
  // If filename is empty, fetch the latest version for this mod
  if (!mod.filename || mod.filename === '') {
    try {
      console.log(`📥 Fetching latest version for ${mod.name}...`);
      const versionData = await getLatestModVersion(mod.projectId);
      mod.filename = versionData.filename;
      mod.fileId = versionData.fileId;
    } catch (error) {
      console.error(`❌ Failed to get latest version for ${mod.name}:`, error.message);
      return null;
    }
  }
  
  const destPath = path.join(destDir, mod.filename);
  
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

async function downloadResourcePack(pack, destDir) {
  // If filename is empty, fetch the latest version for this resource pack
  if (!pack.filename || pack.filename === '') {
    try {
      console.log(`📥 Fetching latest version for ${pack.name}...`);
      const versionData = await getLatestResourcePackVersion(pack.projectId);
      pack.filename = versionData.filename;
      pack.fileId = versionData.fileId;
    } catch (error) {
      console.error(`❌ Failed to get latest version for ${pack.name}:`, error.message);
      return null;
    }
  }
  
  const destPath = path.join(destDir, pack.filename);
  
  if (fs.existsSync(destPath)) {
    console.log(`✓ ${pack.name} already downloaded`);
    return destPath;
  }
  
  console.log(`📥 Downloading resource pack ${pack.name}...`);
  try {
    const downloadUrl = await getModDownloadUrl(pack.projectId, pack.fileId);
    await downloadFile(downloadUrl, destPath);
    console.log(`✓ Downloaded resource pack ${pack.name}`);
    return destPath;
  } catch (error) {
    console.error(`❌ Failed to download resource pack ${pack.name}:`, error.message);
    return null;
  }
}

async function downloadShaderPack(pack, destDir) {
  // If filename is empty, fetch the latest version for this shader pack
  if (!pack.filename || pack.filename === '') {
    try {
      console.log(`📥 Fetching latest version for ${pack.name}...`);
      const versionData = await getLatestResourcePackVersion(pack.projectId);
      pack.filename = versionData.filename;
      pack.fileId = versionData.fileId;
    } catch (error) {
      console.error(`❌ Failed to get latest version for ${pack.name}:`, error.message);
      return null;
    }
  }
  
  const destPath = path.join(destDir, pack.filename);
  
  if (fs.existsSync(destPath)) {
    console.log(`✓ ${pack.name} already downloaded`);
    return destPath;
  }
  
  console.log(`📥 Downloading shader pack ${pack.name}...`);
  try {
    const downloadUrl = await getModDownloadUrl(pack.projectId, pack.fileId);
    await downloadFile(downloadUrl, destPath);
    console.log(`✓ Downloaded shader pack ${pack.name}`);
    return destPath;
  } catch (error) {
    console.error(`❌ Failed to download shader pack ${pack.name}:`, error.message);
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

async function createMrpack(packType, mods, index, enhanced = false) {
  const buildDir = `build/${packType}`;
  const suffix = enhanced ? '-enhanced' : '';
  
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
    
    // Copy shared config files to overrides/config
    const sharedConfigDir = 'src/shared/config';
    if (fs.existsSync(sharedConfigDir)) {
      const overridesConfigDir = path.join(overridesDir, 'config');
      fs.mkdirSync(overridesConfigDir, { recursive: true });
      
      // Copy all config files from shared directory
      const configFiles = fs.readdirSync(sharedConfigDir);
      configFiles.forEach(file => {
        const srcPath = path.join(sharedConfigDir, file);
        const destPath = path.join(overridesConfigDir, file);
        const stats = fs.statSync(srcPath);
        
        if (stats.isDirectory()) {
          // Copy directory recursively
          fs.mkdirSync(destPath, { recursive: true });
          const files = fs.readdirSync(srcPath);
          files.forEach(subFile => {
            const subSrcPath = path.join(srcPath, subFile);
            const subDestPath = path.join(destPath, subFile);
            fs.copyFileSync(subSrcPath, subDestPath);
          });
          console.log(`  Copied config directory: ${file}`);
        } else {
          // Copy file
          fs.copyFileSync(srcPath, destPath);
          console.log(`  Copied config: ${file}`);
        }
      });
    }
    
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
    
    // If enhanced mode, copy resource packs and shader packs to overrides
    if (enhanced && packType === 'client') {
      // Create resourcepacks and shaderpacks directories in overrides
      const resourcePacksDir = path.join(overridesDir, 'resourcepacks');
      const shaderPacksDir = path.join(overridesDir, 'shaderpacks');
      fs.mkdirSync(resourcePacksDir, { recursive: true });
      fs.mkdirSync(shaderPacksDir, { recursive: true });
      
      // Copy resource packs
      for (const pack of resourcePacks.resourcePacks) {
        if (pack.filename) {
          const sourcePath = path.join('build/client/resourcepacks', pack.filename);
          if (fs.existsSync(sourcePath)) {
            fs.copyFileSync(sourcePath, path.join(resourcePacksDir, pack.filename));
            console.log(`  Copied resource pack: ${pack.name}`);
          }
        }
      }
      
      // Copy shader packs  
      for (const pack of shaderPacks.shaderPacks) {
        if (pack.filename) {
          const sourcePath = path.join('build/client/shaderpacks', pack.filename);
          if (fs.existsSync(sourcePath)) {
            fs.copyFileSync(sourcePath, path.join(shaderPacksDir, pack.filename));
            console.log(`  Copied shader pack: ${pack.name}`);
          }
        }
      }
    }
    
    // Create resource pack and shader pack recommendations
    if (packType === 'client') {
      const resourcePackInfo = `RECOMMENDED RESOURCE PACKS:

1. Soartex Grove v1.0.4 (64x)
   - High definition graphics replacement with clean artwork
   - Download: https://modrinth.com/resourcepack/soartex-grove/version/1.0.4.1
   - Optional but enhances the visual experience

RECOMMENDED SHADER PACKS:

1. BSL Shaders
   - Bright, colorful, and distinct lighting
   - Download: https://modrinth.com/shader/Q1vvjJYV
   - Good performance on most hardware

2. Complementary Shaders
   - Continuation of SEUS Renewed with PBR support
   - Download: https://modrinth.com/shader/EtVJtuTU
   - High quality with many features

3. Sildur's Enhanced Default
   - Enhanced default look, performance friendly
   - Download: https://modrinth.com/shader/EmuGFQyu
   - Great for lower-end hardware

Installation:
- Resource packs: Place in .minecraft/resourcepacks/
- Shader packs: Requires Iris Shaders mod (included), place in .minecraft/shaderpacks/
`;
      fs.writeFileSync(path.join(overridesDir, 'RESOURCE_PACKS_AND_SHADERS.txt'), resourcePackInfo);
    }
  }
  
  // Create README
  fs.writeFileSync(
    path.join(buildDir, 'README.txt'),
    `${packInfo.name} - ${packType.charAt(0).toUpperCase() + packType.slice(1)} Pack\n` +
    `Version: ${packInfo.version}\n` +
    `Minecraft: ${packInfo.minecraft}\n` +
    `Fabric Loader: ${packInfo.fabric}\n\n` +
    packInfo.description[packType] +
    (packType === 'client' ? '\n\nSee RESOURCE_PACKS_AND_SHADERS.txt for recommended visual enhancements.' : '')
  );
  
  // Create the mrpack
  const outputFile = `${packInfo.name.toLowerCase().replace(/\s+/g, '-')}-${packType}-${packInfo.version}${suffix}.mrpack`;
  const outputPath = `build/${outputFile}`;
  console.log(`\n📦 Creating ${outputPath}...`);
  
  // Only include necessary files in mrpack (exclude mods folder since they're referenced by URL)
  execSync(`cd ${buildDir} && zip -r ../${outputFile} modrinth.index.json icon.png README.txt overrides/ -x mods/`, { stdio: 'inherit' });
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
  
  // Clean up old assets first
  cleanupOldAssets();
  
  // Download client mods
  console.log('=== Building Client Pack ===');
  const clientDownloaded = {};
  for (const mod of clientMods.mods) {
    const filePath = await downloadMod(mod, 'build/client/mods');
    if (filePath) {
      clientDownloaded[mod.filename] = filePath;
    }
  }
  
  // Download resource packs for client
  console.log('\n=== Downloading Resource Packs ===');
  for (const pack of resourcePacks.resourcePacks) {
    await downloadResourcePack(pack, 'build/client/resourcepacks');
  }
  
  // Download shader packs for client
  console.log('\n=== Downloading Shader Packs ===');
  for (const pack of shaderPacks.shaderPacks) {
    await downloadShaderPack(pack, 'build/client/shaderpacks');
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
  
  // Create regular mrpacks
  await createMrpack('client', clientMods.mods, clientIndex, false);
  await createMrpack('server', serverMods.mods, serverIndex, false);
  
  // Create enhanced mrpacks (with resource packs and shader packs)
  console.log('\n=== Creating Enhanced Packs ===');
  await createMrpack('client', clientMods.mods, clientIndex, true);
  
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