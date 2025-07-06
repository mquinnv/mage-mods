#!/usr/bin/env node

/**
 * Modrinth Research Tool
 * 
 * A reliable script to interact with the Modrinth API for mod research.
 * Eliminates syntax errors and provides consistent, formatted results.
 * 
 * Usage:
 *   node scripts/modrinth-research.js search "mod name" [minecraft_version] [loader]
 *   node scripts/modrinth-research.js project PROJECT_ID
 *   node scripts/modrinth-research.js versions PROJECT_ID [minecraft_version] [loader]
 *   node scripts/modrinth-research.js compatibility "mod1 name" "mod2 name"
 */

const https = require('https');

class ModrinthAPI {
    constructor() {
        this.baseURL = 'https://api.modrinth.com/v2';
        this.userAgent = 'minecraft-mage-modpack-manager/1.0.0';
    }

    async request(endpoint) {
        return new Promise((resolve, reject) => {
            const options = {
                hostname: 'api.modrinth.com',
                path: `/v2${endpoint}`,
                headers: {
                    'User-Agent': this.userAgent,
                    'Accept': 'application/json'
                }
            };

            const req = https.request(options, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        const result = JSON.parse(data);
                        resolve(result);
                    } catch (e) {
                        reject(new Error(`Failed to parse JSON: ${e.message}`));
                    }
                });
            });

            req.on('error', reject);
            req.end();
        });
    }

    async searchMods(query, minecraftVersion = '1.20.1', loader = 'fabric', limit = 10) {
        const encodedQuery = encodeURIComponent(query);
        const facets = encodeURIComponent(JSON.stringify([
            ['versions:' + minecraftVersion],
            ['categories:' + loader]
        ]));
        
        const endpoint = `/search?query=${encodedQuery}&facets=${facets}&limit=${limit}&offset=0`;
        return await this.request(endpoint);
    }

    async getProject(projectId) {
        return await this.request(`/project/${projectId}`);
    }

    async getVersions(projectId, minecraftVersion = '1.20.1', loader = 'fabric') {
        const gameVersions = encodeURIComponent(JSON.stringify([minecraftVersion]));
        const loaders = encodeURIComponent(JSON.stringify([loader]));
        const endpoint = `/project/${projectId}/version?game_versions=${gameVersions}&loaders=${loaders}`;
        return await this.request(endpoint);
    }

    async getDependencies(projectId, minecraftVersion = '1.20.1', loader = 'fabric') {
        const versions = await this.getVersions(projectId, minecraftVersion, loader);
        if (versions.length === 0) return [];
        
        return versions[0].dependencies || [];
    }

    formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    formatDownloads(downloads) {
        if (downloads >= 1000000) {
            return `${(downloads / 1000000).toFixed(1)}M+`;
        } else if (downloads >= 1000) {
            return `${(downloads / 1000).toFixed(0)}k+`;
        }
        return downloads.toString();
    }

    formatProject(project) {
        return {
            name: project.title,
            slug: project.slug,
            projectId: project.project_id || project.id,
            description: project.description,
            downloads: this.formatDownloads(project.downloads),
            categories: project.categories,
            clientSide: project.client_side,
            serverSide: project.server_side,
            license: project.license?.id || 'Unknown',
            sourceUrl: project.source_url,
            issuesUrl: project.issues_url,
            wikiUrl: project.wiki_url
        };
    }

    formatVersion(version) {
        return {
            versionNumber: version.version_number,
            versionName: version.name,
            fileId: version.id,
            filename: version.files[0]?.filename || 'Unknown',
            size: this.formatBytes(version.files[0]?.size || 0),
            downloadUrl: version.files[0]?.url,
            releaseChannel: version.version_type,
            publishedAt: new Date(version.date_published).toLocaleDateString(),
            gameVersions: version.game_versions,
            loaders: version.loaders,
            dependencies: version.dependencies.map(dep => ({
                projectId: dep.project_id,
                dependencyType: dep.dependency_type,
                filename: dep.file_name
            }))
        };
    }
}

class ModrinthResearcher {
    constructor() {
        this.api = new ModrinthAPI();
    }

