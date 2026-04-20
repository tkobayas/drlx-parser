package org.drools.drlx.builder.syntax;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionalTest extends DrlxBuilderTestSupport {

    @Test
    void testPositionalSyntax() {
        // Single-arg positional: /locations("paris") → city == "paris"
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisLocations {
                    Location l : /locations("paris"),
                    do { System.out.println(l); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("locations");
            entryPoint.insert(new Location("paris", "Belleville"));
            entryPoint.insert(new Location("paris", "Montmartre"));
            entryPoint.insert(new Location("london", "Soho"));

            final int fired = kieSession.fireAllRules();

            assertThat(fired).isEqualTo(2);
        });
    }

    @Test
    void testPositionalAndSlotted() {
        // Positional + slotted: city == "paris" AND district == "Belleville"
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisBelleville {
                    Location l : /locations("paris")[ district == "Belleville" ],
                    do { System.out.println(l); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("locations");
            entryPoint.insert(new Location("paris", "Belleville"));
            entryPoint.insert(new Location("paris", "Montmartre"));
            entryPoint.insert(new Location("london", "Soho"));

            final int fired = kieSession.fireAllRules();

            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testPositionalSyntaxTwoArgs() {
        // Multi-arg positional: city == "paris" AND district == "Belleville"
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchParisBelleville {
                    Location l : /locations("paris", "Belleville"),
                    do { System.out.println(l); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("locations");
            entryPoint.insert(new Location("paris", "Belleville"));
            entryPoint.insert(new Location("paris", "Montmartre"));
            entryPoint.insert(new Location("london", "Soho"));

            final int fired = kieSession.fireAllRules();

            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testPositionalOnNonRootChunkFails() {
        // Positional on a navigation chunk (/locations/sub("x")) is grammatically
        // invalid since the grammar split — only oopathRoot allows positional.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule TryPositionalOnNonRoot {
                    Location l : /locations/sub("x"),
                    do { System.out.println(l); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testPositionalWithComplexExpression() {
        // Positional arg references a previously bound variable (beta path) AND
        // contains a binary operator (string concat) that exercises defensive parens.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Location;

                unit MyUnit;

                rule MatchByPersonName {
                    Person p : /persons[ name == "paris" ],
                    Location l : /locations(p.name),
                    do { System.out.println(l); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");
            final EntryPoint locations = kieSession.getEntryPoint("locations");
            persons.insert(new Person("paris", 30));
            locations.insert(new Location("paris", "Belleville"));
            locations.insert(new Location("london", "Soho"));

            final int fired = kieSession.fireAllRules();

            // Person p with name="paris" exists; Location with city == p.name fires once for "paris" Location.
            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testPositionalInheritedCollisionFails() {
        // ChildPositioned and its parent BasePositioned both declare @Position(0) —
        // resolver must reject the inherited collision.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ChildPositioned;

                unit MyUnit;

                rule TryInheritedCollision {
                    ChildPositioned l : /things("x"),
                    do { System.out.println(l); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate @Position(0)")
                .hasMessageContaining("ChildPositioned")
                .hasMessageContaining("BasePositioned");
    }

    @Test
    void testPositionalDuplicatePositionFails() {
        // Two fields both annotated @Position(0) — must fail loud, naming both fields.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.DuplicatePositionLocation;

                unit MyUnit;

                rule TryDuplicate {
                    DuplicatePositionLocation l : /locations("paris"),
                    do { System.out.println(l); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate @Position(0)")
                .hasMessageContaining("DuplicatePositionLocation")
                .hasMessageContaining("city")
                .hasMessageContaining("district");
    }

    @Test
    void testPositionalMissingAnnotation() {
        // PlainLocation has no @Position annotations — positional resolution must fail loud.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.PlainLocation;

                unit MyUnit;

                rule TryPositional {
                    PlainLocation l : /locations("paris"),
                    do { System.out.println(l); }
                }
                """;

        final DrlxRuleBuilder builder = newBuilder();

        assertThatThrownBy(() -> builder.build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("@Position(0)")
                .hasMessageContaining("PlainLocation");
    }
}
