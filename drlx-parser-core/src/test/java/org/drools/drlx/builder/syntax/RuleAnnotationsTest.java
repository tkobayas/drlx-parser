package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
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

        withSession(rule, kieSession -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("Alice", 30));

            final int firedCount = kieSession.fireAllRules();

            assertThat(firedCount).isEqualTo(2);
            assertThat(fired).containsExactly("HighSalience", "LowSalience");
        });
    }

    @Test
    void testDescriptionStoredAsMetadata() {
        // @Description("Checks adult age") must be readable via Rule.getMetaData().get("Description").
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Description;

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
}
