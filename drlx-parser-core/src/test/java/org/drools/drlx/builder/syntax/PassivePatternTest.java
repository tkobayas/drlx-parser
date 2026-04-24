package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class PassivePatternTest extends DrlxBuilderTestSupport {

    // Canonical example from DRLXXXX §"Passive elements":
    //   rule R1 {
    //     var l : /locations[city == "paris"],
    //     var p : ?/persons[age > 18],
    //     do {...}
    //   }
    //
    // The `?` marks the persons pattern as passive: inserts on /persons
    // alone must not wake the rule; only inserts on /locations (the
    // reactive prior pattern) propagate through to firing.

    @Test
    void passiveSideInsertionDoesNotWakeRule() {
        // Prior reactive-side data exists; inserting on the passive side
        // alone must NOT fire the rule. This is the test that proves
        // Pattern.setPassive(true) actually took effect end-to-end.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var l : /locations[ city == "paris" ],
                    var p : ?/persons[ age > 18 ],
                    do { System.out.println("fired"); }
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

            final EntryPoint locations = kieSession.getEntryPoint("locations");
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // 1. Reactive-side data first. No person yet — no match.
            locations.insert(new Location("paris", "centre"));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // 2. Passive-side insertion alone. A match now exists in the
            //    right-side memory (location × person), but because the
            //    pattern is passive, this insertion MUST NOT wake the rule.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
            assertThat(fired).isEmpty();
        });
    }

    @Test
    void reactiveSideInsertionWakesRuleIncludingPendingPassiveMatches() {
        // Contrast test: the reactive side DOES wake the rule, and at
        // that point all pending matches (including those contributed
        // by prior passive-side insertions) fire.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var l : /locations[ city == "paris" ],
                    var p : ?/persons[ age > 18 ],
                    do { System.out.println("fired"); }
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

            final EntryPoint locations = kieSession.getEntryPoint("locations");
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // Insert passive-side first — match is pending in memory
            // but rule does not wake.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // Reactive-side insertion wakes the rule — one match fires.
            locations.insert(new Location("paris", "centre"));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }

    @Test
    void barePassivePatternInsideAndGroup() {
        // Confirms `?` works on BARE oopath (no bind) inside a group CE.
        // Grammar attaches ? to oopathExpression, so this comes free.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    and(/locations[ city == "paris" ], ?/persons[ age > 18 ]),
                    do { System.out.println("fired"); }
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

            final EntryPoint locations = kieSession.getEntryPoint("locations");
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            locations.insert(new Location("paris", "centre"));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // Passive-side insertion — must not wake.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
            assertThat(fired).isEmpty();
        });
    }
}
