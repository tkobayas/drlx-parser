package org.drools.drlx.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.drools.drlx.builder.DrlxLambdaMetadata;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.kie.api.KieBase;
import org.mvel3.lambdaextractor.LambdaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience wrapper for the 2-step DRLX build process.
 *
 * <pre>
 * DrlxCompiler compiler = new DrlxCompiler();
 *
 * // Step 1 (one-time, at build time): pre-compile lambdas
 * compiler.preBuild(Path.of("src/main/resources/rules.drlx"));
 *
 * // Step 2 (repeated, at runtime): build KieBase using pre-compiled classes
 * KieBase kieBase = compiler.build(Path.of("src/main/resources/rules.drlx"));
 * </pre>
 *
 * The {@link #build} method automatically detects pre-built metadata and uses
 * pre-compiled lambda classes when available, falling back to normal compilation otherwise.
 */
public class DrlxCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxCompiler.class);

    private final Path outputDir;
    private final DrlxRuleBuilder builder = new DrlxRuleBuilder();

    /**
     * Creates a DrlxCompiler using the default output directory
     * ({@code target/generated-classes/mvel}, configurable via system property
     * {@code mvel3.compiler.lambda.persistence.path}).
     */
    public DrlxCompiler() {
        this(LambdaRegistry.DEFAULT_PERSISTENCE_PATH);
    }

    /**
     * Creates a DrlxCompiler with a custom output directory for metadata and class files.
     */
    public DrlxCompiler(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Step 1: Pre-build. Compiles all lambda classes and saves metadata to the output directory.
     */
    public void preBuild(Path drlxFile) throws IOException {
        preBuild(Files.readString(drlxFile));
    }

    /**
     * Step 1: Pre-build from a classpath resource.
     */
    public void preBuild(InputStream drlxInput) throws IOException {
        preBuild(new String(drlxInput.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Step 1: Pre-build from a DRLX source string.
     */
    public void preBuild(String drlxSource) throws IOException {
        builder.preBuild(drlxSource, outputDir);
        LOG.info("Pre-build complete. Metadata saved to {}", DrlxLambdaMetadata.metadataFilePath(outputDir));
    }

    /**
     * Step 2: Build a KieBase. Automatically uses pre-compiled lambda classes if metadata exists.
     */
    public KieBase build(Path drlxFile) throws IOException {
        return build(Files.readString(drlxFile));
    }

    /**
     * Step 2: Build from a classpath resource.
     */
    public KieBase build(InputStream drlxInput) throws IOException {
        return build(new String(drlxInput.readAllBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Step 2: Build from a DRLX source string.
     */
    public KieBase build(String drlxSource) throws IOException {
        Path metadataFile = DrlxLambdaMetadata.metadataFilePath(outputDir);
        if (Files.exists(metadataFile)) {
            LOG.info("Found pre-built metadata at {}, using pre-compiled lambda classes", metadataFile);
            return builder.build(drlxSource, metadataFile);
        }
        LOG.info("No pre-built metadata found, compiling from scratch");
        return builder.build(drlxSource);
    }

    public Path getOutputDir() {
        return outputDir;
    }
}
