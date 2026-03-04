//usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//RUNTIME_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//DEPS io.quarkus.platform:quarkus-bom:3.15.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.apache.camel.quarkus:camel-quarkus-ssh:3.15.0
//FILES .pg-ssh-config.properties

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=ERROR
//Q:CONFIG quarkus.log.min-level=TRACE
//Q:CONFIG quarkus.log.console.level=TRACE
//Q:CONFIG quarkus.log.console.format=%d{HH:mm:ss.SSS} %-5p [%c{1.}] (%t) %m%n
//Q:CONFIG quarkus.log.category."pgsshconfig".level=DEBUG

package pgsshconfig;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.PropertiesDefaultProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.Map;
import java.util.LinkedHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Command(
    name = "pg-ssh-config",
    defaultValueProvider = PropertiesDefaultProvider.class,
    mixinStandardHelpOptions = true
)
public class PgSshConfigurer implements Runnable {

    private static final Logger LOG = Logger.getLogger(PgSshConfigurer.class.getName());

    @Inject
    ConfigurationOrchestrationService orchestrationService;

    @Option(
        names = "--ssh-host",
        description = "SSH host address",
        descriptionKey = "ssh.host",
        required = true
    )
    String sshHost;

    @Option(
        names = "--ssh-port",
        description = "SSH port number (1-65535)",
        descriptionKey = "ssh.port",
        required = true
    )
    int sshPort;

    @Option(
        names = "--ssh-user",
        description = "SSH username",
        descriptionKey = "ssh.user",
        required = true
    )
    String sshUsername;

    @Option(
        names = "--ssh-password",
        description = "SSH password",
        descriptionKey = "ssh.password",
        required = true,
        interactive = true
    )
    String sshPassword;

    @Option(
        names = "--config-file",
        description = "PostgreSQL configuration file path",
        descriptionKey = "config.file",
        required = true
    )
    String configFilePath;

    @Option(
        names = "--set",
        description = "PostgreSQL config settings (key=value pairs separated by semicolons)",
        descriptionKey = "set",
        split = ";",
        splitSynopsisLabel = ";"
    )
    Map<String, String> desiredProperties = new LinkedHashMap<>();

    @Option(
        names = "--service",
        description = "Service name to restart after configuration changes",
        descriptionKey = "service",
        defaultValue = "demo-service"
    )
    String targetService;