    async searchCommand(query, minecraftVersion = '1.20.1', loader = 'fabric') {
        console.log(`🔍 Searching for "${query}" on ${loader} ${minecraftVersion}...\n`);
        
        try {
            const results = await this.api.searchMods(query, minecraftVersion, loader);
            
            if (results.hits.length === 0) {
                console.log('❌ No mods found matching your search.');
                return;
            }

            console.log(`✅ Found ${results.hits.length} mods:\n`);
            
            results.hits.forEach((hit, index) => {
                const project = this.api.formatProject(hit);
                console.log(`${index + 1}. **${project.name}**`);
                console.log(`   Project ID: ${project.projectId}`);
                console.log(`   Downloads: ${project.downloads}`);
                console.log(`   Description: ${project.description.substring(0, 100)}...`);
                console.log(`   Categories: ${project.categories.join(', ')}`);
                console.log(`   Client: ${project.clientSide} | Server: ${project.serverSide}`);
                console.log('');
            });
        } catch (error) {
            console.error('❌ Search failed:', error.message);
        }
    }

    async projectCommand(projectId) {
        console.log(`📦 Getting project info for ${projectId}...\n`);
        
        try {
            const project = await this.api.getProject(projectId);
            const formatted = this.api.formatProject(project);
            
            console.log(`**${formatted.name}**`);
            console.log(`Project ID: ${formatted.projectId}`);
            console.log(`Slug: ${formatted.slug}`);
            console.log(`Downloads: ${formatted.downloads}`);
            console.log(`License: ${formatted.license}`);
            console.log(`Client Side: ${formatted.clientSide}`);
            console.log(`Server Side: ${formatted.serverSide}`);
            console.log(`Categories: ${formatted.categories.join(', ')}`);
            console.log(`\nDescription:`);
            console.log(formatted.description);
            
            if (formatted.sourceUrl) console.log(`\nSource: ${formatted.sourceUrl}`);
            if (formatted.issuesUrl) console.log(`Issues: ${formatted.issuesUrl}`);
            if (formatted.wikiUrl) console.log(`Wiki: ${formatted.wikiUrl}`);
            
        } catch (error) {
            console.error('❌ Failed to get project info:', error.message);
        }
    }

    async versionsCommand(projectId, minecraftVersion = '1.20.1', loader = 'fabric') {
        console.log(`📋 Getting versions for ${projectId} on ${loader} ${minecraftVersion}...\n`);
        
        try {
            const versions = await this.api.getVersions(projectId, minecraftVersion, loader);
            
            if (versions.length === 0) {
                console.log(`❌ No versions found for ${loader} ${minecraftVersion}`);
                return;
            }

            console.log(`✅ Found ${versions.length} version(s):\n`);
            
            versions.forEach((version, index) => {
                const formatted = this.api.formatVersion(version);
                console.log(`${index + 1}. **${formatted.versionName}** (${formatted.versionNumber})`);
                console.log(`   File ID: ${formatted.fileId}`);
                console.log(`   Filename: ${formatted.filename}`);
                console.log(`   Size: ${formatted.size}`);
                console.log(`   Release Channel: ${formatted.releaseChannel}`);
                console.log(`   Published: ${formatted.publishedAt}`);
                console.log(`   Game Versions: ${formatted.gameVersions.join(', ')}`);
                console.log(`   Loaders: ${formatted.loaders.join(', ')}`);
                if (formatted.dependencies.length > 0) {
                    console.log(`   Dependencies: ${formatted.dependencies.map(d => `${d.projectId} (${d.dependencyType})`).join(', ')}`);
                }
                console.log('');
            });
        } catch (error) {
            console.error('❌ Failed to get versions:', error.message);
        }
    }

