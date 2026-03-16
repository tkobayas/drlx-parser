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
    void testTwoStepBuildWithJoin() throws IOException {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule JoinRule {
                    Person p1 : /seniors[ age > 30 ],
                    Person p2 : /juniors[ age < p1.age ],
                    do { System.out.println(p2.getName() + " is younger than " + p1.getName()); }
                }
                """;

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        DrlxCompiler compiler = new DrlxCompiler();

        // Step 1: pre-build
        compiler.preBuild(rule);

        // Step 2: build (auto-detects metadata)
        KieBase kieBase = compiler.build(rule);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("seniors").insert(new Person("Alice", 40));
        kieSession.getEntryPoint("juniors").insert(new Person("Bob", 25));

        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testTwoStepBuildWithMultiLevelJoin() throws IOException {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule MultiLevelJoinRule {
                    Person p1 : /persons1[ age > 30 ],
                    Person p2 : /persons2[ age < p1.age ],
                    Person p3 : /persons3[ age > p1.age - p2.age ],
                    do { System.out.println(p3.getName() + " age > " + p1.getName() + ".age - " + p2.getName() + ".age"); }
                }
                """;

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        DrlxCompiler compiler = new DrlxCompiler();

        // Step 1: pre-build
        compiler.preBuild(rule);

        // Step 2: build (auto-detects metadata)
        KieBase kieBase = compiler.build(rule);

        KieSession kieSession = kieBase.newKieSession();
        // p1: age 40, p2: age 25 (< 40), p3: age 20
        // p3 constraint: age > p1.age - p2.age => 20 > 40 - 25 => 20 > 15 => true
        kieSession.getEntryPoint("persons1").insert(new Person("Alice", 40));
        kieSession.getEntryPoint("persons2").insert(new Person("Bob", 25));
        kieSession.getEntryPoint("persons3").insert(new Person("Charlie", 20));

        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(1);

        // Insert a person that should NOT match: age 10 > 15 => false
        kieSession.getEntryPoint("persons3").insert(new Person("Dave", 10));
        int fired2 = kieSession.fireAllRules();
        assertThat(fired2).isEqualTo(0);

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
