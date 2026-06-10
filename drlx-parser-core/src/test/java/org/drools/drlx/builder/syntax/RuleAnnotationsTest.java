package org.drools.drlx.builder.syntax;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.ClockType;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.drools.drlx.ruleunit.TestDataObserver;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
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
    void testLockOnActiveAlonePreventsRefire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.LockOnActive;
                import org.drools.drlx.annotations.Salience;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @LockOnActive
                @Salience(10)
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { p.age += 1; persons.update(p); }
                }

                @LockOnActive
                rule R2 {
                    Person p : /persons[ age > 18 ],
                    do { p.age += 10; persons.update(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);

        assertThat(((RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1")).isLockOnActive()).isTrue();
        assertThat(((RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R2")).isLockOnActive()).isTrue();

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            // R1 fires first (salience 10): age 40 -> 41, update
            // R2 fires: age 41 -> 51, update
            // Both blocked from refiring by @LockOnActive — any rule's update is suppressed
            assertThat(instance.fire(100)).isEqualTo(2);
            assertThat(alice.getAge()).isEqualTo(51);
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

    @Test
    void testTimerWithoutArgumentFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Timer expects one argument");
    }

    @Test
    void testDurationWithoutArgumentFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Duration;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Duration
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Duration expects one argument");
    }

    @Test
    void testTimerEmptyStringFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Timer expects non-empty string literal");
    }

    @Test
    void testDurationEmptyStringFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Duration;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Duration("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Duration expects non-empty string literal");
    }

    @Test
    void testTimerAndDurationTogetherFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;
                import org.drools.drlx.annotations.Duration;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("int: 1s")
                @Duration("5s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Timer and @Duration cannot be used together");
    }

    @Test
    void testTimerInvalidProtocolFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("xyz: 1s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testTimerMissingColonFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("int 1s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDurationInvalidTimeStringFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Duration;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Duration("abc")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    private static KieSession pseudoClockSession(KieBase kieBase) {
        KieSessionConfiguration config = KieServices.Factory.get().newKieSessionConfiguration();
        config.setOption(ClockTypeOption.get(ClockType.PSEUDO_CLOCK.toString()));
        return kieBase.newKieSession(config, null);
    }

    @Test
    void testIntervalTimerFiresRepeatedly() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("int: 1s 1s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("timer fired for " + p.getName()); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final KieSession kieSession = pseudoClockSession(kieBase);
        try {
            AtomicInteger fireCount = new AtomicInteger();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fireCount.incrementAndGet();
                }
            });

            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            PseudoClockScheduler clock = (PseudoClockScheduler) kieSession.getSessionClock();

            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(0);

            clock.advanceTime(1, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(1);

            clock.advanceTime(1, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(2);

            clock.advanceTime(1, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(3);
        } finally {
            kieSession.dispose();
        }
    }

    @Test
    void testDurationDelayedFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Duration;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Duration("5s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("duration fired for " + p.getName()); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final KieSession kieSession = pseudoClockSession(kieBase);
        try {
            AtomicInteger fireCount = new AtomicInteger();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fireCount.incrementAndGet();
                }
            });

            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            PseudoClockScheduler clock = (PseudoClockScheduler) kieSession.getSessionClock();

            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(0);

            clock.advanceTime(3, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(0);

            clock.advanceTime(3, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(1);

            clock.advanceTime(10, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(1);
        } finally {
            kieSession.dispose();
        }
    }

    @Test
    void testCronTimerFires() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("cron: 0/5 * * * * ?")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("cron fired for " + p.getName()); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final KieSession kieSession = pseudoClockSession(kieBase);
        try {
            AtomicInteger fireCount = new AtomicInteger();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fireCount.incrementAndGet();
                }
            });

            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            PseudoClockScheduler clock = (PseudoClockScheduler) kieSession.getSessionClock();

            kieSession.fireAllRules();
            int initial = fireCount.get();

            clock.advanceTime(10, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isGreaterThan(initial);
        } finally {
            kieSession.dispose();
        }
    }

    @Test
    void testTimerDefaultProtocol() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Timer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @Timer("1s")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("default protocol fired"); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        final KieSession kieSession = pseudoClockSession(kieBase);
        try {
            AtomicInteger fireCount = new AtomicInteger();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fireCount.incrementAndGet();
                }
            });

            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            PseudoClockScheduler clock = (PseudoClockScheduler) kieSession.getSessionClock();

            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(0);

            clock.advanceTime(1, TimeUnit.SECONDS);
            kieSession.fireAllRules();
            assertThat(fireCount.get()).isEqualTo(1);
        } finally {
            kieSession.dispose();
        }
    }

    @Test
    void testDateEffectiveParsed() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2020-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        Rule r = kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        RuleImpl impl = (RuleImpl) r;
        assertThat(impl.getDateEffective()).isNotNull();
        assertThat(impl.getDateEffective().get(Calendar.YEAR)).isEqualTo(2020);
        assertThat(impl.getDateEffective().get(Calendar.MONTH)).isEqualTo(Calendar.JANUARY);
        assertThat(impl.getDateEffective().get(Calendar.DAY_OF_MONTH)).isEqualTo(1);
    }

    @Test
    void testDateExpiresParsed() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateExpires("2099-12-31")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        Rule r = kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        RuleImpl impl = (RuleImpl) r;
        assertThat(impl.getDateExpires()).isNotNull();
        assertThat(impl.getDateExpires().get(Calendar.YEAR)).isEqualTo(2099);
        assertThat(impl.getDateExpires().get(Calendar.MONTH)).isEqualTo(Calendar.DECEMBER);
        assertThat(impl.getDateExpires().get(Calendar.DAY_OF_MONTH)).isEqualTo(31);
    }

    @Test
    void testDateEffectiveAndExpiresTogether() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2020-01-01")
                @DateExpires("2099-12-31")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        Rule r = kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        RuleImpl impl = (RuleImpl) r;
        assertThat(impl.getDateEffective()).isNotNull();
        assertThat(impl.getDateExpires()).isNotNull();
    }

    @Test
    void testDateEffectiveFqnWithoutImport() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @org.drools.drlx.annotations.DateEffective("2020-06-15")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        final KieBase kieBase = newBuilder().build(rule);
        Rule r = kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(r).isNotNull();
        RuleImpl impl = (RuleImpl) r;
        assertThat(impl.getDateEffective()).isNotNull();
        assertThat(impl.getDateEffective().get(Calendar.YEAR)).isEqualTo(2020);
    }

    @Test
    void testDateEffectiveWithoutArgumentFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDateEffectiveEmptyStringFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDateEffectiveMalformedDateFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("not-a-date")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDateEffectiveLegacyFormatFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("01-Jan-2025")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDateEffectivePastDateRuleFires() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2020-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testDateEffectiveFutureDateRuleDoesNotFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2099-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(0);
        });
    }

    @Test
    void testDateExpiresFutureDateRuleFires() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateExpires("2099-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testDateExpiresPastDateRuleDoesNotFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateExpires("2020-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(0);
        });
    }

    @Test
    void testDateWindowCurrentlyActiveRuleFires() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2020-01-01")
                @DateExpires("2099-12-31")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testDateWindowFutureRuleDoesNotFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2098-01-01")
                @DateExpires("2099-12-31")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(0);
        });
    }

    @Test
    void testDateWindowExpiredRuleDoesNotFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;
                import org.drools.drlx.annotations.DateExpires;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2019-01-01")
                @DateExpires("2020-01-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p.getName()); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 30));
            int fired = kieSession.fireAllRules();
            assertThat(fired).isEqualTo(0);
        });
    }

    @Test
    void testDuplicateDateEffectiveFails() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.DateEffective;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                @DateEffective("2025-01-01")
                @DateEffective("2025-06-01")
                rule R1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

}