    async compatibilityCommand(mod1Name, mod2Name, minecraftVersion = '1.20.1', loader = 'fabric') {
        console.log(`🔍 Checking compatibility between "${mod1Name}" and "${mod2Name}"...\n`);
        
        try {
            // Search for both mods
            const [mod1Results, mod2Results] = await Promise.all([
                this.api.searchMods(mod1Name, minecraftVersion, loader, 3),
                this.api.searchMods(mod2Name, minecraftVersion, loader, 3)
            ]);

            if (mod1Results.hits.length === 0 || mod2Results.hits.length === 0) {
                console.log('❌ Could not find one or both mods.');
                return;
            }

            const mod1 = mod1Results.hits[0];
            const mod2 = mod2Results.hits[0];

            console.log(`**${mod1.title}** vs **${mod2.title}**\n`);

            // Check for dependency relationships
            const [mod1Deps, mod2Deps] = await Promise.all([
                this.api.getDependencies(mod1.project_id, minecraftVersion, loader),
                this.api.getDependencies(mod2.project_id, minecraftVersion, loader)
            ]);

            const mod1DependsOnMod2 = mod1Deps.some(dep => dep.project_id === mod2.project_id);
            const mod2DependsOnMod1 = mod2Deps.some(dep => dep.project_id === mod1.project_id);

            if (mod1DependsOnMod2) {
                console.log(`✅ ${mod1.title} depends on ${mod2.title} - They are designed to work together!`);
            } else if (mod2DependsOnMod1) {
                console.log(`✅ ${mod2.title} depends on ${mod1.title} - They are designed to work together!`);
            } else {
                console.log(`ℹ️  No direct dependency relationship found.`);
            }

            // Check for category overlaps
            const overlappingCategories = mod1.categories.filter(cat => mod2.categories.includes(cat));
            if (overlappingCategories.length > 0) {
                console.log(`⚠️  Both mods share categories: ${overlappingCategories.join(', ')} - Check for feature conflicts`);
            }

            console.log(`\n**${mod1.title}**:`);
            console.log(`  Project ID: ${mod1.project_id}`);
            console.log(`  Categories: ${mod1.categories.join(', ')}`);
            console.log(`  Downloads: ${this.api.formatDownloads(mod1.downloads)}`);

            console.log(`\n**${mod2.title}**:`);
            console.log(`  Project ID: ${mod2.project_id}`);
            console.log(`  Categories: ${mod2.categories.join(', ')}`);
            console.log(`  Downloads: ${this.api.formatDownloads(mod2.downloads)}`);

        } catch (error) {
            console.error('❌ Compatibility check failed:', error.message);
        }
    }
}

// CLI Interface
async function main() {
    const args = process.argv.slice(2);
    const researcher = new ModrinthResearcher();

    if (args.length === 0) {
        console.log(`
🔍 Modrinth Research Tool

Usage:
  node scripts/modrinth-research.js search "mod name" [minecraft_version] [loader]
  node scripts/modrinth-research.js project PROJECT_ID
  node scripts/modrinth-research.js versions PROJECT_ID [minecraft_version] [loader]
  node scripts/modrinth-research.js compatibility "mod1 name" "mod2 name" [minecraft_version] [loader]

Examples:
  node scripts/modrinth-research.js search "JEI"
  node scripts/modrinth-research.js project "u6dRKJwZ"
  node scripts/modrinth-research.js versions "u6dRKJwZ" "1.20.1" "fabric"
  node scripts/modrinth-research.js compatibility "JEI" "EMI"
        `);
        return;
    }

    const command = args[0];

    switch (command) {
        case 'search':
            if (args.length < 2) {
                console.error('❌ Search command requires a mod name');
                return;
            }
            await researcher.searchCommand(args[1], args[2], args[3]);
            break;

        case 'project':
            if (args.length < 2) {
                console.error('❌ Project command requires a project ID');
                return;
            }
            await researcher.projectCommand(args[1]);
            break;

        case 'versions':
            if (args.length < 2) {
                console.error('❌ Versions command requires a project ID');
                return;
            }
            await researcher.versionsCommand(args[1], args[2], args[3]);
            break;

        case 'compatibility':
            if (args.length < 3) {
                console.error('❌ Compatibility command requires two mod names');
                return;
            }
            await researcher.compatibilityCommand(args[1], args[2], args[3], args[4]);
            break;

        default:
            console.error(`❌ Unknown command: ${command}`);
            console.log('Run without arguments to see usage information.');
    }
}

if (require.main === module) {
    main().catch(console.error);
}

module.exports = { ModrinthAPI, ModrinthResearcher };