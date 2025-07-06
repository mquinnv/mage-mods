const https = require('https');
require('dotenv').config();

const MODRINTH_TOKEN = process.env.MODRINTH_TOKEN;

// Mod list with their current versions for comparison
const mods = [
    { name: 'Fabric API', id: 'P7dR8mSH', current: '0.92.6+1.20.1' },
    { name: 'Sodium', id: 'AANobbMI', current: '0.5.13+mc1.20.1' },
    { name: 'Iris Shaders', id: 'YL57xq9U', current: '1.7.6+mc1.20.1' },
    { name: 'Indium', id: 'Orvt0mRa', current: '1.0.36+mc1.20.1' },
    { name: 'Entity Culling', id: 'NNAgCjsB', current: '1.8.0-mc1.20.1' },
    { name: 'FerriteCore', id: 'uXXizFIs', current: '6.0.1-fabric' },
    { name: 'Yet Another Config Lib v3', id: '1eAoo2KR', current: '3.6.6+1.20.1-fabric' },
    { name: 'LazyDFU', id: 'hvFnDODi', current: '0.1.3' },
    { name: 'Memory Leak Fix', id: 'NRjRiSSD', current: '1.1.5' },
    { name: 'Jade', id: 'nvQzSEkH', current: '11.13.1' },
    { name: 'JEI', id: 'u6dRKJwZ', current: '15.20.0.112' },
    { name: 'Just Enough Resources', id: 'uEfK2CXF', current: '1.4.0.247' },
    { name: 'Enchancement', id: '6hN1V6wJ', current: '1.20-26' },
    { name: 'ToolTipFix', id: '2RKFTmiB', current: '1.1.1-1.20' },
    { name: 'Dye Depot', id: 'dl88rsIq', current: '1.0.3-fabric' },
    { name: 'AppleSkin', id: 'EsAfCjCV', current: '2.5.1' },
    { name: 'Inventory Profiles Next', id: 'O7RBXm3n', current: '1.10.12' },
    { name: 'Continuity', id: '1IjD5062', current: '3.0.0+1.20.1' },
    { name: 'Mod Menu', id: 'mOgUt4GM', current: '7.2.2' },
    { name: 'Cloth Config', id: '9s6osm5g', current: '11.1.136-fabric' },
    { name: 'AutoSwitch', id: 'uSdcnlts', current: '7.0.2' },
    { name: 'Fabric Language Kotlin', id: 'Ha28R6CL', current: '1.13.4+kotlin.2.2.0' },
    { name: 'libIPN', id: 'onSQdWhM', current: '4.0.2' },
    { name: 'Spelunkery', id: 'krskFMfA', current: '0.3.16' },
    { name: 'Supplementaries', id: 'fFEIiSDQ', current: '3.1.31-fabric' },
    { name: 'Mouse Tweaks', id: 'aC3cM3Vq', current: '2.26' },
    { name: 'Bountiful Fares', id: 'YgrSDjlb', current: '1.2.1-1.20.1' },
    { name: 'Create', id: 'Xbc0uyRg', current: '0.5.1-j-build.1631+mc1.20.1' },
    { name: 'Better Clouds', id: '5srFLIaK', current: '1.3.32+1.20.1-fabric' },
    { name: 'Moonlight Lib', id: 'twkfQtEc', current: '2.14.11-fabric' },
    { name: 'Astrocraft', id: 'DCWEXia7', current: '1.5.1+1.20.1' },
    { name: 'Amendments', id: '6iTJugQR', current: '1.2.19-fabric' },
    { name: 'Xaero\'s Minimap', id: '1bokaNcj', current: 'missing file info' },
    { name: 'Xaero\'s World Map', id: 'NcUtCpym', current: 'missing file info' },
    { name: 'Oreganized', id: '2FJutzEL', current: 'missing file info' }
];

function makeRequest(url, headers = {}) {
    return new Promise((resolve, reject) => {
        const options = {
            headers: {
                'User-Agent': 'minecraft.mage.net/1.0 (michael.ventura@gmail.com)',
                ...headers
            }
        };

        https.get(url, options, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', reject);
    });
}

async function getLatestVersion(modId, modName) {
    try {
        const headers = {};
        if (MODRINTH_TOKEN) {
            headers['Authorization'] = `Bearer ${MODRINTH_TOKEN}`;
        }

        // Get project versions filtered for 1.20.1 and fabric
        const url = `https://api.modrinth.com/v2/project/${modId}/version?game_versions=["1.20.1"]&loaders=["fabric"]`;
        const versions = await makeRequest(url, headers);
        
        if (!versions || versions.length === 0) {
            return {
                name: modName,
                id: modId,
                error: 'No versions found for MC 1.20.1 Fabric'
            };
        }

        // Get the latest version (first in array as they're sorted by date)
        const latest = versions[0];
        const primaryFile = latest.files.find(f => f.primary) || latest.files[0];

        return {
            name: modName,
            id: modId,
            version: latest.version_number,
            fileId: latest.id,
            filename: primaryFile.filename,
            downloadUrl: primaryFile.url,
            datePublished: latest.date_published,
            changelog: latest.changelog ? latest.changelog.substring(0, 200) + '...' : 'No changelog'
        };
    } catch (error) {
        return {
            name: modName,
            id: modId,
            error: error.message
        };
    }
}

async function checkAllMods() {
    console.log('Checking latest versions for Minecraft 1.20.1 Fabric mods...\n');
    console.log('='.repeat(80));
    
    const results = [];
    
    // Process mods in batches to avoid rate limiting
    const batchSize = 5;
    for (let i = 0; i < mods.length; i += batchSize) {
        const batch = mods.slice(i, i + batchSize);
        const batchPromises = batch.map(mod => getLatestVersion(mod.id, mod.name));
        const batchResults = await Promise.all(batchPromises);
        results.push(...batchResults);
        
        // Add delay between batches
        if (i + batchSize < mods.length) {
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }
    
    // Display results
    for (const result of results) {
        if (result.error) {
            console.log(`❌ ${result.name} (${result.id}): ${result.error}`);
        } else {
            const currentMod = mods.find(m => m.id === result.id);
            const isUpdated = currentMod.current !== result.version;
            const status = isUpdated ? '🆕 UPDATE AVAILABLE' : '✅ Current';
            
            console.log(`${status} ${result.name}`);
            console.log(`   ID: ${result.id}`);
            console.log(`   Current: ${currentMod.current}`);
            console.log(`   Latest: ${result.version}`);
            console.log(`   File ID: ${result.fileId}`);
            console.log(`   Filename: ${result.filename}`);
            console.log(`   Published: ${new Date(result.datePublished).toLocaleDateString()}`);
            if (result.changelog && result.changelog !== 'No changelog') {
                console.log(`   Changelog: ${result.changelog}`);
            }
            console.log('');
        }
    }
    
    // Summary
    const updatesAvailable = results.filter(r => !r.error && mods.find(m => m.id === r.id).current !== r.version);
    console.log('='.repeat(80));
    console.log(`SUMMARY: ${updatesAvailable.length} updates available out of ${results.filter(r => !r.error).length} successfully checked mods`);
    
    if (updatesAvailable.length > 0) {
        console.log('\nMods with updates available:');
        updatesAvailable.forEach(mod => {
            const current = mods.find(m => m.id === mod.id).current;
            console.log(`- ${mod.name}: ${current} → ${mod.version}`);
        });
    }
}

checkAllMods().catch(console.error);