package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Employee;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InlineCastTest extends DrlxBuilderTestSupport {

    @Test
    void testInlineCast() {
        // Insert both Person and Employee into the same entry point.
        // The rule uses #Employee inline cast, so only Employee instances should match.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Employee;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CheckEmployee {
                    Person e : /persons#Employee[ department == "Engineering" ],
                    do { System.out.println(e); }
                }
                """;

        withInstance(rule, (instance, unit, listener) -> {
            // Insert a plain Person — should NOT match
            unit.persons.add(new Person("Alice", 30));
            // Insert an Employee with matching department — should match
            unit.persons.add(new Employee("Bob", 25, "Engineering"));
            // Insert an Employee with non-matching department — should NOT match
            unit.persons.add(new Employee("Carol", 28, "Marketing"));

            final int fired = instance.fire();

            assertThat(fired).isEqualTo(1);
        });
    }

    @Test
    void testInlineCastWithoutConstraints() {
        // Inline cast without any constraint conditions — just type filtering
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Employee;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule MatchAllEmployees {
                    Person e : /persons#Employee,
                    do { System.out.println(e); }
                }
                """;

        withInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 30));
            unit.persons.add(new Employee("Bob", 25, "Engineering"));
            unit.persons.add(new Employee("Carol", 28, "Marketing"));

            final int fired = instance.fire();

            // Only the 2 Employees should match, not the plain Person
            assertThat(fired).isEqualTo(2);
        });
    }
}
