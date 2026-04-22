package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class ExistsTest extends DrlxBuilderTestSupport {

    @Test
    void existsAllowsMatch() {
        // `exists /persons[age>=18]` — rule fires ONLY when at least one adult
        // person is inserted. Inverse of notSuppressesMatch.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule HasAdult {
                    exists /persons[ age >= 18 ],
                    do { System.out.println("adult exists"); }
                }
                """;

        withSession(rule, kieSession -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // No facts → EXISTS unsatisfied, rule does not fire.
            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(fired).isEmpty();

            // Insert a child → still no adult, EXISTS unsatisfied.
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();

            // Insert an adult → EXISTS satisfied, rule fires once.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("HasAdult");
        });
    }
}
