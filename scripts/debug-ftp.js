#!/usr/bin/env node

// Debug FTP connection to see directory structure
require('dotenv').config();

const { Client } = require('basic-ftp');

const { FTP_HOST, FTP_USER, FTP_PASSWORD, FTP_PORT = 21 } = process.env;

async function debugFTP() {
  const client = new Client();
  
  try {
    console.log('🔍 Debugging FTP connection...');
    
    await client.access({
      host: FTP_HOST,
      user: FTP_USER,
      password: FTP_PASSWORD,
      port: parseInt(FTP_PORT),
      secure: false
    });
    
    console.log('✅ Connected to FTP server');
    
    // List current directory
    console.log('\n📁 Current directory contents:');
    const files = await client.list();
    files.forEach(file => {
      console.log(`  ${file.type === 'd' ? '📁' : '📄'} ${file.name} (${file.size} bytes)`);
    });
    
    // Check working directory
    console.log('\n📍 Current working directory:', await client.pwd());
    
    // Check if mods directory exists
    try {
      console.log('\n📁 Checking mods directory...');
      await client.cd('mods');
      console.log('📍 Inside mods directory:', await client.pwd());
      const modFiles = await client.list();
      console.log(`  Found ${modFiles.length} items in mods directory:`);
      modFiles.forEach(file => {
        console.log(`    ${file.type === 'd' ? '📁' : '📄'} ${file.name} (${file.size} bytes)`);
      });
      await client.cd('..');
    } catch (error) {
      console.log(`  ❌ Cannot access mods directory: ${error.message}`);
    }
    
    // Try creating mods directory if it doesn't exist properly
    console.log('\n🛠️  Testing mods directory creation...');
    try {
      await client.ensureDir('mods');
      console.log('  ✅ Mods directory ensured');
    } catch (error) {
      console.log(`  ❌ Cannot ensure mods directory: ${error.message}`);
    }
    
    // Test creating a small file
    console.log('\n🧪 Testing file upload...');
    try {
      const testContent = 'test file for deployment';
      const testPath = '/tmp/test-upload.txt';
      require('fs').writeFileSync(testPath, testContent);
      
      await client.uploadFrom(testPath, 'test-upload.txt');
      console.log('  ✅ Test file upload successful');
      
      // Clean up
      await client.remove('test-upload.txt');
      require('fs').unlinkSync(testPath);
      console.log('  🧹 Test file cleaned up');
    } catch (error) {
      console.log(`  ❌ Test file upload failed: ${error.message}`);
    }
    
  } catch (error) {
    console.error('❌ Debug failed:', error.message);
  } finally {
    client.close();
  }
}

debugFTP();