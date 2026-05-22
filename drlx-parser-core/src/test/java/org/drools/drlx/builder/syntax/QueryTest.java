package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
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
}
