#!/bin/bash
set -e

echo "Setting up demo service..."

# Create a simple demo systemd service definition
mkdir -p /etc/systemd/system
cat > /etc/systemd/system/demo-service.service << 'EOF'
[Unit]
Description=Demo Service for Testing
After=network.target

[Service]
Type=simple
ExecStart=/bin/bash -c "while true; do echo 'Demo service running at '$(date); sleep 60; done"
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

echo "Demo service created successfully"
