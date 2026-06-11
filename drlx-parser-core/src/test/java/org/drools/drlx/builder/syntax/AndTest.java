package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AndTest extends DrlxBuilderTestSupport {

    @Test
    void andAllowsMatch() {
        // `and(/persons[age>18], /persons[name=="Bob"])` — rule fires when
        // BOTH an adult AND someone named Bob exist. Exercises the basic
        // AND→GroupElement(AND) path (N children, no single-child wrap).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule AdultAndBob {
                    and(/persons[ age > 18 ], /persons[ name == "Bob" ]),
                    do { System.out.println("adult and bob both exist"); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            assertThat(instance.fire()).isZero();

            // Adult only → AND unsatisfied (no Bob yet).
            unit.persons.add(new Person("Alice", 30));
            assertThat(instance.fire()).isZero();

            // Add Bob (child) → Alice is adult and Bob matches name=="Bob".
            // AND satisfied; rule fires.
            unit.persons.add(new Person("Bob", 10));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("AdultAndBob");
        });
    }

    @Test
    void andSingleChild() {
        // `and(/persons[age>18])` — single-child paren form. Grammar allows
        // it; Drools' GroupElement.pack() collapses the AND at compile time,
        // so runtime shape is equivalent to a bare /persons[age>18] pattern.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule SingleChildAnd {
                    and(/persons[ age > 18 ]),
                    do { System.out.println("adult"); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            assertThat(instance.fire()).isZero();

            unit.persons.add(new Person("Alice", 30));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("SingleChildAnd");
        });
    }

    @Test
    void andEmpty_failsParse() {
        // `and()` — empty child list. Grammar requires at least one groupChild.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule EmptyAnd {
                    and(),
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withMyUnitInstance(rule, (instance, unit, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }

    @Test
    void andBare_failsParse() {
        // `and /persons[age>18]` — bare form is only sugar for NOT/EXISTS
        // per DRLXXXX §"'not' / 'exists'". AND requires parentheses.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule BareAnd {
                    and /persons[ age > 18 ],
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withMyUnitInstance(rule, (instance, unit, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
