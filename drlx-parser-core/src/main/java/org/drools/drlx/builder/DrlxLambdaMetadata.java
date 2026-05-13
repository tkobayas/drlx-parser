package org.drools.drlx.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import org.mvel3.lambdaextractor.ArtifactRef;

/**
 * Pre-build metadata for DRLX lambdas. Persisted as a Properties file with
 * {@code format.version=2}. Each entry is keyed by {@code rule.<ruleName>.<counterId>}
 * with {@code expression}, {@code fqn}, and {@code classFile} sub-keys.
 * <p>
 * Self-sufficient: contains absolute {@code classFile} paths so the runtime build
 * loads classes directly via {@link org.mvel3.lambdaextractor.LambdaArtifactLoader},
 * without any callback into MVEL's registry state.
 */
public class DrlxLambdaMetadata {

    private static final String FILE_NAME = "drlx-lambda-metadata.properties";
    private static final String FORMAT_VERSION = "2";
    private static final String KEY_VERSION = "format.version";

    private final Map<String, LambdaEntry> entries = new LinkedHashMap<>();

    public record LambdaEntry(String fqn, Path classFile, String expression) {
        public ArtifactRef toArtifactRef() { return new ArtifactRef(fqn, classFile); }
    }

    public void put(String ruleName, int counterId, ArtifactRef ref, String expression) {
        entries.put(key(ruleName, counterId), new LambdaEntry(ref.fqn(), ref.classFile(), expression));
    }

    public LambdaEntry get(String ruleName, int counterId) {
        return entries.get(key(ruleName, counterId));
    }

    public int size() { return entries.size(); }

    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_NAME);
        Properties props = new Properties();
        props.setProperty(KEY_VERSION, FORMAT_VERSION);
        for (Map.Entry<String, LambdaEntry> e : entries.entrySet()) {
            String base = e.getKey();
            props.setProperty(base + ".expression", e.getValue().expression());
            props.setProperty(base + ".fqn", e.getValue().fqn());
            props.setProperty(base + ".classFile", e.getValue().classFile().toString());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "DRLX lambda metadata");
        }
    }

    /**
     * Loads metadata from {@code file}.
     * <ul>
     *   <li>Missing file → empty metadata (NOT a mismatch).</li>
     *   <li>Missing or wrong {@code format.version} → throws {@link InvalidDrlxLambdaMetadataException}.</li>
     *   <li>Missing required keys in an entry → throws same.</li>
     * </ul>
     */
    public static DrlxLambdaMetadata load(Path file) throws IOException {
        DrlxLambdaMetadata metadata = new DrlxLambdaMetadata();
        if (!Files.exists(file)) return metadata;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        String version = props.getProperty(KEY_VERSION);
        if (!FORMAT_VERSION.equals(version)) {
            throw new InvalidDrlxLambdaMetadataException(
                    "Unsupported DRLX lambda metadata format.version: " + version + " (expected " + FORMAT_VERSION + ")");
        }

        TreeSet<String> bases = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("rule.")) continue;
            int lastDot = key.lastIndexOf('.');
            if (lastDot <= "rule.".length()) continue;
            bases.add(key.substring(0, lastDot));
        }
        for (String base : bases) {
            String expression = required(props, base + ".expression");
            String fqn = required(props, base + ".fqn");
            String classFile = required(props, base + ".classFile");
            Path classFilePath;
            try {
                classFilePath = Path.of(classFile);
            } catch (InvalidPathException e) {
                throw new InvalidDrlxLambdaMetadataException(
                        "Invalid classFile path for " + base + ": " + classFile, e);
            }
            metadata.entries.put(base, new LambdaEntry(fqn, classFilePath, expression));
        }
        return metadata;
    }

    public static Path metadataFilePath(Path dir) {
        return dir.resolve(FILE_NAME);
    }

    private static String key(String ruleName, int counterId) {
        return "rule." + ruleName + "." + counterId;
    }

    private static String required(Properties p, String key) throws InvalidDrlxLambdaMetadataException {
        String v = p.getProperty(key);
        if (v == null) throw new InvalidDrlxLambdaMetadataException("Missing key: " + key);
        return v;
    }
}
