#!/bin/bash
# Apex Hosting Server Configuration Script
# Deploys Minecraft Mage server files via FTP

set -e

# Load environment variables
if [ -f ".env" ]; then
    source .env
else
    echo "Error: .env file not found"
    exit 1
fi

# Check required FTP credentials
if [ -z "$FTP_HOST" ] || [ -z "$FTP_USER" ] || [ -z "$FTP_PASSWORD" ]; then
    echo "Error: Missing FTP credentials in .env file"
    exit 1
fi

echo "🚀 Deploying Minecraft Mage to Apex Hosting..."

# Create FTP script
cat > ftp_deploy.txt << EOF
open $FTP_HOST $FTP_PORT
user $FTP_USER $FTP_PASSWORD
binary
passive

# Upload optimized server.properties
put ../build/server-config/server.properties server.properties

quit
EOF

# Execute FTP commands
echo "📁 Connecting to Apex FTP server..."
ftp -n < ftp_deploy.txt

# Clean up
rm ftp_deploy.txt

echo "✅ Deployment complete!"
echo ""
echo "📋 Next steps on Apex control panel:"
echo "1. Set server JAR to: fabric-server-mc.1.20.1-loader.0.15.7.jar"
echo "2. Add JVM arguments:"
echo "   -Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dfabric.systemLibraries -Dfabric.skipMcProvider=true -Djava.awt.headless=true"
echo "3. Restart server"
