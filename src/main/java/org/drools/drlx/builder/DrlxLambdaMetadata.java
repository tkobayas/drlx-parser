package org.drools.drlx.builder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DrlxLambdaMetadata {

    private static final String FILE_NAME = "drlx-lambda-metadata.properties";
    private static final String SEPARATOR = "|";

    private final Map<String, String> entries = new LinkedHashMap<>();

    public record LambdaEntry(String fqn, String classFilePath, String expression) {
    }

    public void put(String ruleName, int counterId, String fqn, String classFilePath, String expression) {
        String key = ruleName + "." + counterId;
        String value = fqn + SEPARATOR + classFilePath + SEPARATOR + expression;
        entries.put(key, value);
    }

    public LambdaEntry get(String ruleName, int counterId) {
        String key = ruleName + "." + counterId;
        String value = entries.get(key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", 3);
        if (parts.length != 3) {
            return null;
        }
        return new LambdaEntry(parts[0], parts[1], parts[2]);
    }

    public int size() {
        return entries.size();
    }

    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_NAME);
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        }
    }

    public static DrlxLambdaMetadata load(Path file) throws IOException {
        DrlxLambdaMetadata metadata = new DrlxLambdaMetadata();
        if (!Files.exists(file)) {
            return metadata;
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx);
                    String value = line.substring(eqIdx + 1);
                    metadata.entries.put(key, value);
                }
            }
        }
        return metadata;
    }

    public static Path metadataFilePath(Path dir) {
        return dir.resolve(FILE_NAME);
    }
}
