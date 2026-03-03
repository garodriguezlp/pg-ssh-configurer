
# PostgreSQL SSH Configurer Specification

## Overview
Build a single-file Quarkus JBang application (Java 17+) that automates remote configuration file management over SSH with sudo privileges.

## Objectives
- Connect to a remote host via SSH with automated, unattended execution
- Read and modify configuration files with sudo privileges
- Provide a clean abstraction for SSH operations and file configuration management
- Demonstrate the full lifecycle: view, check, update, and remove configuration properties

## Technical Requirements

### Stack
- **Framework**: Quarkus 3.15.1+
- **Build Tool**: JBang (single-file executable)
- **Java Version**: 17+
- **Quarkus Extensions**: quarkus-ssh, quarkus-picocli
- **Logging**: JBoss LogManager via Quarkus logging configuration
- **Dependency Injection**: Quarkus Arc

### Authentication
- SSH password provided via stdin using `printf <password> | sudo -S` pattern
- Enables fully automated, unattended command execution

## Architecture

### 1. SSH Abstraction
Responsible for:
- SSH connection management and context initialization
- Command execution with sudo capabilities
- Error handling and connection lifecycle

### 2. Config File Management Abstraction
Responsible for:
- **View**: Print file contents
- **Check**: Verify property existence
- **Set**: Update property value
- **Add**: Insert new property if not present
- **Remove**: Delete property from file

## Execution Flow

1. Print SSH connection details (host, port, user)
2. Display target configuration file content
3. Verify a property exists in the file
4. Update the property with a new value, then display result
5. Add a new property to the file
6. Remove a property from the file
7. Print final configuration state

Properties and file paths are hardcoded for demonstration purposes.

## Configuration

### JBang Header
```
#!/usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//RUNTIME_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
```

### Quarkus Logging Config
- Banner disabled
- Root log level: WARN
- Console level: TRACE
- Format: `%d{HH:mm:ss.SSS} %-5p [%c{1.}] (%t) %m%n`

## Deliverable
Single Java file executable via `jbang run <file>.java`

## Sample config


//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.min-level=TRACE
//Q:CONFIG quarkus.log.console.level=TRACE
//Q:CONFIG quarkus.log.console.format=%d{HH:mm:ss.SSS} %-5p [%c{1.}] (%t) %m%n
//Q:CONFIG quarkus.log.category."dev.gustavo.qlogging".level=INFO