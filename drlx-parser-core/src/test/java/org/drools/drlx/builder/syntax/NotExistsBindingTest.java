package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class NotExistsBindingTest extends DrlxBuilderTestSupport {

    @Test
    void notBindingWithinGroup() {
        // `not(var p : /persons1, /persons2[name == p.name])` — fires
        // when NO (p, matching-person-in-persons2) pair joins. Binding
        // `p` is a within-group join; never visible outside the `not`.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule NoJoinExists {
                    not(var p : /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("no join"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            // Alice in persons1, no Alice in persons2 → no join exists → fires.
            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Bob", 40));

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }

    @Test
    void existsBindingWithinGroup() {
        // `exists(var p : /persons1, /persons2[name == p.name])` — fires
        // when at least one joined pair exists. Binding is within-group.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule JoinExists {
                    exists(var p : /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("join exists"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final List<String> fired = new ArrayList<>();
            kieSession.addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }
            });

            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            // Alice in both → join exists → fires.
            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Alice", 25));

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).hasSize(1);
        });
    }
}
