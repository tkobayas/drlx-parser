package org.drools.drlx.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.drools.drlx.builder.DrlxLambdaMetadata;
import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxCompilerTest {

    @Test
    void testTwoStepBuild() throws IOException {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;

                unit MyUnit;

                rule CheckAge1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }

                rule CheckAge2 {
                    Address s : /addresses[ city == "Tokyo" ],
                        Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        DrlxCompiler compiler = new DrlxCompiler();

        // Step 1: pre-build
        compiler.preBuild(rule);

        // metadata file should exist
        Path metadataFile = DrlxLambdaMetadata.metadataFilePath(compiler.getOutputDir());
        assertThat(Files.exists(metadataFile)).isTrue();

        // Step 2: build (auto-detects metadata)
        KieBase kieBase = compiler.build(rule);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 25));
        kieSession.getEntryPoint("addresses").insert(new Address("Tokyo"));

        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    @Test
    void testBuildWithoutPreBuild() throws IOException {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        // build without pre-build — should compile from scratch
        DrlxCompiler compiler = new DrlxCompiler();
        KieBase kieBase = compiler.build(rule);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 25));

        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }
}
