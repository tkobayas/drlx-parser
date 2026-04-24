package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Employee;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

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

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            // Insert a plain Person — should NOT match
            entryPoint.insert(new Person("Alice", 30));
            // Insert an Employee with matching department — should match
            entryPoint.insert(new Employee("Bob", 25, "Engineering"));
            // Insert an Employee with non-matching department — should NOT match
            entryPoint.insert(new Employee("Carol", 28, "Marketing"));

            final int fired = kieSession.fireAllRules();

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

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("Alice", 30));
            entryPoint.insert(new Employee("Bob", 25, "Engineering"));
            entryPoint.insert(new Employee("Carol", 28, "Marketing"));

            final int fired = kieSession.fireAllRules();

            // Only the 2 Employees should match, not the plain Person
            assertThat(fired).isEqualTo(2);
        });
    }
}
