//usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//RUNTIME_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//FILES .pg-ssh-config.properties

//DEPS io.quarkus.platform:quarkus-bom:3.32.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.apache.camel.quarkus:camel-quarkus-ssh:3.32.0

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=ERROR
//Q:CONFIG quarkus.log.min-level=TRACE
//Q:CONFIG quarkus.log.console.level=TRACE
//Q:CONFIG quarkus.log.console.format=%d{HH:mm:ss.SSS} %-5p [%c{1.}] (%t) %m%n
//Q:CONFIG quarkus.log.category."pgsshconfig".level=INFO

package pgsshconfig;

import org.apache.camel.ProducerTemplate;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.PropertiesDefaultProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(
    name = "pg-ssh-config",
    defaultValueProvider = PropertiesDefaultProvider.class,
    mixinStandardHelpOptions = true
)
public class pg_ssh_configurer implements Runnable {

    private static final Logger LOG = Logger.getLogger(pg_ssh_configurer.class.getName());

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

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose logging",
        defaultValue = "false"
    )
    boolean verbose;

    @Override
    public void run() {
        // Dynamically configure logging level based on verbose flag
        if (verbose) {
            setLoggingLevel(Level.FINE);
            LOG.info("Verbose logging enabled - FINE/DEBUG level");
        }
        try {
            SshConnectionDetails sshConnection = new SshConnectionDetails(
                sshHost,
                sshPort,
                sshUsername,
                sshPassword
            );

            PgConfigChangeRequest pgConfigRequest = new PgConfigChangeRequest(
                configFilePath,
                desiredProperties,
                targetService
            );

            ConfigurationRequest request = new ConfigurationRequest(sshConnection, pgConfigRequest);
            orchestrationService.orchestrate(request);

        } catch (IOException e) {
            LOG.severe("Error during execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void setLoggingLevel(Level level) {
        LogManager logManager = LogManager.getLogManager();

        // Loggers to configure for dynamic level changes
        Stream.of("pgsshconfig")
            .map(logManager::getLogger)
            .filter(Objects::nonNull)
            .forEach(logger -> logger.setLevel(level));
    }
}

record SshConnectionDetails(
    String host,
    int port,
    String username,
    String password
) {}

record PgConfigChangeRequest(
    String configFilePath,
    Map<String, String> desiredProperties,
    String targetService
) {}

record ConfigurationRequest(
    SshConnectionDetails ssh,
    PgConfigChangeRequest pgConfig
) {}

@ApplicationScoped
class ConfigurationOrchestrationService {
    private static final Logger LOG = Logger.getLogger(ConfigurationOrchestrationService.class.getName());

    @Inject
    SshManager sshManager;

    @Inject
    ConfigFileManager configFileManager;

    @Inject
    ExecutionReporter executionReporter;

    public void orchestrate(ConfigurationRequest request) throws IOException {
        executionReporter.printConfiguration(request);

        sshManager.initializeSession(
            request.ssh().host(),
            request.ssh().port(),
            request.ssh().username(),
            request.ssh().password()
        );

        showConfigFileSnapshot("Current config", request.pgConfig().configFilePath());
        reconcileDesiredProperties(request.pgConfig());
        showConfigFileSnapshot("Final config", request.pgConfig().configFilePath());
        restartTargetService(request.pgConfig().targetService());
    }

    private void showConfigFileSnapshot(String title, String filePath) throws IOException {
        LOG.info("Reading configuration file: " + filePath);
        String fileContent = configFileManager.readFile(filePath);
        executionReporter.printFileSnapshot(title, filePath, fileContent);
    }

    private void reconcileDesiredProperties(PgConfigChangeRequest pgConfigRequest) {
        pgConfigRequest.desiredProperties().entrySet().stream().forEach(entry -> {
            try {
                PropertyReconciliationResult result =
                    configFileManager.reconcileProperty(pgConfigRequest.configFilePath(), entry.getKey(), entry.getValue());
                executionReporter.printReconciliationResult(entry.getKey(), entry.getValue(), result);
            } catch (IOException e) {
                throw new RuntimeException("Failed to reconcile property '" + entry.getKey() + "': " + e.getMessage(), e);
            }
        });
    }

    private void restartTargetService(String serviceName) throws IOException {
        sshManager.restartService(serviceName);
        LOG.info("Service restarted: " + serviceName);
    }
}

record PropertyReconciliationResult(String action) {}

@ApplicationScoped
class ExecutionReporter {
    private static final Logger LOG = Logger.getLogger(ExecutionReporter.class.getName());

    public void printConfiguration(ConfigurationRequest request) {
        String desiredProperties = request.pgConfig().desiredProperties().isEmpty()
            ? "(none)"
            : request.pgConfig().desiredProperties().entrySet().stream()
                .map(entry -> "  - " + entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n"));

        String summary = String.join("\n",
            "================ pg-ssh-config ================",
            "SSH Host     : " + request.ssh().host(),
            "SSH Port     : " + request.ssh().port(),
            "SSH User     : " + request.ssh().username(),
            "Config File  : " + request.pgConfig().configFilePath(),
            "Target Svc   : " + request.pgConfig().targetService(),
            "Properties   :",
            desiredProperties,
            "================================================"
        );
        LOG.info(String.format("%n%s", summary));
    }

    public void printFileSnapshot(String title, String filePath, String content) {
        LOG.info("--- " + title + ": " + filePath + " ---");
        LOG.info(String.format("%n%s", content));
        LOG.info("--- End " + title + " ---");
    }

    public void printReconciliationResult(String key, String value, PropertyReconciliationResult result) {
        LOG.info("set " + key + "=" + value + " (" + result.action() + ")");
    }
}

@ApplicationScoped
class SshManager {
    private static final Logger LOG = Logger.getLogger(SshManager.class.getName());

    @Inject
    ProducerTemplate producerTemplate;
    
    private String sshEndpoint;
    private String password;

    public void initializeSession(String host, int port, String username, String password) {
        LOG.info("Connecting to SSH server at " + host + ":" + port + " as user " + username + "...");
        this.password = password;
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
        LOG.fine("Reading file contents from: " + filePath);
        String content = sshManager.executeSudoCommand("cat " + filePath);
        return content;
    }

    /**
     * Reconcile a property by removing existing declaration(s) and appending the desired one.
     * Returns a result indicating if the property was added or updated.
     */
    public PropertyReconciliationResult reconcileProperty(String filePath, String propertyName, String value) throws IOException {
        LOG.info("Reconciling property '" + propertyName + "' to " + value + " in " + filePath);
        boolean existed = propertyExists(filePath, propertyName);
        setProperty(filePath, propertyName, value);
        return new PropertyReconciliationResult(existed ? "updated" : "added");
    }

    /**
     * Check if a property exists (returns true/false)
     */
    public boolean propertyExists(String filePath, String propertyName) throws IOException {
        LOG.fine("Checking if property '" + propertyName + "' exists in " + filePath);
        try {
            String command = String.format(
                "grep -q '^%s[[:space:]]*=' '%s'",
                escapeRegex(propertyName),
                escapeSingleQuote(filePath)
            );
            sshManager.executeSudoCommand(command);
            return true;
        } catch (IOException e) {
            if (e.getMessage().contains("exit code 1")) {
                return false;
            }
            throw e;
        }
    }

    public void setProperty(String filePath, String propertyName, String value) throws IOException {
        String escapedRegexProperty = escapeRegex(propertyName);
        String escapedFilePath = escapeSingleQuote(filePath);
        String configLine = propertyName + " = " + value;

        LOG.fine("Removing existing declarations of '" + propertyName + "' from " + filePath);
        String deleteCommand = String.format(
            "sed -i '/^%s[[:space:]]*=/d' '%s'",
            escapedRegexProperty,
            escapedFilePath
        );
        sshManager.executeSudoCommand(deleteCommand);

        LOG.fine("Appending new property declaration to " + filePath);
        String appendCommand = String.format(
            "printf '%%s\\n' '%s' | tee -a '%s' > /dev/null",
            escapeSingleQuote(configLine),
            escapedFilePath
        );
        sshManager.executeSudoCommand(appendCommand);
        LOG.fine("Property '" + propertyName + "' set to '" + value + "'");
    }

    private String escapeRegex(String value) {
        StringBuilder escaped = new StringBuilder();
        String regexChars = "\\.^$|?*+()[]{}";
        for (char current : value.toCharArray()) {
            if (regexChars.indexOf(current) >= 0 || current == '/') {
                escaped.append('\\');
            }
            escaped.append(current);
        }
        return escaped.toString();
    }

    private String escapeSingleQuote(String value) {
        return value.replace("'", "'\"'\"'");
    }
}
