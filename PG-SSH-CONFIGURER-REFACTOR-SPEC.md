# pg-ssh-configurer v2 Refactor Specification

## Overview

Refactor [PgSshConfigurer.java](PgSshConfigurer.java) with a new layer of orchestration that implements the property reconciliation workflow defined in [PG-SSH-CONFIG-SPEC.md](PG-SSH-CONFIG-SPEC.md). This refactor adds the Picocli Map-based `--set` option and implements a clean, step-by-step orchestration flow while preserving all existing low-level abstractions.

---

## Goals

- ✅ Introduce the `--set` option using Picocli Map with semicolon split (per [PICOCLI_MAP_SOLUTION.md](PICOCLI_MAP_SOLUTION.md))
- ✅ Add orchestration layer that coordinates the workflow without disrupting existing SSH/config abstractions
- ✅ Implement professional step-by-step logging at each orchestration stage
- ✅ Keep orchestration method short, readable, and focused on business flow

---

## Architecture

### Preservation of Existing Layers

The refactor **does not modify** low-level abstractions:

- `SshManager` — unchanged command execution wrapper
- `ConfigFileManager` — unchanged file read/write operations
- Existing SSH connection/authentication logic — unchanged
- All Picocli option handling for SSH parameters — unchanged

### New Orchestration Layer

A single new layer sits above existing abstractions and coordinates the workflow:

- **`ConfigurationOrchestrationService`** — orchestrates the 6-step workflow
  - Delegates SSH operations to `SshManager`
  - Delegates file operations to `ConfigFileManager`
  - Handles iteration and logging at each step
  - Remains independent of Picocli details

---

## Workflow (6 Steps)

The orchestration service executes this flow:

1. **Print Configuration** — Display all bound Picocli options (SSH settings, file path, desired properties)
2. **Connect SSH** — Establish SSH session (low-level detail, handled by `SshManager`)
3. **Show Original File** — Print target config file contents before any changes
4. **Reconcile Properties** — Iterate desired properties and update/add each to the file
5. **Show Updated File** — Print target config file contents after reconciliation
6. **Restart Service** — Execute service restart command (via `SshManager`)

### Logging at Each Step

| Step | Log Level | Example Message |
|:-----|:---|:---|
| Print config | `INFO` | `Configuration: ssh.host=localhost, ssh.port=2223, config.file=/etc/postgresql/16/main/postgresql.conf, desired.properties=3` |
| Connect SSH | `INFO` | `Connecting to SSH server at localhost:2223 as user demo...` |
| Show original | `INFO` | `Reading current config from /etc/postgresql/16/main/postgresql.conf` |
| Reconcile each | `INFO` | `set key=value (updated)` / `set key=value (added)` / `set key=value (unchanged)` |
| Show updated | `INFO` | `Final config from /etc/postgresql/16/main/postgresql.conf` |
| Restart service | `INFO` | `Restarting service: demo-service` |
|  | `INFO` | `Service restarted: demo-service` |

---

## New Picocli Option

### Definition

Add a Map-based `--set` option as defined in [PG-SSH-CONFIG-SPEC.md](PG-SSH-CONFIG-SPEC.md):

```java
@Option(
    names = "--set",
    description = "PostgreSQL config settings (key=value pairs separated by semicolons)",
    descriptionKey = "set",
    split = ";",
    splitSynopsisLabel = ";"
)
Map<String, String> desiredProperties = new LinkedHashMap<>();
```

### Properties File Support

The `--set` option integrates with `PropertiesDefaultProvider`:

- **Properties file entry**: `set=listen_addresses=*;max_connections=200;shared_buffers=256MB`
- **CLI override**: `--set "listen_addresses=127.0.0.1;port=5433"`
- **Precedence**: CLI value overrides properties file value

### Service Restart Configuration

Add a new Picocli option for the target service to restart:

```java
@Option(
    names = "--service",
    description = "Service name to restart after configuration changes",
    defaultValue = "demo-service"
)
String targetService;
```

---

## Implementation Notes

### Orchestration Method Pseudocode

```java
public void orchestrateConfigurationChange() {
    // 1. Print configuration
    logger.info("Configuration: {}", buildConfigurationSummary());
    
    try {
        // 2. Connect SSH (handled by SshManager)
        sshManager.connect(sshHost, sshPort, sshUser, sshPassword);
        
        // 3. Show original file
        String originalContent = configFileManager.readFile(configFilePath);
        logger.info("Reading current config from {}", configFilePath);
        printConfigSnapshot(originalContent);
        
        // 4. Reconcile properties
        for (Map.Entry<String, String> entry : desiredProperties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            PropertyReconciliationResult result = 
                configFileManager.reconcileProperty(configFilePath, key, value);
            
            logger.info("set {}={} ({})", key, value, result.getAction());
        }
        
        // 5. Show updated file
        String updatedContent = configFileManager.readFile(configFilePath);
        logger.info("Final config from {}", configFilePath);
        printConfigSnapshot(updatedContent);
        
        // 6. Restart service
        logger.info("Restarting service: {}", targetService);
        sshManager.executeCommand("systemctl restart " + targetService);
        logger.info("Service restarted: {}", targetService);
        
    } finally {
        sshManager.disconnect();
    }
}
```

### No Low-Level Changes

- Do **not** modify `SshManager` command execution logic
- Do **not** modify `ConfigFileManager` file I/O operations
- Do **not** modify existing Picocli option definitions for SSH parameters
- Only **add** new orchestration layer and properties reconciliation logic

---

## Backward Compatibility

The refactor maintains full backward compatibility:

- Existing command-line options remain unchanged
- Properties file format for SSH parameters remains unchanged
- SSH connection behavior remains unchanged
- All existing abstractions remain intact and unchanged

---

## Acceptance Criteria

- [ ] `--set` option accepts Picocli Map input with semicolon-delimited pairs
- [ ] Properties file defaults work with `PropertiesDefaultProvider`
- [ ] Configuration summary printed before any operations begin
- [ ] Original config file contents displayed before reconciliation
- [ ] Each property reconciliation produces exactly one log line (added/updated/unchanged)
- [ ] Updated config file contents displayed after all reconciliation
- [ ] Service restart command executes successfully
- [ ] All logging is clear, concise, and action-oriented
- [ ] Orchestration method reads like business steps, not implementation details
- [ ] Existing SSH/config abstractions remain completely unchanged
