package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

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
}
