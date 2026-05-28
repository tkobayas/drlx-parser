package org.drools.drlx.ruleunit;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for issues #37, #45 (DataStore CRUD), and #34
 * (compact setter blocks): each rule consequence calls a
 * {@code DataStore} method on a unit-field reference
 * (e.g. {@code persons1.add(p)}, {@code persons.update(p)}), and
 * {@link DrlxRuleUnitInstance} provides the runtime surface.
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

    @Test
    void removeByObjectViaDataStore() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule RemoveAdults {
                    Person p : /persons[ age > 30 ],
                    do { persons.remove(p); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs = TestDataObserver.subscribeTo(unit.persons);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(obs.removed()).hasSize(1);
        }
    }

    @Test
    void consequenceCanReferenceMultipleUnitFields() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule FanOut {
                    Person p : /persons[ age > 30 ],
                    do { persons1.add(p); persons2.add(p); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs1 = TestDataObserver.subscribeTo(unit.persons1);
            TestDataObserver<Person> obs2 = TestDataObserver.subscribeTo(unit.persons2);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(obs1.inserted()).containsExactly(alice);
            assertThat(obs2.inserted()).containsExactly(alice);
        }
    }

    @Test
    void updateByObjectViaDataStore() {
        // Reset age so the pattern no longer matches after the update — otherwise
        // the rule re-fires in an infinite loop (the update notifies the engine,
        // age > 30 is still true, etc.).
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule ResetAdults {
                    Person p : /persons[ age > 30 ],
                    do { p.setAge(0); persons.update(p); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs = TestDataObserver.subscribeTo(unit.persons);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(obs.updated()).hasSize(1);
            assertThat(alice.getAge()).isEqualTo(0);
        }
    }

    @Test
    void compactWithStandaloneSetterBlock() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule ResetWithCompactWith {
                    Person p : /persons[ age > 30 ],
                    do p{age = 0};
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(alice.getAge()).isEqualTo(0);
        }
    }

    @Test
    void compactWithMultipleAssignments() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule RenameAndReset {
                    Person p : /persons[ age > 30 ],
                    do p{name = "Reset", age = 0};
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(alice.getName()).isEqualTo("Reset");
            assertThat(alice.getAge()).isEqualTo(0);
        }
    }

    @Test
    void updateOfMissingFactThrows() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule UpdateMissing {
                    Person p : /persons[ age > 30 ],
                    do { Person stranger = new Person("Stranger", 99); persons.update(stranger); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        unit.persons.add(alice);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThatThrownBy(instance::fire)
                    .hasMessageContaining("DataStore 'persons' has no DataHandle for the given fact");
        }
    }
}
