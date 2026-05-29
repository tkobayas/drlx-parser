package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.drools.drlx.domain.Trust;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.api.runtime.rule.Variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTest extends DrlxBuilderTestSupport {

    @Test
    void queryDefinitionViaApi() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            QueryResults results = instance.executeQuery("PersonsByAge", 25, Variable.v);

            List<String> names = new ArrayList<>();
            for (QueryResultsRow row : results) {
                Person p = (Person) row.get("result");
                names.add(p.getName());
            }
            assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
        }
    }

    @Test
    void queryInvocationFromRule() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge(25, var p),
                    do { results.add(p); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
        }
    }

    @Test
    void queryMultipleParameters() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAgeRange(int minAge, int maxAge, Person result) {
                    Person result : /persons[age >= minAge, age <= maxAge],
                }

                rule R1 {
                    /personsByAgeRange(25, 35, var p),
                    do { results.add(p); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("Alice");
        }
    }

    @Test
    void queryDefinitionViaApiMultipleParams() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAgeRange(int minAge, int maxAge, Person result) {
                    Person result : /persons[age >= minAge, age <= maxAge],
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            QueryResults results = instance.executeQuery("PersonsByAgeRange", 25, 35, Variable.v);

            List<String> names = new ArrayList<>();
            for (QueryResultsRow row : results) {
                Person p = (Person) row.get("result");
                names.add(p.getName());
            }
            assertThat(names).containsExactlyInAnyOrder("Alice");
        }
    }

    @Test
    void queryWrongArgCountFails() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge(25),
                    do { System.out.println("wrong"); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expects 2 arguments but got 1");
    }

    @Test
    void passiveQueryInvocationDoesNotWakeRule() {
        final String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var l : /locations[city == "paris"],
                    ?/personsByAge(25, var p),
                    do { System.out.println("fired"); }
                }
                """;

        withSession(source, (kieSession, listener) -> {
            final EntryPoint locations = kieSession.getEntryPoint("locations");
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // 1. Reactive side first — no query match yet, no fire
            locations.insert(new Location("paris", "centre"));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // 2. Passive query side — a complete match now exists
            //    (location × query result), but because the query invocation
            //    is passive, this insertion MUST NOT wake R1
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void passiveQueryInvocationWakesWhenReactiveSideFires() {
        final String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Location;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var l : /locations[city == "paris"],
                    ?/personsByAge(25, var p),
                    do { System.out.println("fired"); }
                }
                """;

        withSession(source, (kieSession, listener) -> {
            final EntryPoint locations = kieSession.getEntryPoint("locations");
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // Passive side first — no fire
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // Reactive side triggers — picks up pending passive query results
            locations.insert(new Location("paris", "centre"));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void recursiveQueryTransitiveClosure() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Trust;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule Trusts(String a, String b) {
                    or(
                        /trusts(a, b),
                        and(/trusts(a, var z), /trusts(z, b))
                    ),
                }

                rule R1 {
                    /trusts("A", var t),
                    do { results.add(t); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.trusts.add(new Trust("A", "B"));
        unit.trusts.add(new Trust("B", "C"));
        unit.trusts.add(new Trust("C", "D"));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            // Transitive closure from A: direct A->B, via B->C, via C->D
            assertThat(unit.results).containsExactlyInAnyOrder("B", "C", "D");
        }
    }

    @Test
    void queryResultBindingNamedAccess() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var t : /personsByAge(25, var p),
                    do { results.add(t.result); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
        }
    }

    @Test
    void queryResultBindingIndexedAccess() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var t : /personsByAge(25, var p),
                    do { results.add(t[1]); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("Alice", "Charlie");
        }
    }

    @Test
    void queryResultBindingNamedEqualsIndexed() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var t : /personsByAge(25, var p),
                    do { results.add(t.result == t[1]); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            // t.result and t[1] should refer to the same Person object
            assertThat(unit.results).allMatch(o -> Boolean.TRUE.equals(o));
            assertThat(unit.results).hasSize(2);
        }
    }
}
