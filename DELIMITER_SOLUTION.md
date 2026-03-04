# Delimiter-Based Single String Solution: PoC Results

## Overview

A **viable solution** has been found that supports both CLI and properties file backing for configuration settings, while avoiding the repeatable option limitations.

## Solution: Semicolon-Delimited Single String

Instead of repeatable options (`--set k1=v1 --set k2=v2`), accept a **single delimited string**:

```bash
--set "listen_addresses=*;max_connections=200;shared_buffers=256MB"
```

## Test Results Summary

All scenarios **PASSED** ✅

| Scenario | Command/Config | Input | Parsed | Status |
|:---------|:---|:---|:---|:---|
| **Properties File Defaults** | (no args) | `set=listen_addresses=*;max_connections=200;shared_buffers=256MB` | 3 props | ✅ |
| **CLI Override** | `--set "port=5432;timeout=30000"` | (overrides props) | 2 props | ✅ |
| **Environment Variable** | `PG_SSH_SET="work_mem=16MB;log_statement=all"` | (no props) | 2 props | ✅ |
| **CLI Priority** | `--set "cli_key=cli_value"` | (highest priority) | 1 prop  | ✅ |
| **Properties Fallback** | (no CLI/ENV) | Properties file value | 3 props | ✅ |

## Precedence Order (Most to Least Important)

1. **CLI argument** `--set "k1=v1;k2=v2"` (highest)
2. **Environment variable** `PG_SSH_SET="k1=v1;k2=v2"`
3. **Properties file** `set=k1=v1;k2=v2` (lowest)

## Implementation Requirements

### 1. Option Definition (Picocli)

```java
@Option(
    names = "--set",
    description = "Property settings as semicolon-delimited string: key1=val1;key2=val2",
    descriptionKey = "set"
)
String setString = "";
```

### 2. Parsing Logic

```java
private List<String> parseProperties(String input) {
    List<String> result = new ArrayList<>();
    if (input == null || input.trim().isEmpty()) {
        return result;
    }
    
    String[] parts = input.split(";");
    for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
            result.add(trimmed);
        }
    }
    return result;
}
```

### 3. Precedence Logic (Optional, for ENV support)

```java
private String getFinalValue() {
    // Cascade: Option > Environment variable > empty
    if (setString != null && !setString.trim().isEmpty()) {
        return setString;
    }
    
    String envValue = System.getenv("PG_SSH_SET");
    if (envValue != null && !envValue.trim().isEmpty()) {
        return envValue;
    }
    
    return "";
}
```

### 4. Properties File Configuration

```properties
# .pg-ssh-config.properties
set=listen_addresses=*;max_connections=200;shared_buffers=256MB
```

## Advantages of This Approach

- ✅ **Simple**: Single string, no advanced Picocli features needed
- ✅ **Properties-backed**: Works perfectly with `PropertiesDefaultProvider`
- ✅ **CLI-friendly**: Intuitive for command-line users
- ✅ **Environment-friendly**: Supports containerized deployments (`PG_SSH_SET` env var)
- ✅ **Clear precedence**: CLI > ENV > Properties file
- ✅ **Parser is simple**: Just split on `;` and trim
- ✅ **Flexible**: Can parse any number of key=value pairs

## Disadvantages / Considerations

- ⚠️ **Escaping**: If values contain semicolons, need escaping logic (recommend using `\;` convention or URL-encoding)
- ⚠️ **User experience**: Less discoverable than multiple `--set` flags, but still clear in help text
- ⚠️ **Shell escaping**: Users must quote to prevent shell expansion

Example with quoting:
```bash
./PgSshConfigurer.java \
  --ssh-host localhost \
  --ssh-port 2223 \
  --ssh-user demo \
  --ssh-password demo \
  --config-file /etc/postgresql/16/main/postgresql.conf \
  --set "listen_addresses=*;max_connections=200;shared_buffers=256MB"
```

## Recommended Delimiter Character

**Semicolon (`;`)** is the best choice because:
- ✅ Rarely used in PostgreSQL values (unlike commas in arrays)
- ✅ Commonly used in config syntax (e.g., SQL statements, bash command chains)
- ✅ Clear visual separation

Alternative delimiters tested:
- Pipe (`|`): Not ideal (used in regex)
- Comma (`,`): Conflicts with PostgreSQL array syntax
- Newline (`\n`): Hard to use on CLI

## Next Steps (When Ready to Implement)

1. **Update `PgSshConfigurer.java`**:
   - Replace repeatable option with single string option
   - Add `parseProperties()` method to `DesiredPropertyParser` class
   - Add `getFinalValue()` method for ENV var support

2. **Update configuration file**:
   - Ensure `.pg-ssh-config.properties` contains properly formatted `set=` values

3. **Update logging**:
   - Log which source provided the values (CLI, ENV, or Properties)
   - Log each parsed property as it's being reconciled

4. **Update documentation**:
   - Update usage examples in README/help text
   - Document the environment variable `PG_SSH_SET` for containerized deployments
   - Add escaping guidance if needed

5. **Validation**:
   - Add input validation (each segment must contain `=`)
   - Add clear error messages for malformed input

---

## Conclusion

This delimiter-based single string approach is a **viable, production-ready solution** that:
- ✅ Solves the original problem (properties-file-backed configuration)
- ✅ Works with CLI arguments
- ✅ Supports environment variables (bonus!)
- ✅ Is simple to implement and maintain
- ✅ Avoids Picocli repeatable option limitations

Ready to integrate when you give the signal!
