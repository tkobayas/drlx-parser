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

    @Test
    void queryResultBindingObjectsMethod() {
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
                    do { results.add(t.objects().length); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            // PersonsByAge has 2 parameters (minAge, result) → objects() length is 2
            assertThat(unit.results).containsExactly(2);
        }
    }

    @Test
    void queryResultBindingHandleIndexedAccess() {
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
                    do { results.add(t.handles()[1].getObject()); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            assertThat(unit.results).hasSize(1);
            assertThat(unit.results.get(0)).isInstanceOf(Person.class);
            assertThat(((Person) unit.results.get(0)).getName()).isEqualTo("Alice");
        }
    }

    @Test
    void queryResultBindingHandleNamedAccess() {
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
                    do { results.add(t.handles().result.getObject()); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            assertThat(unit.results).hasSize(1);
            assertThat(unit.results.get(0)).isInstanceOf(Person.class);
            assertThat(((Person) unit.results.get(0)).getName()).isEqualTo("Alice");
        }
    }

    @Test
    void queryResultBindingHandleIdentity() {
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
                    do {
                        results.add(t.handles()[1].getObject() == t.result);
                        results.add(t.handles().result.getObject() == t.result);
                    }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            assertThat(unit.results).allMatch(o -> Boolean.TRUE.equals(o));
            assertThat(unit.results).hasSize(2);
        }
    }

    @Test
    void namedQueryAccessBasic() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge[minAge == 25, var p : result],
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
    void testExpressionWithQueryOutputVariable() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge(20, var p),
                    test p.age > 35,
                    do { results.add(p); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Charlie", 40));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactly("Charlie");
        }
    }

    @Test
    void namedQueryAccessAllInputs() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAgeRange(int minAge, int maxAge, Person result) {
                    Person result : /persons[age >= minAge, age <= maxAge],
                }

                rule R1 {
                    /personsByAgeRange[minAge == 25, maxAge == 35, var p : result],
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
            assertThat(names).containsExactly("Alice");
        }
    }

    @Test
    void namedQueryAccessOrderIndependence() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge[var p : result, minAge == 25],
                    do { results.add(p); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));
        unit.persons.add(new Person("Bob", 20));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            List<String> names = unit.results.stream()
                    .map(o -> ((Person) o).getName())
                    .toList();
            assertThat(names).containsExactly("Alice");
        }
    }

    @Test
    void namedQueryAccessErrorMissingParameter() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge[minAge == 25],
                    do { results.add("wrong"); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing parameter")
                .hasMessageContaining("result");
    }

    @Test
    void namedQueryAccessErrorUnknownParameter() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge[badName == 25, var p : result],
                    do { results.add(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown query parameter")
                .hasMessageContaining("badName");
    }

    @Test
    void namedQueryAccessErrorMixingPositionalAndNamed() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge(25)[var p : result],
                    do { results.add(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot mix positional arguments");
    }

    @Test
    void namedQueryAccessErrorSelfReferencing() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Trust;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule Trusts(String a, String b) {
                    or(
                        /trusts[a == a, b == b],
                        and(/trusts(a, var z), /trusts(z, b))
                    ),
                }

                rule R1 {
                    /trusts("A", var t),
                    do { results.add(t); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("self-referencing query")
                .hasMessageContaining("cannot use named access");
    }

    @Test
    void namedQueryAccessErrorNonEqOperator() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    /personsByAge[minAge >= 25, var p : result],
                    do { results.add(p); }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(source))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("must use '=='");
    }

    @Test
    void namedQueryAccessWithResultBinding() {
        String source = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule PersonsByAge(int minAge, Person result) {
                    Person result : /persons[age >= minAge],
                }

                rule R1 {
                    var t : /personsByAge[minAge == 25, var p : result],
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
    void queryResultBindingCoexistsWithOutputVariables() {
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
                    do { results.add(p.name + ":" + ((org.drools.drlx.domain.Person)t.result).name); }
                }
                """;

        KieBase kieBase = newBuilder().build(source);
        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 30));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            instance.fire();

            // p and t.result should refer to the same Person
            assertThat(unit.results).containsExactly("Alice:Alice");
        }
    }
}
