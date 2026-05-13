package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural guard (Phase 0 D7). Asserts that DRLX source files do not import
 * MVEL-internal lambda components: {@code LambdaRegistry}, {@code LambdaCatalog},
 * {@code LambdaPersistenceManager}, {@code LambdaArtifactStore}, or
 * {@code LambdaRegistryStore}. The supported MVEL surface is the narrow set
 * {@code ArtifactRef}, {@code LambdaArtifactLoader}, {@code LambdaRuntime}.
 */
class DrlxMvelImportGuardTest {

    private static final Path DRLX_SRC = Path.of("src/main/java/org/drools/drlx");

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("^\\s*import\\s+org\\.mvel3\\.lambdaextractor\\.LambdaRegistry\\s*;"),
            Pattern.compile("^\\s*import\\s+org\\.mvel3\\.lambdaextractor\\.LambdaCatalog\\s*;"),
            Pattern.compile("^\\s*import\\s+org\\.mvel3\\.lambdaextractor\\.LambdaPersistenceManager\\s*;"),
            Pattern.compile("^\\s*import\\s+org\\.mvel3\\.lambdaextractor\\.LambdaArtifactStore\\s*;"),
            Pattern.compile("^\\s*import\\s+org\\.mvel3\\.lambdaextractor\\.LambdaRegistryStore\\s*;")
    );

    @Test
    void D7_drlx_noForbiddenMvelLambdaImports() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(DRLX_SRC)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    List<String> lines;
                    try {
                        lines = Files.readAllLines(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        for (Pattern pat : FORBIDDEN) {
                            if (pat.matcher(line).find()) {
                                violations.add(p + ":" + (i + 1) + " — " + line.trim());
                            }
                        }
                    }
                });
        }
        assertThat(violations)
                .as("DRLX source files must not import MVEL-internal lambda components")
                .isEmpty();
    }
}
