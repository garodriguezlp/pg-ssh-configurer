# pg-ssh-configurer vNext Specification

## 1) Purpose

Define a **clean, minimal, professional** behavior for `pg-ssh-config` where the user provides PostgreSQL settings as repeatable `property=value` pairs and the tool guarantees that those pairs are the final state in the target config file.

This spec is proposal-only. **No code changes are included.**

---

## 2) Goals

- Accept repeatable property assignments via Picocli options.
- Support existing properties-file defaults and allow CLI overrides.
- Enforce end-state semantics: for each requested property, resulting file must contain exactly the requested value.
- Keep orchestration simple and readable:
  1. print file (before)
  2. iterate desired properties and reconcile each
  3. print file (after)
  4. restart service
- Reduce verbosity of logs to meaningful, operator-friendly lines.

---

## 3) Non-Goals

- Full PostgreSQL syntax parsing.
- Bulk deletion of unspecified properties.
- Templating or complex config transformations.
- Support for advanced quoting/escaping beyond a practical MVP.

---

## 3.5) PoC Findings: Properties File Backing for Repeatable Options

**Date:** 2026-03-04

### Solution Found: Picocli Map with Split ✅

A **superior solution** was discovered that uses Picocli's native **Map option with split attribute**. This completely avoids the repeatable option limitations while remaining elegant and maintainable.

**Test Results:** All scenarios passed ✅

| Approach | Properties Backed | CLI Works | Code Needed | Status |
|:---------|:---|:---|:---|:---|
| Repeatable Options | ❌ FAIL (0 values) | ✅ Works | Minimal | Rejected |
| Delimited String | ✅ Works | ✅ Works | ~15 lines | Viable |
| **Map with Split** | ✅ **Works** | ✅ **Works** | **0 lines** | **RECOMMENDED** ✨ |

### Picocli Map with Split Solution

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

**Why this is superior:**
- ✅ Picocli handles all parsing (no custom code)
- ✅ Type-safe: Returns `Map<String, String>`
- ✅ Automatic validation: Ensures key=value format
- ✅ Auto-generated help text with clear synopsis
- ✅ Works perfectly with `PropertiesDefaultProvider`
- ✅ Zero parsing logic to maintain

### Test Proof

1. **Properties file default** → Parsed 3 properties correctly ✅
2. **CLI override** → Parsed 2 properties correctly ✅
3. **Single property** → Parsed 1 property correctly ✅
4. **Help text** → Auto-generated, clear and accurate ✅

Generated Help Output:
```
--set=<String=String>[;<String=String>...]
      Property settings as key=value pairs separated by semicolons:
        key1=val1;key2=val2
```

### Recommended CLI Usage

```bash
./PgSshConfigurer \
  --ssh-host localhost \
  --config-file /etc/postgresql/16/main/postgresql.conf \
  --set "listen_addresses=*;max_connections=200;shared_buffers=256MB"
```

### Recommended Properties File Format

```properties
# .pg-ssh-config.properties
set=listen_addresses=*;max_connections=200;shared_buffers=256MB
```

### Key Decision: Semicolon as Delimiter

