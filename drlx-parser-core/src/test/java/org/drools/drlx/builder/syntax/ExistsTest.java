package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Order;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExistsTest extends DrlxBuilderTestSupport {

    @Test
    void existsAllowsMatch() {
        // `exists /persons[age>=18]` — rule fires ONLY when at least one adult
        // person is inserted. Inverse of notSuppressesMatch.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule HasAdult {
                    exists /persons[ age >= 18 ],
                    do { System.out.println("adult exists"); }
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

            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // No facts → EXISTS unsatisfied, rule does not fire.
            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(fired).isEmpty();

            // Insert a child → still no adult, EXISTS unsatisfied.
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();

            // Insert an adult → EXISTS satisfied, rule fires once.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("HasAdult");
        });
    }

    @Test
    void existsWithOuterBinding() {
        // `Person p : /persons[...], exists /orders[customerId == p.age]` —
        // outer binding 'p' is referenced inside the EXISTS constraint, proving
        // beta-join from the outer pattern into the EXISTS group element.
        //
        // Domain note: correlates Order.customerId with Person.age only to
        // avoid adding a new integer field to Person. The semantic — "fire
        // when a person exists AND at least one order references them" — is
        // what's tested.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Order;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule ConfirmedPerson {
                    Person p : /persons[ age > 0 ],
                    exists /orders[ customerId == p.age ],
                    do { System.out.println("confirmed: " + p); }
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

            final EntryPoint persons = kieSession.getEntryPoint("persons");
            final EntryPoint orders = kieSession.getEntryPoint("orders");

            // Person but no order → EXISTS unsatisfied, no firing.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(fired).isEmpty();

            // Add a matching order (customerId == Alice.age = 30) → fires.
            orders.insert(new Order("O1", 30, 100));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("ConfirmedPerson");
        });
    }

    @Test
    void existsMultiElement_crossProduct() {
        // `exists(/persons[age>=18], /orders[amount>1000])` — EXISTS-with-AND
        // semantics: fires while at least one `(adult, high-value order)`
        // combination exists. Exercises the AND-wrap path for multi-child
        // EXISTS (Drools' newExistsInstance() enforces single-child).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Order;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule HasAdultWithHighValueOrder {
                    exists(/persons[ age >= 18 ], /orders[ amount > 1000 ]),
                    do { System.out.println("adult with high-value order exists"); }
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

            final EntryPoint persons = kieSession.getEntryPoint("persons");
            final EntryPoint orders = kieSession.getEntryPoint("orders");

            // Neither side → EXISTS unsatisfied, no firing.
            assertThat(kieSession.fireAllRules()).isZero();

            // Only adult → EXISTS still unsatisfied (missing order side).
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isZero();

            // Add a high-value order → both sides match → EXISTS satisfied,
            // rule fires once.
            orders.insert(new Order("O1", 99, 5000));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("HasAdultWithHighValueOrder");
        });
    }

    @Test
    void existsWithInnerBinding_failsParse() {
        // `exists var p : /persons[...]` — bindings inside `exists` can never
        // escape to the outer scope. The grammar does not accept `var p :`
        // inside existsElement, so the parser rejects at that level.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule IllegalInnerExistsBinding {
                    exists var p : /persons[ age >= 18 ],
                    do { System.out.println("should not compile"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("parse error")
                .hasMessageContaining("var");
    }

    @Test
    void existsEmpty_failsParse() {
        // `exists()` — parens with zero inner oopaths. Grammar's first
        // oopathExpression is not optional, so this is a parse error.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule EmptyExists {
                    exists(),
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
