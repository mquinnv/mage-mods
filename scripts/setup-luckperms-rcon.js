const { execSync } = require('child_process');
require('dotenv').config();

// LuckPerms setup via RCON
// Make sure to enable RCON in your server.properties:
// enable-rcon=true
// rcon.port=25575
// rcon.password=your_password
// And add RCON_PASSWORD=your_password to your .env file

const RCON_HOST = process.env.RCON_HOST || 'localhost';
const RCON_PORT = process.env.RCON_PORT || 25575;
const RCON_PASSWORD = process.env.RCON_PASSWORD;

const commands = [
    // Create groups
    'lp creategroup guest',
    'lp creategroup member',
    'lp creategroup vip',
    'lp creategroup moderator',
    'lp creategroup admin',
    
    // Set inheritance
    'lp group member parent add guest',
    'lp group vip parent add member',
    'lp group moderator parent add vip',
    'lp group admin parent add moderator',
    
    // Set weights
    'lp group guest meta setweight 10',
    'lp group member meta setweight 20',
    'lp group vip meta setweight 30',
    'lp group moderator meta setweight 40',
    'lp group admin meta setweight 50',
    
    // Basic permissions
    'lp group guest permission set minecraft.command.me true',
    'lp group guest permission set minecraft.command.tell true',
    'lp group guest permission set minecraft.command.help true',
    'lp group guest permission set minecraft.command.list true',
    'lp group guest meta setprefix "&7[Guest] "',
    
    'lp group member permission set minecraft.command.seed true',
    'lp group member permission set minecraft.command.time true',
    'lp group member meta setprefix "&a[Member] "',
    
    // VIP permissions (kids)
    'lp group vip permission set minecraft.command.gamemode true',
    'lp group vip permission set minecraft.command.give true',
    'lp group vip permission set minecraft.command.tp true',
    'lp group vip permission set minecraft.command.summon true',
    'lp group vip permission set minecraft.command.fill true',
    'lp group vip permission set minecraft.command.effect true',
    'lp group vip meta setprefix "&6[VIP] "',
    
    // Moderator permissions
    'lp group moderator permission set minecraft.command.ban true',
    'lp group moderator permission set minecraft.command.kick true',
    'lp group moderator permission set minecraft.command.whitelist true',
    'lp group moderator meta setprefix "&c[Mod] "',
    
    // Admin permissions
    'lp group admin permission set "*" true',
    'lp group admin meta setprefix "&4[Admin] "',
    
    // Set default
    'lp group default parent add guest'
];

const Rcon = require('rcon');

async function setupLuckPerms() {
    if (!RCON_PASSWORD) {
        console.error('Error: RCON_PASSWORD not found in .env file');
        console.log('Please add RCON_PASSWORD=your_password to your .env file');
        process.exit(1);
    }
    
    console.log(`Connecting to ${RCON_HOST}:${RCON_PORT}...`);
    
    const rcon = new Rcon(RCON_HOST, RCON_PORT, RCON_PASSWORD);
    
    // Set up event handlers first
    rcon.on('auth', () => {
        console.log('✓ RCON authentication successful');
    });
    
    rcon.on('response', (response) => {
        console.log(`Server response: ${response}`);
    });
    
    rcon.on('error', (err) => {
        console.error('RCON error:', err);
    });
    
    try {
        // Connect and wait for auth
        await new Promise((resolve, reject) => {
            rcon.once('auth', resolve);
            rcon.once('error', reject);
            rcon.connect();
        });
        
        // Run commands sequentially
        for (let i = 0; i < commands.length; i++) {
            const command = commands[i];
            console.log(`[${i + 1}/${commands.length}] Running: ${command}`);
            
            // Send command and don't wait for response - just continue
            rcon.send(command);
            
            // Small delay between commands
            await new Promise(resolve => setTimeout(resolve, 200));
        }
        
        console.log('\n✅ LuckPerms setup complete!');
        console.log('\nTo add users to groups, run:');
        console.log('lp user <username> parent add <group>');
        console.log('\nExample:');
        console.log('lp user kidname1 parent add vip');
        console.log('lp user yourusername parent add admin');
        
    } catch (error) {
        console.error('Error:', error.message);
        console.log('\nMake sure:');
        console.log('1. Server is running');
        console.log('2. RCON is enabled in server.properties');
        console.log('3. RCON password is correct');
    } finally {
        rcon.disconnect();
    }
}

if (require.main === module) {
    setupLuckPerms();
}