    @Override
    public void run() {
        try {
            SshConnectionConfig sshConfig = new SshConnectionConfig(
                sshHost,
                sshPort,
                sshUsername,
                sshPassword,
                configFilePath,
                desiredProperties,
                targetService
            );

            orchestrationService.orchestrate(sshConfig);

        } catch (IOException e) {
            LOG.severe("Error during execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

record SshConnectionConfig(
    String host,
    int port,
    String username,
    String password,
    String configFilePath,
    Map<String, String> desiredProperties,
    String targetService
) {}

@ApplicationScoped
class ConfigurationOrchestrationService {
    private static final Logger LOG = Logger.getLogger(ConfigurationOrchestrationService.class.getName());

    @Inject
    SshManager sshManager;

    @Inject
    ConfigFileManager configFileManager;

    public void orchestrate(SshConnectionConfig sshConfig) throws IOException {
        try {
            // 1. Print Configuration
            LOG.info("Configuration: ssh.host=" + sshConfig.host() + 
                ", ssh.port=" + sshConfig.port() + 
                ", config.file=" + sshConfig.configFilePath() + 
                ", desired.properties=" + sshConfig.desiredProperties().size());

            // 2. Connect SSH
            LOG.info("Connecting to SSH server at " + sshConfig.host() + ":" + sshConfig.port() + 
                " as user " + sshConfig.username() + "...");
            sshManager.connect(
                sshConfig.host(),
                sshConfig.port(),
                sshConfig.username(),
                sshConfig.password()
            );

            // 3. Show Original File
            LOG.info("Reading current config from " + sshConfig.configFilePath());
            String originalContent = configFileManager.readFile(sshConfig.configFilePath());
            LOG.info(originalContent);

            // 4. Reconcile Properties
            for (Map.Entry<String, String> entry : sshConfig.desiredProperties().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                PropertyReconciliationResult result = 
                    configFileManager.reconcileProperty(sshConfig.configFilePath(), key, value);
                
                LOG.info("set " + key + "=" + value + " (" + result.action() + ")");
            }

            // 5. Show Updated File
            LOG.info("Final config from " + sshConfig.configFilePath());
            String updatedContent = configFileManager.readFile(sshConfig.configFilePath());
            LOG.info(updatedContent);

            // 6. Restart Service
            LOG.info("Restarting service: " + sshConfig.targetService());
            sshManager.restartService(sshConfig.targetService());
            LOG.info("Service restarted: " + sshConfig.targetService());

        } finally {
            sshManager.disconnect();
        }
    }
}

record PropertyReconciliationResult(String action) {}

@ApplicationScoped
class SshManager {
    private static final Logger LOG = Logger.getLogger(SshManager.class.getName());
    
    @Inject
    CamelContext camelContext;
    
    @Inject
    ProducerTemplate producerTemplate;
    
    private String sshEndpoint;
    private String password;

    public void connect(String host, int port, String username, String password) throws IOException {
        this.password = password;
        // Build SSH endpoint URI for Apache Camel
        // Format: ssh://username@host:port?certResource=&useFixedDelay=true&delay=5000&pollCommand=...
        this.sshEndpoint = String.format("ssh:%s@%s:%d?password=RAW(%s)&timeout=30000", 
            username, host, port, password);
        LOG.fine("SSH endpoint configured: ssh:" + username + "@" + host + ":" + port);
    }

    public String executeCommand(String command) throws IOException {
        if (sshEndpoint == null) {
            throw new IllegalStateException("SSH not connected");
        }

        try {
            // Send command to SSH endpoint and get result
            String result = producerTemplate.requestBody(sshEndpoint, command, String.class);
            LOG.fine("Command: " + command);
            LOG.fine("Output: " + result);
            return result != null ? result : "";
        } catch (Exception e) {
            LOG.severe("Command execution failed: " + e.getMessage());
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        }
    }

    public String executeSudoCommand(String command) throws IOException {
        // Use printf to provide password to sudo via stdin for unattended execution
        // Wrap the command in bash -c to ensure the entire command runs with sudo
        String sudoCommand = String.format("printf '%s\\n' | sudo -S bash -c \"%s\" 2>&1", 
            password, command.replace("\"", "\\\""));
        return executeCommand(sudoCommand);
    }

    public void restartService(String serviceName) throws IOException {
        LOG.info("Restarting service: " + serviceName);
        String command = "systemctl restart " + serviceName;
        executeSudoCommand(command);
        LOG.fine("Service " + serviceName + " restarted successfully");
    }

    public void disconnect() {
        // Camel manages connections through its lifecycle, no explicit disconnect needed
        LOG.fine("SSH connection closed (managed by Camel)");
    }
}

@ApplicationScoped
class ConfigFileManager {
    private static final Logger LOG = Logger.getLogger(ConfigFileManager.class.getName());

    @Inject
    SshManager sshManager;

    /**
     * Read file contents and return as a string
     */
    public String readFile(String filePath) throws IOException {
        LOG.info("[READ_FILE] Reading file: " + filePath);
        try {
            String content = sshManager.executeSudoCommand("cat " + filePath);
            LOG.info("[READ_FILE_SUCCESS] File read successfully, length: " + content.length() + " bytes");
            return content;
        } catch (IOException e) {
            LOG.severe("[READ_FILE_ERROR] Failed to read file: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reconcile a property: update if exists, add if doesn't exist
     * Returns a result indicating the action taken (updated, added, or unchanged)
     */
    public PropertyReconciliationResult reconcileProperty(String filePath, String propertyName, String value) throws IOException {
        LOG.info("[RECONCILE_START] ===== RECONCILING PROPERTY =====");
        LOG.info("[RECONCILE_START] Property name: " + propertyName);
        LOG.info("[RECONCILE_START] Desired value: " + value);
        LOG.info("[RECONCILE_START] File path: " + filePath);
        
        // Get current value to determine if this will be an add, update, or no-op
        String currentValue = getPropertyValue(filePath, propertyName);
        LOG.info("[RECONCILE_CHECK] Current value: " + (currentValue == null ? "NOT_FOUND" : currentValue));
        
        // If value already matches, no action needed
        if (currentValue != null && currentValue.equals(value)) {
            LOG.info("[RECONCILE_DECISION] Property already has correct value - NO CHANGE");
            LOG.info("[RECONCILE_END] ===== RECONCILIATION COMPLETE (unchanged) =====");
            return new PropertyReconciliationResult("unchanged");
        }
        
        // Determine action: 'added' if property doesn't exist, 'updated' if it does
        String action = currentValue == null ? "added" : "updated";
        LOG.info("[RECONCILE_DECISION] Action to perform: " + action);
        
        // Step 1: Remove any existing line with this property (using sed -i deletion)
        LOG.info("[RECONCILE_REMOVE] Removing any existing line for property: " + propertyName);
        try {
            String sedRemoveCommand = String.format("sed -i '/^%s\\s*=/d' %s", propertyName, filePath);
            LOG.fine("[RECONCILE_REMOVE_CMD] Executing: " + sedRemoveCommand);
            sshManager.executeSudoCommand(sedRemoveCommand);
            LOG.info("[RECONCILE_REMOVE_SUCCESS] Line removed or was not present");
        } catch (IOException e) {
            LOG.severe("[RECONCILE_REMOVE_ERROR] Failed to remove property line: " + e.getMessage());
            throw e;
        }
        
        // Step 2: Append the new property value
        LOG.info("[RECONCILE_ADD] Adding/appending property: " + propertyName + " = " + value);
        try {
            String echoCommand = String.format("echo '%s = %s' | tee -a %s > /dev/null", propertyName, value, filePath);
            LOG.fine("[RECONCILE_ADD_CMD] Executing: " + echoCommand);
            sshManager.executeSudoCommand(echoCommand);
            LOG.info("[RECONCILE_ADD_SUCCESS] Property added successfully");
        } catch (IOException e) {
            LOG.severe("[RECONCILE_ADD_ERROR] Failed to add property: " + e.getMessage());
            throw e;
        }
        
        // Verify the property was set correctly
        LOG.info("[RECONCILE_VERIFY] Verifying property was set correctly");
        String verifyValue = getPropertyValue(filePath, propertyName);
        LOG.info("[RECONCILE_VERIFY_RESULT] Verification - property now has value: " + (verifyValue == null ? "NOT_FOUND" : verifyValue));
        if (verifyValue == null || !verifyValue.equals(value)) {
            LOG.warning("[RECONCILE_VERIFY_FAILED] Property verification failed! Expected: " + value + ", Got: " + verifyValue);
        }
        
        LOG.info("[RECONCILE_END] ===== RECONCILIATION COMPLETE (" + action + ") =====");
        return new PropertyReconciliationResult(action);
    }

    /**
     * Get the current value of a property
     */
    public String getPropertyValue(String filePath, String propertyName) throws IOException {
        LOG.fine("[GET_VALUE] Retrieving value for property: " + propertyName);
        try {
            String command = String.format("grep '^%s\\s*=' %s | sed 's/^%s\\s*=\\s*//' | head -1", 
                propertyName, filePath, propertyName);
            LOG.fine("[GET_VALUE_CMD] Executing: " + command);
            String result = sshManager.executeSudoCommand(command).trim();
            String resultDisplay = result.isEmpty() ? "EMPTY/NOT_FOUND" : result;
            LOG.fine("[GET_VALUE_RESULT] Property [" + propertyName + "] = [" + resultDisplay + "]");
            return result.isEmpty() ? null : result;
        } catch (IOException e) {
            LOG.warning("[GET_VALUE_ERROR] Failed to get property value for [" + propertyName + "]: " + e.getMessage());
            return null;
        }
    }
}
