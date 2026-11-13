package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiSegmentOOPathTest extends DrlxBuilderTestSupport {

    @Test
    void twoSegmentOOPathCollectsMatchingAddresses() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var addr : /persons/addresses[ city == "London" ],
                    fn { results.add(addr.city); }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            Person alice = new Person("Alice", 30);
            alice.addAddress(new Address("London"));
            alice.addAddress(new Address("Paris"));

            Person bob = new Person("Bob", 25);
            bob.addAddress(new Address("London"));

            unit.persons.add(alice);
            unit.persons.add(bob);

            assertThat(instance.fire()).isEqualTo(2);
            assertThat(unit.results).containsExactlyInAnyOrder("London", "London");
        });
    }

    @Test
    void twoSegmentWithRootConstraint() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var addr : /persons[ age > 25 ]/addresses[ city == "London" ],
                    fn { results.add(addr.city); }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            Person alice = new Person("Alice", 30);
            alice.addAddress(new Address("London"));

            Person bob = new Person("Bob", 20);
            bob.addAddress(new Address("London"));

            unit.persons.add(alice);
            unit.persons.add(bob);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(unit.results).containsExactly("London");
        });
    }
}
