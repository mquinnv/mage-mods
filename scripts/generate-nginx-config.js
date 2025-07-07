#!/usr/bin/env node

// Load environment variables from .env file
require('dotenv').config();

const fs = require('fs');
const path = require('path');
const https = require('https');

const MODRINTH_API = 'https://api.modrinth.com/v2';
const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.error('❌ MODRINTH_TOKEN is required!');
  console.error('   Set it in your .env file');
  process.exit(1);
}

// Load project configurations
const uploadConfig = JSON.parse(fs.readFileSync('config/upload-config.json', 'utf8'));
const projectConfigs = JSON.parse(fs.readFileSync('config/modrinth-projects.json', 'utf8'));

async function getLatestDownloadUrl(projectKey, projectId) {
  // Map project keys to their slugs
  const projectSlugMap = {
    'client-base': 'mage-net-client-base',
    'client-enhanced': 'mage-net-client-enhanced',
    'server': 'mage-net-server'
  };
  
  const slug = projectSlugMap[projectKey] || projectKey;
  
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'api.modrinth.com',
      path: `/v2/project/${slug}/version`,
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${MODRINTH_TOKEN}`,
        'User-Agent': 'minecraft-mage-pack-builder/1.0.0'
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode === 200) {
          const versions = JSON.parse(data);
          if (versions.length > 0) {
            // Get the latest version (first in array)
            const latestVersion = versions[0];
            const downloadUrl = latestVersion.files[0].url;
            resolve(downloadUrl);
          } else {
            reject(new Error(`No versions found for project ${slug}`));
          }
        } else {
          reject(new Error(`API request failed: ${res.statusCode} for project ${slug}`));
        }
      });
    });

    req.on('error', reject);
    req.end();
  });
}

async function generateNginxConfig() {
  console.log('🔧 Generating nginx configuration for modpack redirects...');
  
  const redirectRules = [];
  
  try {
    // Generate redirect rules for each project
    for (const [projectKey, projectId] of Object.entries(uploadConfig.projects)) {
      console.log(`📦 Getting latest download URL for ${projectKey}...`);
      const downloadUrl = await getLatestDownloadUrl(projectKey, projectId);
      
      // Map project keys to clean URL paths
      const urlPath = projectKey.replace('-', '/');
      
      redirectRules.push({
        path: `/${urlPath}`,
        url: downloadUrl,
        project: projectKey
      });
      
      // Add a small delay to be polite to the API
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    // Generate the nginx configuration
    const nginxConfig = `# Minecraft Mage.net Modpack Redirects
# Generated on ${new Date().toISOString()}
# This configuration provides clean URLs for the latest modpack versions

# Modpack download redirects
${redirectRules.map(rule => 
  `location ${rule.path} {
    return 302 "${rule.url}";
}`).join('\n\n')}

# Alternative paths for convenience
location /client {
    return 302 https://minecraft.mage.net/client/base;
}

location /modpacks {
    return 302 https://modrinth.com/user/michael;
}

# Info endpoint
location /modpack-info {
    return 200 '{
        "client_base": "https://minecraft.mage.net/client/base",
        "client_enhanced": "https://minecraft.mage.net/client/enhanced", 
        "server": "https://minecraft.mage.net/server",
        "modrinth_projects": {
            "client_base": "https://modrinth.com/modpack/mage-net-client-base",
            "client_enhanced": "https://modrinth.com/modpack/mage-net-client-enhanced",
            "server": "https://modrinth.com/modpack/mage-net-server"
        }
    }';
    add_header Content-Type application/json;
}
`;

    // Write the configuration to file
    const configPath = path.join(process.cwd(), 'nginx-modpack-redirects.conf');
    fs.writeFileSync(configPath, nginxConfig);
    
    console.log('✅ Nginx configuration generated successfully!');
    console.log(`📄 Config saved to: ${configPath}`);
    console.log('\n📋 Generated redirect rules:');
    redirectRules.forEach(rule => {
      console.log(`  ${rule.path} → ${rule.url}`);
    });
    
    console.log('\n🔧 To use this configuration:');
    console.log('1. Include this file in your nginx server block:');
    console.log('   include /path/to/nginx-modpack-redirects.conf;');
    console.log('2. Reload nginx: sudo nginx -s reload');
    console.log('\n💡 URLs will be available at:');
    console.log('   https://minecraft.mage.net/client/base');
    console.log('   https://minecraft.mage.net/client/enhanced');
    console.log('   https://minecraft.mage.net/server');
    console.log('   https://minecraft.mage.net/modpack-info (JSON API)');
    
  } catch (error) {
    console.error('❌ Failed to generate nginx config:', error.message);
    process.exit(1);
  }
}

generateNginxConfig();