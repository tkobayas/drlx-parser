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
import org.drools.core.event.TrackingAgendaEventListener;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.ReteDumper;
import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.mvel3.lambdaextractor.LambdaRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DrlxRuleBuilderTest {

    @Test
    void testBuildBasicRule() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        MyUnit unit = new MyUnit();
        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);

            unit.persons.add(new Person("John", 25));
            int fired = instance.fire();

            assertThat(fired).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("CheckAge");
        }
    }

    @Test
    void testLambdaSharing() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;

                import org.drools.drlx.ruleunit.MyUnit;
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

        LambdaRuntime.getInstance().resetAndRemoveAllPersistedFiles();

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

        MyUnit unit = new MyUnit();
        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);

            unit.persons.add(new Person("John", 25));
            unit.addresses.add(new Address("Tokyo"));

            int fired = instance.fire();

            assertThat(fired).isEqualTo(2);
            assertThat(listener.getAfterMatchFired()).containsExactlyInAnyOrder("CheckAge1", "CheckAge2");
        }
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

                import org.drools.drlx.ruleunit.MyUnit;
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

        LambdaRuntime.getInstance().resetAndRemoveAllPersistedFiles();

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        DrlxLambdaMetadata metadata = builder.preBuild(rule, LambdaRuntime.defaultPersistencePath());

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
        Path metadataFile = DrlxLambdaMetadata.metadataFilePath(LambdaRuntime.defaultPersistencePath());
        assertThat(Files.exists(metadataFile)).isTrue();
    }

    @Test
    void testRuntimeBuildWithPreCompiledClasses() throws IOException {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;

                import org.drools.drlx.ruleunit.MyUnit;
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

        LambdaRuntime.getInstance().resetAndRemoveAllPersistedFiles();

        DrlxRuleBuilder builder = new DrlxRuleBuilder();

        // Step 1: pre-build
        DrlxLambdaMetadata metadata = builder.preBuild(rule, LambdaRuntime.defaultPersistencePath());

        // Step 2: runtime build with pre-compiled metadata
        KieBase kieBase = builder.build(rule, metadata);

        MyUnit unit = new MyUnit();
        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);

            unit.persons.add(new Person("John", 25));
            unit.addresses.add(new Address("Tokyo"));

            int fired = instance.fire();
            assertThat(fired).isEqualTo(2);
            assertThat(listener.getAfterMatchFired()).containsExactlyInAnyOrder("CheckAge1", "CheckAge2");
        }
    }

    @Test
    void testFallbackWhenMetadataMissing() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
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
            MyUnit unit = new MyUnit();
            try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
                TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
                instance.addEventListener(listener);

                unit.persons.add(new Person("John", 25));
                int fired = instance.fire();

                assertThat(fired).isEqualTo(1);
                assertThat(listener.getAfterMatchFired()).containsExactly("CheckAge");
            }
        } finally {
            System.clearProperty(DrlxMetadataMismatchMode.PROPERTY);
        }
    }

    @Test
    void testSetterDesugaring() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule SetName {
                    Person p : /persons[ age > 18 ],
                    do { p.name = "Modified"; }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        MyUnit unit = new MyUnit();
        Person person = new Person("John", 25);
        unit.persons.add(person);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);

            int fired = instance.fire();

            assertThat(fired).isEqualTo(1);
            assertThat(person.getName()).isEqualTo("Modified");
        }
    }

    @Test
    void testSetterDesugaringChainedProperty() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule SetCity {
                    Person p : /persons[ age > 18 ],
                    do { p.address.city = "Tokyo"; }
                }
                """;

        DrlxRuleBuilder builder = new DrlxRuleBuilder();
        KieBase kieBase = builder.build(rule);

        MyUnit unit = new MyUnit();
        Person person = new Person("John", 25, new Address("Osaka"));
        unit.persons.add(person);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);

            int fired = instance.fire();

            assertThat(fired).isEqualTo(1);
            assertThat(person.getAddress().getCity()).isEqualTo("Tokyo");
        }
    }

    private List<Path> listClassFiles() {
        try (Stream<Path> walk = Files.walk(LambdaRuntime.defaultPersistencePath())) {
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
