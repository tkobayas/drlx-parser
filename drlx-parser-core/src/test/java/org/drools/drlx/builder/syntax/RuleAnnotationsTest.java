package org.drools.drlx.builder.syntax;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.drools.drlx.ruleunit.TestDataObserver;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleAnnotationsTest extends DrlxBuilderTestSupport {

    @Test
    void testSalienceAffectsFiringOrder() {
        // HighSalience (10) must fire before LowSalience (5) for the same fact.
        // An AgendaEventListener captures firing order externally.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(5)
                rule LowSalience {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("low"); }
                }

                @Salience(10)
                rule HighSalience {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("high"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("Alice", 30));

            final int firedCount = kieSession.fireAllRules();

            assertThat(firedCount).isEqualTo(2);
            assertThat(listener.getAfterMatchFired()).containsExactly("HighSalience", "LowSalience");
        });
    }

    @Test
    void testDescriptionStoredAsMetadata() {
        // @Description("Checks adult age") must be readable via Rule.getMetaData().get("Description").
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Description;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Description("Checks adult age")
                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final Rule r = kieBase.getRule("org.drools.drlx.parser", "CheckAge");

        assertThat(r).isNotNull();
        assertThat(r.getMetaData()).containsEntry("Description", "Checks adult age");
    }

    @Test
    void testSalienceAndDescriptionCombined() {
        // Both annotations on one rule must each take effect independently.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;
                import org.drools.drlx.annotations.Description;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(42)
                @Description("The answer")
                rule Combined {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final Rule r = kieBase.getRule("org.drools.drlx.parser", "Combined");

        assertThat(r).isNotNull();
        assertThat(r.getMetaData()).containsEntry("Description", "The answer");
    }

    @Test
    void testFullyQualifiedAnnotationWithoutImport() {
        // Fully-qualified form must be accepted with no matching 'import' line.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @org.drools.drlx.annotations.Description("FQN form")
                rule FullyQualified {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final Rule r = kieBase.getRule("org.drools.drlx.parser", "FullyQualified");

        assertThat(r).isNotNull();
        assertThat(r.getMetaData()).containsEntry("Description", "FQN form");
    }

    @Test
    void testSalienceWithoutImportFailsLoud() {
        // @Salience used without an import and without FQN must throw at parse time.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(10)
                rule MissingImport {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unresolved annotation")
                .hasMessageContaining("Salience");
    }

    @Test
    void testUnsupportedAnnotationFailsLoud() {
        // A fully-qualified annotation outside the supported set must be rejected.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @org.example.NoLoop
                rule Unsupported {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unsupported DRLX rule annotation")
                .hasMessageContaining("NoLoop");
    }

    @Test
    void testDuplicateSalienceFailsLoud() {
        // Two @Salience on one rule must throw.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(5)
                @Salience(10)
                rule Duplicate {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("duplicate @Salience");
    }

    @Test
    void testSalienceNonIntArgumentFailsLoud() {
        // @Salience("ten") — string literal where int required — must throw.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience("ten")
                rule BadType {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Salience expects int literal");
    }

    @Test
    void testSalienceExpressionArgumentFailsLoud() {
        // @Salience(1 + 2) — expression, not a pure literal — must throw.
        // The spec explicitly rejects expression-based salience in this first pass.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(1 + 2)
                rule BadShape {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Salience expects int literal");
    }

    @Test
    void testDataSourceEmptyStringFailsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Trust;
                import org.drools.drlx.annotations.DataSource;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DataSource("")
                rule Trusts(Object a, Object b) {
                    Trust t : /trusts[a == a, b == b],
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Datasource expects non-empty string literal");
    }

    @Test
    void testDataSourceOnNonQueryRuleFailsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DataSource;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DataSource("people")
                rule NotAQuery {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@DataSource is only allowed on query rules");
    }

    @Test
    void testDataSourceWithoutArgumentFailsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Trust;
                import org.drools.drlx.annotations.DataSource;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DataSource
                rule Trusts(Object a, Object b) {
                    Trust t : /trusts[a == a, b == b],
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Datasource expects one argument");
    }

    @Test
    void testDataSourceNonStringArgumentFailsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Trust;
                import org.drools.drlx.annotations.DataSource;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DataSource(42)
                rule Trusts(Object a, Object b) {
                    Trust t : /trusts[a == a, b == b],
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Datasource expects string literal");
    }

    @Test
    void testNoLoopRejectsArgument() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.NoLoop;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @NoLoop(true)
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@NoLoop takes no arguments");
    }

    @Test
    void testLockOnActiveRejectsArgument() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.LockOnActive;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @LockOnActive(true)
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@LockOnActive takes no arguments");
    }

    @Test
    void testAutoFocusRejectsArgument() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.AutoFocus;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @AutoFocus(true)
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@AutoFocus takes no arguments");
    }

    @Test
    void testDisabledRejectsArgument() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Disabled;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Disabled(false)
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Disabled takes no arguments");
    }

    @Test
    void testAgendaGroupRejectsEmptyString() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.AgendaGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @AgendaGroup("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@AgendaGroup expects non-empty string literal");
    }

    @Test
    void testActivationGroupRejectsEmptyString() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.ActivationGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @ActivationGroup("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@ActivationGroup expects non-empty string literal");
    }

    @Test
    void testRuleFlowGroupRejectsEmptyString() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.RuleFlowGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @RuleFlowGroup("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@RuleFlowGroup expects non-empty string literal");
    }

    @Disabled("#87 — DataStore update doesn't propagate terminal node origin, so no-loop is not enforced")
    @Test
    void testNoLoopAttributeApplied() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.NoLoop;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @NoLoop
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { p.age += 1; persons.update(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final RuleImpl r = (RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");

        assertThat(r).isNotNull();
        assertThat(r.isNoLoop()).isTrue();

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs = TestDataObserver.subscribeTo(unit.persons);

            assertThat(instance.fire(100)).isEqualTo(1);
            assertThat(obs.updated()).hasSize(1);
            assertThat(alice.getAge()).isEqualTo(41);
        }
    }

    @Test
    void testDisabledRuleDoesNotFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Disabled;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Disabled
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire(100)).isEqualTo(0);
        }
    }

    @Disabled("#88 — SimpleAgendaGroupsManager doesn't support agenda groups / setFocus")
    @Test
    void testAutoFocusActivatesGroup() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.AgendaGroup;
                import org.drools.drlx.annotations.AutoFocus;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @AgendaGroup("g1")
                @AutoFocus
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire(100)).isEqualTo(1);
        }
    }

    @Disabled("#87 #88 — requires both no-loop terminal node propagation and agenda group support")
    @Test
    void testLockOnActivePreventsRefire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.AgendaGroup;
                import org.drools.drlx.annotations.AutoFocus;
                import org.drools.drlx.annotations.LockOnActive;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @AgendaGroup("g1")
                @AutoFocus
                @LockOnActive
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { p.age += 1; persons.update(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final RuleImpl r = (RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");

        assertThat(r).isNotNull();
        assertThat(r.isLockOnActive()).isTrue();

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire(100)).isEqualTo(1);
            assertThat(alice.getAge()).isEqualTo(41);
        }
    }

    @Disabled("#88 — SimpleAgendaGroupsManager doesn't enforce agenda groups")
    @Test
    void testAgendaGroupAssignment() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.AgendaGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @AgendaGroup("g1")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final RuleImpl r = (RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        assertThat(r.getAgendaGroup()).isEqualTo("g1");

        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            // Without setFocus, rule should not fire
            assertThat(instance.fire(100)).isEqualTo(0);
        }
    }

    @Test
    void testActivationGroupOnlyOneFiresPerGroup() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.ActivationGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @ActivationGroup("only-one")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("R1"); }
                }

                @ActivationGroup("only-one")
                rule R2 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("R2"); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire(100)).isEqualTo(1);
        }
    }

    @Disabled("#89 — SimpleAgendaGroupsManager doesn't enforce ruleflow groups")
    @Test
    void testRuleFlowGroupAssignment() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.RuleFlowGroup;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @RuleFlowGroup("rfg1")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final RuleImpl r = (RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        assertThat(r.getRuleFlowGroup()).isEqualTo("rfg1");

        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            // RuleFlowGroup is not active, so rule should not fire
            assertThat(instance.fire(100)).isEqualTo(0);
        }
    }

    @Disabled("#87 #88 — requires both no-loop and agenda group runtime support")
    @Test
    void testMultipleAnnotationsCombined() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;
                import org.drools.drlx.annotations.NoLoop;
                import org.drools.drlx.annotations.AgendaGroup;
                import org.drools.drlx.annotations.AutoFocus;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Salience(5)
                @NoLoop
                @AgendaGroup("g1")
                @AutoFocus
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { p.age += 1; persons.update(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            // @AutoFocus makes the agenda group active, @NoLoop prevents refire
            assertThat(instance.fire(100)).isEqualTo(1);
            assertThat(alice.getAge()).isEqualTo(41);
        }
    }
}
