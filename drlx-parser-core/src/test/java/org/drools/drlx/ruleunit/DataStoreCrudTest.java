package org.drools.drlx.ruleunit;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for issue #37 (DataStore CRUD): each rule consequence
 * calls a {@code DataStore} method on a unit-field reference (e.g.
 * {@code persons1.add(p)}), and {@link DrlxRuleUnitInstance} provides the
 * runtime surface. The class is intentionally named after the broader
 * #37 scope; it will grow as additional sub-pieces (update, with-block) land.
 */
@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DataStoreCrudTest {

    @Test
    void consequenceCanCallDataStoreAdd() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CopyAdults {
                    Person p : /persons[ age > 30 ],
                    do { persons1.add(p); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        Person bob = new Person("Bob", 20);
        unit.persons.add(alice);
        unit.persons.add(bob);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs = TestDataObserver.subscribeTo(unit.persons1);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(obs.inserted()).containsExactly(alice);
        }
    }
}
