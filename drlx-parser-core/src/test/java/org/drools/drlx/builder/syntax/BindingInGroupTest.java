package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class BindingInGroupTest extends DrlxBuilderTestSupport {

    @Test
    void andBindingVisibleToRightSibling() {
        // `and(var p : /persons1, /persons2[name == p.name])` — joins two
        // person streams by name. Binding `p` from the first child must be
        // visible to the second child's condition. Primary AC for #17.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule JoinByName {
                    and(var p : /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("joined"); }
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

            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            persons1.insert(new Person("Alice", 30));
            persons1.insert(new Person("Bob", 40));
            persons2.insert(new Person("Alice", 25));
            persons2.insert(new Person("Carol", 50));

            // Exactly one pair joins: (Alice/persons1, Alice/persons2).
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }
}
