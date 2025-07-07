#!/usr/bin/env node

// Test script to verify the Modrinth icon API endpoint
require('dotenv').config();

const fs = require('fs');
const https = require('https');

const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

if (!MODRINTH_TOKEN) {
  console.error('❌ MODRINTH_TOKEN is required!');
  process.exit(1);
}

async function testIconAPI() {
  console.log('🧪 Testing Modrinth Icon API\n');
  
  // Test with a small test icon (create a minimal PNG for testing)
  const testIconPath = './test-icon.png';
  
  // Create a minimal 1x1 PNG file for testing
  const minimalPNG = Buffer.from([
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4,
    0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
    0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
    0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00,
    0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE,
    0x42, 0x60, 0x82
  ]);
  
  fs.writeFileSync(testIconPath, minimalPNG);
  
  console.log('✅ Created test icon file');
  
  // Test project ID (using one of your existing projects)
  const testProjectId = 'Tnp9M66F';
  
  try {
    const extension = 'png';
    const contentType = 'image/png';
    const fileData = fs.readFileSync(testIconPath);
    
    console.log(`📤 Testing API call to /v2/project/${testProjectId}/icon?ext=${extension}`);
    console.log(`   File size: ${fileData.length} bytes`);
    console.log(`   Content type: ${contentType}`);
    
    const options = {
      method: 'PATCH',
      hostname: 'api.modrinth.com',
      path: `/v2/project/${testProjectId}/icon?ext=${extension}`,
      headers: {
        'Authorization': `Bearer ${MODRINTH_TOKEN}`,
        'User-Agent': 'minecraft-mage-pack-builder/1.0.0',
        'Content-Type': contentType,
        'Content-Length': fileData.length
      }
    };
    
    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        console.log(`📊 Response Status: ${res.statusCode}`);
        console.log(`📊 Response Headers:`, res.headers);
        console.log(`📊 Response Body:`, data);
        
        if (res.statusCode === 200 || res.statusCode === 204) {
          console.log('✅ API test successful!');
        } else {
          console.log('❌ API test failed');
        }
        
        // Clean up test file
        fs.unlinkSync(testIconPath);
      });
    });
    
    req.on('error', (error) => {
      console.error('❌ Request error:', error);
      fs.unlinkSync(testIconPath);
    });
    
    req.write(fileData);
    req.end();
    
  } catch (error) {
    console.error('❌ Test failed:', error);
    fs.unlinkSync(testIconPath);
  }
}

testIconAPI();