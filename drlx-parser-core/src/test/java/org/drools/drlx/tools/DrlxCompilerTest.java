package org.drools.drlx.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.drools.drlx.builder.DrlxBuildCacheStrategy;
import org.drools.drlx.builder.DrlxLambdaMetadata;
import org.drools.drlx.builder.DrlxRuleAstParseResult;
import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DrlxCompilerTest {

    @Test
    void testTwoStepBuildWithRuleAstParseResult() throws IOException {
        String previousStrategy = System.getProperty(DrlxBuildCacheStrategy.PROPERTY);
        System.setProperty(DrlxBuildCacheStrategy.PROPERTY, "ruleAst");

        try {
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

            Path outputDir = Files.createTempDirectory("drlx-rule-ast-");
            DrlxCompiler compiler = new DrlxCompiler(outputDir);

            compiler.preBuild(rule);

            Path metadataFile = DrlxLambdaMetadata.metadataFilePath(outputDir);
            Path parseResultFile = DrlxRuleAstParseResult.parseResultFilePath(outputDir);
            assertThat(Files.exists(metadataFile)).isTrue();
            assertThat(Files.exists(parseResultFile)).isTrue();

            KieBase kieBase = compiler.build(rule);
            KieSession kieSession = kieBase.newKieSession();
            kieSession.getEntryPoint("seniors").insert(new Person("Alice", 40));
            kieSession.getEntryPoint("juniors").insert(new Person("Bob", 25));

            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(1);

            kieSession.dispose();
        } finally {
            if (previousStrategy == null) {
                System.clearProperty(DrlxBuildCacheStrategy.PROPERTY);
            } else {
                System.setProperty(DrlxBuildCacheStrategy.PROPERTY, previousStrategy);
            }
        }
    }

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
    void testLambdaSharingInPreBuild() throws IOException {
        // 3 rules with identical beta constraint (age < p1.age), identical consequence (System.out.println(p2)),
        // but different alpha constraints (age > 0, age > 1, age > 2).
        // Pre-build should produce only 4 unique class files (3 alpha + 1 shared beta + 1 shared consequence = 5 lambdas, but 4 unique).
        // Wait: 3 unique alphas + 1 shared beta + 1 shared consequence = 5 unique classes? No:
        // Each rule has: 1 alpha (unique) + 1 beta (shared) + 1 consequence (shared) = 3 lambdas per rule = 9 total.
        // But only 3 unique alphas + 1 unique beta + 1 unique consequence = 5 unique classes.
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule Rule_0 {
                    Person p1 : /persons1[ age > 0 ],
                    Person p2 : /persons2[ age < p1.age ],
                    do { System.out.println(p2); }
                }

                rule Rule_1 {
                    Person p1 : /persons1[ age > 1 ],
                    Person p2 : /persons2[ age < p1.age ],
                    do { System.out.println(p2); }
                }

                rule Rule_2 {
                    Person p1 : /persons1[ age > 2 ],
                    Person p2 : /persons2[ age < p1.age ],
                    do { System.out.println(p2); }
                }
                """;

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        DrlxCompiler compiler = new DrlxCompiler();

        // Step 1: pre-build
        compiler.preBuild(rule);

        // Count .class files produced — should be 5 (3 unique alpha + 1 shared beta + 1 shared consequence)
        // Without dedup it would be 9 (3 rules x 3 lambdas each)
        Path outputDir = compiler.getOutputDir();
        long classFileCount;
        try (var stream = Files.walk(outputDir)) {
            classFileCount = stream.filter(p -> p.toString().endsWith(".class")).count();
        }
        assertThat(classFileCount).isEqualTo(5);

        // Step 2: build — should still work correctly with shared classes
        KieBase kieBase = compiler.build(rule);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons1").insert(new Person("Alice", 40));
        kieSession.getEntryPoint("persons2").insert(new Person("Bob", 25));

        // All 3 rules should fire: Bob.age(25) < Alice.age(40) for all, and Alice.age(40) > 0, 1, 2
        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(3);

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
