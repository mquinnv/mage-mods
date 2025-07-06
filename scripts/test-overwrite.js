#!/usr/bin/env node

require('dotenv').config();
const path = require('path');
const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function testOverwrite() {
  const client = new Client();
  
  try {
    console.log('🧪 Testing file overwrite behavior...');
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // Try to delete the existing ferritecore file first
    console.log('🗑️  Trying to delete existing ferritecore file...');
    try {
      await client.remove('mods/ferritecore-6.0.1-fabric.jar');
      console.log('✅ Deleted existing file');
    } catch (error) {
      console.log(`❌ Could not delete: ${error.message}`);
    }
    
    // Now try to upload ferritecore again
    console.log('📤 Trying to upload ferritecore again...');
    const testFile = 'ferritecore-6.0.1-fabric.jar';
    const localPath = path.join(process.cwd(), 'build', 'server', 'mods', testFile);
    const remotePath = `mods/${testFile}`;
    
    try {
      await client.uploadFrom(localPath, remotePath);
      console.log('✅ Upload successful after deletion!');
    } catch (error) {
      console.log(`❌ Upload failed even after deletion: ${error.message}`);
    }
    
    // Test uploading a different mod that doesn't exist yet
    console.log('📤 Trying to upload a new mod (autoswitch)...');
    const newFile = 'autoswitch-7.0.2.jar';
    const newLocalPath = path.join(process.cwd(), 'build', 'server', 'mods', newFile);
    const newRemotePath = `mods/${newFile}`;
    
    try {
      await client.uploadFrom(newLocalPath, newRemotePath);
      console.log('✅ New file upload successful!');
    } catch (error) {
      console.log(`❌ New file upload failed: ${error.message}`);
    }
    
  } catch (error) {
    console.error('❌ Test failed:', error.message);
  } finally {
    client.close();
  }
}

testOverwrite();