Semicolon (`;`) is optimal because:
- Rarely appears in PostgreSQL config values
- Shell-safe (doesn't trigger pipes or redirects)
- Consistent with SQL statement termination
- Works in properties files without escaping

See [PICOCLI_MAP_SOLUTION.md](PICOCLI_MAP_SOLUTION.md) for complete PoC results and implementation details.

---

## 4) CLI Contract (Proposed)

## 4.1 Existing required options (keep)

- `--ssh-host`
- `--ssh-port`
- `--ssh-user`
- `--ssh-password`
- `--config-file`

## 4.2 New property settings option (Map-based)

Introduce one Map-based option with semicolon-delimited key=value pairs:

- `--set key1=value1;key2=value2`

This approach (PoC-validated) leverages Picocli's native Map support with automatic parsing and validation.

Examples:

```bash
./PgSshConfigurer.java \
  --ssh-host localhost \
  --ssh-port 2223 \
  --ssh-user demo \
  --ssh-password \
  --config-file /etc/postgresql/16/main/postgresql.conf \
  --set "listen_addresses=*;max_connections=200;shared_buffers=256MB"
```

### Picocli-friendly form

Single `--set` with semicolon-separated pairs:
- `--set "k1=v1;k2=v2;k3=v3"`

### Validation constraints

- Each pair must contain exactly one `=` separator.
- Key and value (both sides of `=`) must be non-empty after trim.
- Values may contain special characters except unescaped semicolons.
- Semicolons inside values can be escaped with backslash.
- Invalid entries fail fast with clear error message.

### Advantages of Map-based approach

- ✅ **Picocli-native**: Uses Picocli's standard Map parser
- ✅ **Zero custom parsing**: No custom split/parse logic needed
- ✅ **Auto-validated**: Picocli ensures key=value format
- ✅ **Type-safe**: Returns `Map<String, String>`
- ✅ **Properties-backed**: Works seamlessly with `PropertiesDefaultProvider`
- ✅ **Auto-documented**: Help text auto-generated with clear format

---

## 5) Properties File Backing & Precedence

**✅ SOLUTION: The Map-based approach works perfectly with `PropertiesDefaultProvider` (PoC-validated in section 3.5).**

### Recommended Precedence

1. **CLI `--set` entries** (highest precedence)
2. **Properties file** `set=` value via `PropertiesDefaultProvider` (fallback)
3. If neither present: no desired settings

### How It Works

Picocli's `PropertiesDefaultProvider` automatically loads the `set` property value from `.pg-ssh-config.properties` and parses it using the Map split attribute.

**Properties file:**
```properties
set=listen_addresses=*;max_connections=200;shared_buffers=256MB
```

Picocli automatically converts this to a Map with three key-value pairs.

**CLI override:**
```bash
./PgSshConfigurer --set "listen_addresses=127.0.0.1;port=5433"
# Completely overrides the properties file value
```

### Properties File (Current Scope: SSH Connection Only)

Existing properties file support applies only to SSH connection parameters:

```properties
# SSH Connection Parameters (continue to use properties file for these)
ssh.host=localhost
ssh.port=2223
ssh.user=demo
ssh.password=demo
config.file=/etc/postgresql/16/main/postgresql.conf

# Repeatable property settings are NOT supported in properties file
# Use --set via command line instead
```

---

## 6) End-State Reconciliation Semantics

For each desired `property=value` entry:

1. Detect whether property exists as active assignment line.
2. If exists and value differs → update line.
3. If exists and value already matches → no-op.
4. If not exists → append new assignment line.

### Determinism rules

- If duplicate key appears multiple times in desired input, **last one wins**.
- Keys are compared exactly after trim (case-sensitive by default).
- Preserve comments and unrelated lines.
- Do not remove properties unless explicit future feature is added.

### Matching scope (MVP)

- Match active lines in form `^\s*key\s*=`
- Ignore commented lines beginning with optional whitespace + `#`.

---

## 7) Orchestration Flow (Clean & Minimal)

Target orchestration pseudoflow:

1. Connect SSH
2. Print file (before)
3. Build desired-properties map (apply precedence + deduplicate last-wins)
4. Iterate entries in stable order:
   - reconcile one property
   - log concise action
5. Print file (after)
6. Restart target service
7. Disconnect SSH in `finally`

This keeps orchestration at a high level and delegates details to focused collaborators.

---

## 8) Proposed Internal Abstractions

Keep abstractions small and explicit (no overengineering):

- `DesiredPropertyParser`
  - Parse `--set` inputs into typed entries.
  - Validate format.

- `PropertyReconciler`
  - Reconcile one `key/value` against file.
  - Decide `ADD`, `UPDATE`, or `NOOP`.

- `ConfigSnapshotPrinter`
  - Print file before and after.

- `ConfigurationOrchestrationService`
  - Only orchestration/iteration flow; no low-level command details.

- Existing `SshManager`
  - Keep as command executor boundary.

- Existing `ConfigFileManager`
  - Keep low-level file commands; possibly expose a single `reconcileProperty(...)` entry.

### Professional readability target

- Orchestration method should read almost like business steps.
- Per-step logic should be delegated to named methods/classes.
- Avoid introducing a deep class hierarchy.

---

## 9) Logging Contract (Less Verbose)

### Before changes

- `INFO  Reading current config: <path>`
- Print file contents once.

### During iteration (one line each)

- `INFO  set <key>=<value> (added)`
- `INFO  set <key>=<value> (updated)`
- `INFO  set <key>=<value> (unchanged)`

### After changes

- `INFO  Final config: <path>`
- Print file contents once.
- `INFO  Restarting service: <service>`
- `INFO  Service restarted: <service>`

No noisy section banners are required.

---

## 10) Error Handling

- Invalid `--set` format: fail before SSH connection.
- SSH/connect failures: return clear message and exit non-zero.
- File read/write failures: include command context and stderr.
- Restart failure after successful edits:
  - report failure clearly
  - keep edited file as-is (no rollback in MVP)

---

## 11) Acceptance Criteria

1. User can pass multiple `--set key=value` options.
2. Tool prints config file before and after reconciliation.
3. For each desired property, final file contains requested value.
4. Existing property is updated instead of duplicated.
5. Missing property is added.
6. Duplicate desired keys resolve using last-wins.
7. Logs are concise and action-oriented.
8. Orchestration method remains short, readable, and iterative.

---

## 12) Example Session (Expected Operator Experience)

Input:

```bash
--set "listen_addresses=*" --set "max_connections=200" --set "max_connections=250"
```

Behavior:

- Print initial file
- Reconcile:
  - `listen_addresses=*` → updated
  - `max_connections=250` → added/updated (last wins over 200)
- Print final file
- Restart service

Logs (shape):

```text
INFO Reading current config: /etc/postgresql/16/main/postgresql.conf
INFO set listen_addresses=* (updated)
INFO set max_connections=250 (updated)
INFO Final config: /etc/postgresql/16/main/postgresql.conf
INFO Restarting service: demo-service
INFO Service restarted: demo-service
```

---

## 13) Open Decisions for Implementation Review

1. Confirm option name: `--set` vs `--property`.
2. Confirm service restart target source:
   - fixed (`demo-service`) or
   - configurable via option/property.
3. **[RESOLVED BY POC]** ~~Confirm repeatable defaults format in properties file~~ 
   - **Decision**: Use Picocli Map with split attribute.
   - **Why**: PoC proved it works perfectly with `PropertiesDefaultProvider`.
   - **Benefit**: Zero custom parsing code, type-safe, auto-validated, auto-documented.
   - See [PICOCLI_MAP_SOLUTION.md](PICOCLI_MAP_SOLUTION.md) for complete PoC results.
4. Confirm key case sensitivity policy (default: case-sensitive).

---

## 14) Implementation Outline (When Approved)

1. Add Map-based Picocli option for desired properties (`--set`):
   ```java
   @Option(names = "--set", split = ";")
   Map<String, String> desiredProperties = new LinkedHashMap<>();
   ```
2. Properties file format: `set=key1=val1;key2=val2;key3=val3`
3. Refactor orchestration to strict 4-step flow (print, iterate, print, restart).
4. Introduce concise reconcile operation that chooses add/update/no-op.
5. Iterate directly over `desiredProperties.entrySet()` (no parsing needed).
6. Trim logging to action lines.
7. Validate behavior against the demo container.
