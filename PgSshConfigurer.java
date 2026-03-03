//usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//RUNTIME_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//DEPS io.quarkus.platform:quarkus-bom:3.15.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS org.apache.camel.quarkus:camel-quarkus-ssh:3.15.0
//FILES .pg-ssh-config.properties

//Q:CONFIG app.greeting.message=Hello

//Q:CONFIG quarkus.banner.enabled=false
//Q:CONFIG quarkus.log.level=WARN
//Q:CONFIG quarkus.log.min-level=TRACE
//Q:CONFIG quarkus.log.console.level=TRACE
//Q:CONFIG quarkus.log.console.format=%d{HH:mm:ss.SSS} %-5p [%c{1.}] (%t) %m%n
//Q:CONFIG quarkus.log.category."PgSshConfigurer".level=INFO
//Q:CONFIG quarkus.log.category."SshManager".level=INFO
//Q:CONFIG quarkus.log.category."ConfigFileManager".level=INFO

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
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Command(
    name = "pg-ssh-config",
    defaultValueProvider = PropertiesDefaultProvider.class,
    mixinStandardHelpOptions = true
)
public class PgSshConfigurer implements Runnable {

    private static final Logger LOG = Logger.getLogger(PgSshConfigurer.class.getName());

    @Inject
    SshManager sshManager;

    @Inject
    ConfigFileManager configFileManager;

    @ConfigProperty(name = "app.greeting.message")
    String greetingMessage;

    @Option(
        names = "--ssh-host",
        description = "SSH host address",
        defaultValue = "localhost"
    )
    String sshHost;

    @Option(
        names = "--ssh-port",
        description = "SSH port number (1-65535)",
        defaultValue = "2223"
    )
    int sshPort;

    @Option(
        names = "--ssh-user",
        description = "SSH username",
        defaultValue = "demo"
    )
    String sshUsername;

    @Option(
        names = "--ssh-password",
        description = "SSH password",
        defaultValue = "demo",
        interactive = true
    )
    String sshPassword;

    @Option(
        names = "--config-file",
        description = "PostgreSQL configuration file path",
        defaultValue = "/etc/postgresql/16/main/postgresql.conf"
    )
    String configFilePath;

    @Override
    public void run() {
        try {
            LOG.info(greetingMessage);

            // 1. Print SSH connection details
            LOG.info("=== SSH Connection Details ===");
            LOG.info("Host: " + sshHost);
            LOG.info("Port: " + sshPort);
            LOG.info("User: " + sshUsername);
            LOG.info("");

            // 2. Initialize SSH connection
            sshManager.connect(sshHost, sshPort, sshUsername, sshPassword);
            LOG.info("SSH connection established successfully");
            LOG.info("");

            // 3. Display target configuration file content
            LOG.info("=== Initial Configuration File ===");
            configFileManager.viewFile(configFilePath);
            LOG.info("");

            // 4. Verify a property exists
            LOG.info("=== Checking if property exists ===");
            boolean exists = configFileManager.checkProperty(configFilePath,
                "listen_addresses");
            LOG.info("Property 'listen_addresses' exists: " + exists);
            LOG.info("");

            // 5. Update the property with a new value
            LOG.info("=== Updating property value ===");
            configFileManager.setProperty(configFilePath,
                "listen_addresses", "localhost");
            LOG.info("Property 'listen_addresses' updated to 'localhost'");
            configFileManager.viewFile(configFilePath);
            LOG.info("");

            // 6. Add a new property
            LOG.info("=== Adding new property ===");
            configFileManager.addProperty(configFilePath,
                "max_connections", "200");
            LOG.info("Property 'max_connections' added with value '200'");
            configFileManager.viewFile(configFilePath);
            LOG.info("");

            // 7. Remove a property
            LOG.info("=== Removing property ===");
            configFileManager.removeProperty(configFilePath,
                "max_connections");
            LOG.info("Property 'max_connections' removed");
            LOG.info("");

            // 8. Print final configuration state
            LOG.info("=== Final Configuration State ===");
            configFileManager.viewFile(configFilePath);

            // 9. Demonstrate systemd service management
            LOG.info("=== Restarting Demo Service ===");
            sshManager.restartService("demo-service");
            LOG.info("Demo service restarted successfully");

        } catch (Exception e) {
            LOG.severe("Error during execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            sshManager.disconnect();
        }
    }
}

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

    public void viewFile(String filePath) throws IOException {
        LOG.info("--- File: " + filePath + " ---");
        String content = sshManager.executeSudoCommand("cat " + filePath);
        LOG.info(content);
        LOG.info("--- End of file ---");
    }

    public boolean checkProperty(String filePath, String propertyName) throws IOException {
        try {
            String command = String.format("grep -q '^%s\\s*=' %s", propertyName, filePath);
            sshManager.executeSudoCommand(command);
            return true;
        } catch (IOException e) {
            // grep returns non-zero if pattern not found
            if (e.getMessage().contains("exit code 1")) {
                return false;
            }
            throw e;
        }
    }

    public void setProperty(String filePath, String propertyName, String value) throws IOException {
        // Use sed to replace the property value
        String sedCommand = String.format(
            "sed -i \"s/^%s\\s*=.*/%s = %s/\" %s",
            propertyName, propertyName, value, filePath
        );
        sshManager.executeSudoCommand(sedCommand);
        LOG.fine("Property '" + propertyName + "' set to '" + value + "'");
    }

    public void addProperty(String filePath, String propertyName, String value) throws IOException {
        // Check if property already exists
        if (checkProperty(filePath, propertyName)) {
            LOG.info("Property '" + propertyName + "' already exists, updating instead");
            setProperty(filePath, propertyName, value);
        } else {
            // Append the property to the file using tee -a for sudo access
            String echoCommand = String.format(
                "echo '%s = %s' | tee -a %s > /dev/null",
                propertyName, value, filePath
            );
            sshManager.executeSudoCommand(echoCommand);
            LOG.fine("Property '" + propertyName + "' added with value '" + value + "'");
        }
    }

    public void removeProperty(String filePath, String propertyName) throws IOException {
        // Use sed to remove lines that match the property
        String sedCommand = String.format(
            "sed -i '/^%s\\s*=/d' %s",
            propertyName, filePath
        );
        sshManager.executeSudoCommand(sedCommand);
        LOG.fine("Property '" + propertyName + "' removed");
    }
}
