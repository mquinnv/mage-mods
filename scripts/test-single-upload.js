#!/usr/bin/env node

require('dotenv').config();
const path = require('path');
const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function testSingleUpload() {
  const client = new Client();
  
  try {
    console.log('🧪 Testing single mod upload...');
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // Try uploading the smallest mod file
    const testFile = 'ferritecore-6.0.1-fabric.jar';
    const localPath = path.join(process.cwd(), 'build', 'server', 'mods', testFile);
    const remotePath = `mods/${testFile}`;
    
    console.log(`📤 Uploading ${testFile}...`);
    console.log(`  Local: ${localPath}`);
    console.log(`  Remote: ${remotePath}`);
    
    await client.uploadFrom(localPath, remotePath);
    console.log('✅ Upload successful!');
    
    // Verify the upload
    console.log('🔍 Verifying upload...');
    await client.cd('mods');
    const files = await client.list();
    const uploadedFile = files.find(f => f.name === testFile);
    if (uploadedFile) {
      console.log(`✅ File verified: ${uploadedFile.name} (${uploadedFile.size} bytes)`);
    } else {
      console.log('❌ File not found after upload');
    }
    
  } catch (error) {
    console.error('❌ Test failed:', error.message);
    console.error('Full error:', error);
  } finally {
    client.close();
  }
}

testSingleUpload();