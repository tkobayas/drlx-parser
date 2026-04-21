package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Car;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeInferenceTest extends DrlxBuilderTestSupport {

    @Test
    void bareVarPattern_inferredFromUnit() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule OnlyAdults {
                    var p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");
            persons.insert(new Person("Alice", 30));
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void explicitTypeMatchesUnitField() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule ExplicitMatches {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void inlineCastBeatsInference() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Car;
                import org.drools.drlx.domain.Vehicle;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule FastCars {
                    var v : /objects#Car[ speed > 80 ],
                    do { System.out.println(v); }
                }
                """;

        withSession(rule, kieSession -> {
            final EntryPoint objects = kieSession.getEntryPoint("objects");
            objects.insert(new Car("ABC", 120));
            objects.insert(new Car("XYZ", 40));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void missingUnitDeclaration_failsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                rule NoUnit {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("'unit'");
    }

    @Test
    void missingUnitImport_failsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit MyUnit;

                rule NoImport {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("Unit class 'MyUnit' not found")
                .hasMessageContaining("import");
    }

    @Test
    void unitClassNotOnClasspath_failsLoud() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.NotARealUnit;

                unit NotARealUnit;

                rule BadUnit {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("not on classpath");
    }
}
