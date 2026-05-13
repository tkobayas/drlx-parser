package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.mvel3.ClassManager;
import org.mvel3.MVELCompiler;
import org.mvel3.lambdaextractor.ArtifactRef;
import org.mvel3.lambdaextractor.LambdaArtifactLoader;
import org.mvel3.lambdaextractor.LambdaRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 0 DRLX boundary characterisation tests D1-D6. D7 is in
 * {@link DrlxMvelImportGuardTest}.
 */
class DrlxLambdaBoundaryTest {

    private static final String SIMPLE_RULE = """
            package org.drools.drlx.parser;

            import org.drools.drlx.domain.Person;
            import org.drools.drlx.ruleunit.MyUnit;
            unit MyUnit;

            rule CheckAge {
                Person p : /persons[ age > 18 ],
                do { System.out.println(p); }
            }
            """;

    @Test
    @DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
    void D1_preBuildThenRuntime_loadsWithoutCompile() throws IOException {
        LambdaRuntime.getInstance().resetAndRemoveAllPersistedFiles();
        DrlxRuleBuilder builder = new DrlxRuleBuilder();

        DrlxLambdaMetadata metadata = builder.preBuild(SIMPLE_RULE, LambdaRuntime.defaultPersistencePath());
        assertThat(metadata.size()).isGreaterThan(0);

        LambdaRuntime.getInstance().reset();

        int before = MVELCompiler.compileInvocationCount();
        KieBase kieBase = builder.build(SIMPLE_RULE, metadata);
        int after = MVELCompiler.compileInvocationCount();

        assertThat(after)
                .as("Runtime build with pre-built metadata must not recompile lambdas")
                .isEqualTo(before);

        try (KieSession session = kieBase.newKieSession()) {
            assertThat(session).isNotNull();
        }
    }

    @Test
    void D2_metadata_missingFile_freshCompile(@TempDir Path tmp) throws IOException {
        DrlxLambdaMetadata loaded = DrlxLambdaMetadata.load(tmp.resolve("nonexistent.properties"));
        assertThat(loaded.size()).isEqualTo(0);
    }

    @Test
    void D3_metadata_unsupportedVersion_throws(@TempDir Path tmp) throws IOException {
        Files.writeString(DrlxLambdaMetadata.metadataFilePath(tmp),
                "format.version=1\nrule.X.0.expression=foo\n");
        assertThatThrownBy(() -> DrlxLambdaMetadata.load(DrlxLambdaMetadata.metadataFilePath(tmp)))
                .isInstanceOf(InvalidDrlxLambdaMetadataException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void D4_metadata_staleClassFile_throws_inLoader(@TempDir Path tmp) {
        ArtifactRef stale = new ArtifactRef("org.example.Missing", tmp.resolve("does-not-exist.class"));
        assertThatThrownBy(() -> LambdaArtifactLoader.loadOrDefinePersistedClass(new ClassManager(), stale))
                .isInstanceOf(java.nio.file.NoSuchFileException.class);
    }

    @Test
    void D6_metadata_format_roundTrip(@TempDir Path tmp) throws IOException {
        DrlxLambdaMetadata m = new DrlxLambdaMetadata();
        m.put("RuleA", 0, new ArtifactRef("org.mvel3.GenA", tmp.resolve("GenA.class")), "age > 18");
        m.put("RuleA", 1, new ArtifactRef("org.mvel3.GenB", tmp.resolve("GenB.class")), "name != null");
        m.save(tmp);

        DrlxLambdaMetadata loaded = DrlxLambdaMetadata.load(DrlxLambdaMetadata.metadataFilePath(tmp));
        assertThat(loaded.size()).isEqualTo(2);
        DrlxLambdaMetadata.LambdaEntry e0 = loaded.get("RuleA", 0);
        assertThat(e0.fqn()).isEqualTo("org.mvel3.GenA");
        assertThat(e0.classFile()).isEqualTo(tmp.resolve("GenA.class"));
        assertThat(e0.expression()).isEqualTo("age > 18");
    }
}
