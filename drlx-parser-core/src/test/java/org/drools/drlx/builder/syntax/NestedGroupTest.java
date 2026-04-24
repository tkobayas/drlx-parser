package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class NestedGroupTest extends DrlxBuilderTestSupport {

    @Test
    void orOfAnds() {
        // `or(and(/persons1[age>18], /persons2[age<30]),
        //    and(/persons1[age>60], /persons3[age<18]))` — DRLXXXX §16
        // canonical example: two branches, each is an AND of two predicates.
        // Fires for each branch that is satisfied.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule OrOfAnds {
                    or(and(/persons1[ age > 18 ], /persons2[ age < 30 ]),
                       and(/persons1[ age > 60 ], /persons3[ age < 18 ])),
                    do { System.out.println("match"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            // First branch satisfied: adult in persons1, young adult in persons2.
            persons1.insert(new Person("Alice", 25));
            persons2.insert(new Person("Bob", 22));

            // Second branch unsatisfied (no senior in persons1, no kid in persons3).
            // First branch fires once.
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }

    @Test
    void andContainingNot() {
        // `and(/persons[age>18], not(/persons[name=="Bob"]))` — fires when
        // at least one adult exists AND no one is named Bob. Verifies AND
        // accepts nested NOT (groupChild-level CE nesting).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule AdultWithoutBob {
                    and(/persons[ age > 18 ], not(/persons[ name == "Bob" ])),
                    do { System.out.println("adult and no bob"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // Adult with no Bob → fires.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);

            // Insert Bob → NOT now unsatisfied, rule no longer activates.
            persons.insert(new Person("Bob", 40));
            assertThat(kieSession.fireAllRules()).isZero();

            // Still one total firing.
            assertThat(fired).hasSize(1);
        });
    }

    @Test
    void notContainingOr() {
        // `not(or(/persons[name=="Alice"], /persons[name=="Bob"]))` —
        // DeMorgan: fires when there's no Alice AND no Bob. Verifies NOT
        // paren form accepts nested OR (groupChild-level CE nesting).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule NoAliceNoBob {
                    not(or(/persons[ name == "Alice" ], /persons[ name == "Bob" ])),
                    do { System.out.println("no alice, no bob"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // Empty — NOT satisfied (vacuously), rule fires once.
            assertThat(kieSession.fireAllRules()).isEqualTo(1);

            // Insert Charlie — still no Alice, no Bob — NOT still satisfied.
            // Match already fired; no new activation.
            persons.insert(new Person("Charlie", 50));
            assertThat(kieSession.fireAllRules()).isZero();

            // Insert Alice — OR becomes satisfied, so NOT is not. No fire.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isZero();

            assertThat(fired).hasSize(1);
        });
    }

    @Test
    void andWithNestedNotCarryingBinding() {
        // `and(var p : /persons1, not(/persons2[name == p.name]))` —
        // binding `p` from outer AND is visible to the nested NOT's
        // oopath condition. Fires when some p in persons1 has no
        // same-named match in persons2. Confirms bindings survive
        // nesting across groupChild boundaries.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule NoSameNameInOther {
                    and(var p : /persons1, not(/persons2[ name == p.name ])),
                    do { System.out.println("unique to persons1"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            // Alice and Bob in persons1; only Alice in persons2.
            // → For Bob, no match in persons2 → fires.
            // → For Alice, match in persons2 → does not fire.
            persons1.insert(new Person("Alice", 30));
            persons1.insert(new Person("Bob", 40));
            persons2.insert(new Person("Alice", 25));

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }
}
