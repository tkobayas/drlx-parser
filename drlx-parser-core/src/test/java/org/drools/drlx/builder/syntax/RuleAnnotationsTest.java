package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleAnnotationsTest extends DrlxBuilderTestSupport {

    @Test
    void testSalienceAffectsFiringOrder() {
        // HighSalience (10) must fire before LowSalience (5) for the same fact.
        // An AgendaEventListener captures firing order externally.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.annotations.Salience;

                unit MyUnit;

                @Salience(5)
                rule LowSalience {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("low"); }
                }

                @Salience(10)
                rule HighSalience {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println("high"); }
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

            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("Alice", 30));

            final int firedCount = kieSession.fireAllRules();

            assertThat(firedCount).isEqualTo(2);
            assertThat(fired).containsExactly("HighSalience", "LowSalience");
        });
    }
}
