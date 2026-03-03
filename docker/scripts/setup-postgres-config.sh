#!/bin/bash
set -e

echo "Setting up fake PostgreSQL configuration..."

# Fake "postgres config" that requires sudo to edit
mkdir -p /etc/postgresql/16/main
printf "# demo config\nlisten_addresses = '*'\n" > /etc/postgresql/16/main/postgresql.conf
chown root:root /etc/postgresql/16/main/postgresql.conf
chmod 0644 /etc/postgresql/16/main/postgresql.conf

echo "PostgreSQL configuration created successfully"
