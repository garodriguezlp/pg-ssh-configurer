#!/bin/bash
set -e

echo "Setting up demo user..."

# Create a normal user with password and sudo
useradd -m -s /bin/bash demo
echo 'demo:demo' | chpasswd
usermod -aG sudo demo
echo 'demo ALL=(ALL) ALL' > /etc/sudoers.d/demo
chmod 0440 /etc/sudoers.d/demo

echo "Demo user created successfully"
