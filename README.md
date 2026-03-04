
# PostgreSQL SSH Configurer

A JBang-based Java application for remotely modifying PostgreSQL configuration files via SSH. Includes a Docker-based demo environment that simulates a PostgreSQL server for testing and learning purposes.

## Overview

This project consists of two main components:

1. **JBang Application**: A command-line tool that connects to a remote server via SSH and modifies PostgreSQL configuration files
2. **Docker Demo Environment**: A containerized Ubuntu SSH server that simulates a PostgreSQL setup, complete with protected config files and systemctl service management

## Use Cases

- **Learning**: Understand how to programmatically manage PostgreSQL configuration files over SSH
- **Automation**: Automate PostgreSQL configuration changes on remote servers without manual SSH sessions
- **Testing**: Practice configuration management in a safe, isolated environment before deploying to production

## Technology Stack

- **JBang**: Zero-friction Java scripting with dependency management
- **Quarkus**: Modern Java framework for fast startup and low memory footprint
- **Picocli**: Command-line interface with configuration file support
- **Apache Camel SSH**: SSH connectivity and command execution
- **Docker**: Containerized demo environment with Ubuntu 24.04

## Requirements

- **Java 17+**: For running the JBang application
- **JBang**: Install from [jbang.dev](https://www.jbang.dev)
- **Docker & Docker Compose**: For running the demo environment

## How to Use

### 1. Start the Demo Environment

```bash
docker-compose up -d
```

Verify the container is running:
```bash
docker ps | grep ubuntu-ssh-demo
```

### 2. Configure Connection Settings

Edit [.pg-ssh-config.properties](./.pg-ssh-config.properties) to customize:

```properties
ssh.host=localhost
ssh.port=2223
ssh.user=demo
ssh.password=demo
config.file=/etc/postgresql/16/main/postgresql.conf
set=listen_addresses=*;max_connections=400;shared_buffers=256MB
service=demo-service
```

### 3. Run the Configurer

```bash
jbang PgSshConfigurer.java
```

The application will:
- Connect to the SSH server
- Modify the PostgreSQL configuration file with your settings
- Restart the PostgreSQL service (if specified)

### 4. Verify Manually (Optional)

Connect to the demo container to verify changes:

```bash
ssh -p 2223 demo@localhost
# Password: demo
```

View the configuration:
```bash
cat /etc/postgresql/16/main/postgresql.conf
```

Test sudo privileges for service management:
```bash
sudo systemctl status demo-service
# Password: demo
```

Exit:
```bash
exit
```

## Configuration Options

All options can be set via command-line flags or the `.pg-ssh-config.properties` file:

- `--ssh-host`: SSH server hostname or IP
- `--ssh-port`: SSH port (default: 2223)
- `--ssh-user`: SSH username
- `--ssh-password`: SSH password
- `--config-file`: Path to PostgreSQL configuration file on remote server
- `--set`: Configuration parameters to set (semicolon-separated key=value pairs)
- `--service`: Service name to restart after configuration changes

## Clean Up

Stop and remove the demo container:

```bash
docker-compose down
```

Reset with a fresh build:
```bash
docker-compose down
docker-compose up -d --build
```

## Demo Credentials

- **Username**: demo
- **Password**: demo
- **SSH Port**: 2223
- **Sudo Access**: Yes (password required)