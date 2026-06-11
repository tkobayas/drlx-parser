package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotExistsBindingTest extends DrlxBuilderTestSupport {

    @Test
    void notBindingWithinGroup() {
        // `not(var p : /persons1, /persons2[name == p.name])` — fires
        // when NO (p, matching-person-in-persons2) pair joins. Binding
        // `p` is a within-group join; never visible outside the `not`.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule NoJoinExists {
                    not(var p : /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("no join"); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            // Alice in persons1, no Alice in persons2 → no join exists → fires.
            unit.persons1.add(new Person("Alice", 30));
            unit.persons2.add(new Person("Bob", 40));

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("NoJoinExists");
        });
    }

    @Test
    void existsBindingWithinGroup() {
        // `exists(var p : /persons1, /persons2[name == p.name])` — fires
        // when at least one joined pair exists. Binding is within-group.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule JoinExists {
                    exists(var p : /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("join exists"); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            // Alice in both → join exists → fires.
            unit.persons1.add(new Person("Alice", 30));
            unit.persons2.add(new Person("Alice", 25));

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("JoinExists");
        });
    }
}
