package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.ReteDumper;
import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Employee;
import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.drools.drlx.domain.PlainLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mvel3.lambdaextractor.LambdaRegistry.DEFAULT_PERSISTENCE_PATH;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DrlxRuleBuilderTest {

    @Test
    void testBuildBasicRule() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("persons");
        Person person = new Person("John", 25);
        entryPoint.insert(person);
        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testLambdaSharing() {
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
                """; // placed /persons after /addresses to avoid node sharing, because this test is about lambda sharing

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        List<Path> classFiles = listClassFiles();
        assertThat(classFiles).hasSize(3);

        // GeneratorEvaluaor___0 : `age > 18` is shared between both rules
        // GeneratorEvaluaor___1 : `System.out.println(p);` is shared between both rules
        // GeneratorEvaluaor___2 : `city == "Tokyo"` is unique to CheckAge2

        List<DrlxLambdaConstraint> constraints = collectConstraints(kieBase, "age > 18");

        assertThat(constraints).hasSize(2);
        // class names are the same (e.g. GeneratorEvaluaor___0), but Class objects are different, because ClassManager is not shared at the moment
        assertThat(stripHiddenClassNameSuffix(constraints.get(0).getEvaluator().getClass().getName()))
                .isEqualTo(stripHiddenClassNameSuffix(constraints.get(1).getEvaluator().getClass().getName()));

        List<DrlxLambdaConsequence> consequences = collectConsequences(kieBase);
        assertThat(consequences).hasSize(2);
        assertThat(stripHiddenClassNameSuffix(consequences.get(0).getEvaluator().getClass().getName()))
                .isEqualTo(stripHiddenClassNameSuffix(consequences.get(1).getEvaluator().getClass().getName()));

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint personsEntryPoint = kieSession.getEntryPoint("persons");
        Person person = new Person("John", 25);
        personsEntryPoint.insert(person);
        EntryPoint addressesEntryPoint = kieSession.getEntryPoint("addresses");
        Address address = new Address("Tokyo");
        addressesEntryPoint.insert(address);

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    private String stripHiddenClassNameSuffix(String className) {
        return className.split("/0x")[0];
    }

    private static List<DrlxLambdaConsequence> collectConsequences(KieBase kieBase) {
        return kieBase.getKiePackage("org.drools.drlx.parser").getRules().stream()
                .map(RuleImpl.class::cast)
                .map(RuleImpl::getConsequence)
                .map(DrlxLambdaConsequence.class::cast)
                .toList();
    }

    private static List<DrlxLambdaConstraint> collectConstraints(KieBase kieBase, String expression) {
        Set<AlphaNode> alphaNodes = ReteDumper.collectRete(kieBase).stream()
                .filter(n -> n.getType() == NodeTypeEnums.AlphaNode)
                .map(AlphaNode.class::cast)
                .collect(Collectors.toSet());
        return alphaNodes.stream()
                .map(AlphaNode::getConstraint)
                .map(DrlxLambdaConstraint.class::cast)
                .filter(constraint -> constraint.getExpression().equals(expression))
                .toList();
    }

    @Test
    void testPreBuild() throws IOException {
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

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        DrlxLambdaMetadata metadata = builder.preBuild(rule, DEFAULT_PERSISTENCE_PATH);

        // CheckAge1: constraint(age > 18) -> 0, consequence(System.out.println(p);) -> 1
        // CheckAge2: constraint(city == "Tokyo") -> 0, constraint(age > 18) -> 1, consequence(System.out.println(p);) -> 2
        assertThat(metadata.size()).isEqualTo(5);
        assertThat(metadata.get("CheckAge1", 0)).isNotNull();
        assertThat(metadata.get("CheckAge1", 0).expression()).isEqualTo("age > 18");
        assertThat(metadata.get("CheckAge1", 1)).isNotNull();
        assertThat(metadata.get("CheckAge2", 0)).isNotNull();
        assertThat(metadata.get("CheckAge2", 0).expression()).isEqualTo("city == \"Tokyo\"");
        assertThat(metadata.get("CheckAge2", 1)).isNotNull();
        assertThat(metadata.get("CheckAge2", 1).expression()).isEqualTo("age > 18");
        assertThat(metadata.get("CheckAge2", 2)).isNotNull();

        // metadata file exists on disk
        Path metadataFile = DrlxLambdaMetadata.metadataFilePath(DEFAULT_PERSISTENCE_PATH);
        assertThat(Files.exists(metadataFile)).isTrue();
    }

    @Test
    void testRuntimeBuildWithPreCompiledClasses() throws IOException {
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

        DrlxRuleBuilder builder = new DrlxRuleBuilder();

        // Step 1: pre-build
        DrlxLambdaMetadata metadata = builder.preBuild(rule, DEFAULT_PERSISTENCE_PATH);

        // Step 2: runtime build with pre-compiled metadata
        KieBase kieBase = builder.build(rule, metadata);

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint personsEntryPoint = kieSession.getEntryPoint("persons");
        personsEntryPoint.insert(new Person("John", 25));
        EntryPoint addressesEntryPoint = kieSession.getEntryPoint("addresses");
        addressesEntryPoint.insert(new Address("Tokyo"));

        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    @Test
    void testFallbackWhenMetadataMissing() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        DrlxLambdaMetadata emptyMetadata = new DrlxLambdaMetadata();

        // Default is FAIL_FAST — empty metadata should raise an explicit error.
        assertThatThrownBy(() -> builder.build(rule, emptyMetadata))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pre-built metadata");

        // FALLBACK mode recompiles silently — opt-in behavior.
        System.setProperty(DrlxMetadataMismatchMode.PROPERTY, "fallback");
        try {
            KieBase kieBase = builder.build(rule, emptyMetadata);
            KieSession kieSession = kieBase.newKieSession();

            EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("John", 25));
            int fired = kieSession.fireAllRules();

            assertThat(fired).isEqualTo(1);
            kieSession.dispose();
        } finally {
            System.clearProperty(DrlxMetadataMismatchMode.PROPERTY);
        }
    }

    @Test
    void testInlineCast() {
        // Insert both Person and Employee into the same entry point.
        // The rule uses #Employee inline cast, so only Employee instances should match.
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Employee;

                unit MyUnit;

                rule CheckEmployee {
                    Person e : /persons#Employee[ department == "Engineering" ],
                    do { System.out.println(e); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("persons");
        // Insert a plain Person — should NOT match
        entryPoint.insert(new Person("Alice", 30));
        // Insert an Employee with matching department — should match
        entryPoint.insert(new Employee("Bob", 25, "Engineering"));
        // Insert an Employee with non-matching department — should NOT match
        entryPoint.insert(new Employee("Carol", 28, "Marketing"));

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testInlineCastWithoutConstraints() {
        // Inline cast without any constraint conditions — just type filtering
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Employee;

                unit MyUnit;

                rule MatchAllEmployees {
                    Person e : /persons#Employee,
                    do { System.out.println(e); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("persons");
        entryPoint.insert(new Person("Alice", 30));
        entryPoint.insert(new Employee("Bob", 25, "Engineering"));
        entryPoint.insert(new Employee("Carol", 28, "Marketing"));

        int fired = kieSession.fireAllRules();

        // Only the 2 Employees should match, not the plain Person
        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    @Test
    void testPositionalSyntax() {
        // Single-arg positional: /locations("paris") → city == "paris"
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisLocations {
                    Location l : /locations("paris"),
                    do { System.out.println(l); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);
        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("locations");
        entryPoint.insert(new Location("paris", "Belleville"));
        entryPoint.insert(new Location("paris", "Montmartre"));
        entryPoint.insert(new Location("london", "Soho"));

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    @Test
    void testPositionalAndSlotted() {
        // Positional + slotted: city == "paris" AND district == "Belleville"
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisBelleville {
                    Location l : /locations("paris")[ district == "Belleville" ],
                    do { System.out.println(l); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);
        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("locations");
        entryPoint.insert(new Location("paris", "Belleville"));
        entryPoint.insert(new Location("paris", "Montmartre"));
        entryPoint.insert(new Location("london", "Soho"));

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testPositionalSyntaxTwoArgs() {
        // Multi-arg positional: city == "paris" AND district == "Belleville"
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisBelleville {
                    Location l : /locations("paris", "Belleville"),
                    do { System.out.println(l); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);
        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("locations");
        entryPoint.insert(new Location("paris", "Belleville"));
        entryPoint.insert(new Location("paris", "Montmartre"));
        entryPoint.insert(new Location("london", "Soho"));

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testPositionalMissingAnnotation() {
        // PlainLocation has no @Position annotations — positional resolution must fail loud.
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.PlainLocation;

                unit MyUnit;

                rule TryPositional {
                    PlainLocation l : /locations("paris"),
                    do { System.out.println(l); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Position(0)")
                .hasMessageContaining("PlainLocation");
    }

    private List<Path> listClassFiles() {
        try (Stream<Path> walk = Files.walk(DEFAULT_PERSISTENCE_PATH)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
