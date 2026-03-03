#!/bin/bash
set -e

echo "Setting up systemctl wrapper..."

# Install the wrapper script
mkdir -p /opt/local/bin
cp /tmp/docker-assets/systemctl-wrapper.sh /opt/local/bin/systemctl-wrapper.sh
chmod +x /opt/local/bin/systemctl-wrapper.sh

# Create systemctl wrapper at /usr/local/bin
cat > /usr/local/bin/systemctl << 'EOF'
#!/bin/bash
# Wrapper that delegates to the service control script for demo-service
# Usage: systemctl [action] [service|options]
if [[ "$*" == *"demo-service"* ]]; then
  # Extract action (first argument) and service (second argument)
  ACTION="$1"
  SERVICE="$2"
  exec /opt/local/bin/systemctl-wrapper.sh system "$ACTION" "$SERVICE"
else
  echo "This container only supports management of demo-service" >&2
  exit 1
fi
EOF

chmod +x /usr/local/bin/systemctl

echo "Systemctl wrapper installed successfully"
