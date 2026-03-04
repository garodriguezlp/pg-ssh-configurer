//usr/bin/env jbang "$0" "$@" ; exit $?

//JAVA 17+

//DEPS io.quarkus.platform:quarkus-bom:3.15.1@pom
//DEPS io.quarkus:quarkus-picocli
//FILES .poc-test.properties

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.PropertiesDefaultProvider;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Command(
    name = "poc-test",
    defaultValueProvider = PropertiesDefaultProvider.class,
    mixinStandardHelpOptions = true
)
public class RepeatableOptionsPoc implements Runnable {

    @Option(
        names = "--set",
        description = "Property settings as key=value pairs separated by semicolons: key1=val1;key2=val2",
        descriptionKey = "set",
        split = ";",
        splitSynopsisLabel = ";"
    )
    Map<String, String> setProperties = new LinkedHashMap<>();

    @Override
    public void run() {
        System.out.println("=== Picocli Map with Split PoC ===");
        System.out.println("Number of properties: " + setProperties.size());
        
        if (setProperties.isEmpty()) {
            System.out.println("No properties provided.");
        } else {
            System.out.println("\nParsed properties:");
            for (Map.Entry<String, String> entry : setProperties.entrySet()) {
                System.out.println("  [" + entry.getKey() + "] = " + entry.getValue());
            }
        }
        
        System.out.println("\n=== Test Result ===");
        if (setProperties.size() > 0) {
            System.out.println("✅ SUCCESS: Picocli Map option works!");
        } else {
            System.out.println("❌ FAILED: No properties parsed");
        }
    }

    public static void main(String[] args) {
        new CommandLine(new RepeatableOptionsPoc())
            .execute(args);
    }
}
