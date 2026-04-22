package org.drools.drlx.builder.syntax;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstParseResult;
import org.drools.drlx.domain.Order;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotTest extends DrlxBuilderTestSupport {

    @Test
    void notSuppressesMatch() {
        // `not /persons[age < 18]` → rule fires ONLY when no under-18 person exists.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule OnlyAdults {
                    not /persons[ age < 18 ],
                    do { System.out.println("only adults"); }
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

            // No under-18 → rule fires once.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("OnlyAdults");

            // Insert an under-18 → the NOT becomes unsatisfied; no further firings.
            fired.clear();
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(fired).isEmpty();
        });
    }

    @Test
    void notWithOuterBinding() {
        // `Person p : /persons[...], not /orders[customerId == p.age]` — outer
        // binding 'p' is referenced inside the NOT's constraint, proving
        // beta-join from the outer pattern into the NOT group element.
        //
        // Domain note: correlates Order.customerId with Person.age only to
        // avoid adding a new integer field to Person. The semantic — "fire
        // when a person exists who has no matching order" — is what's tested.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Order;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule OrphanedPerson {
                    Person p : /persons[ age > 0 ],
                    not /orders[ customerId == p.age ],
                    do { System.out.println("orphan: " + p); }
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
            final EntryPoint orders = kieSession.getEntryPoint("orders");

            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(fired).containsExactly("OrphanedPerson");

            fired.clear();
            orders.insert(new Order("O1", 30, 100));
            assertThat(kieSession.fireAllRules()).isZero();
        });
    }

    @Test
    void protoRoundTrip_withNot() throws Exception {
        PatternIR inner = new PatternIR("", "", "persons", List.of("age < 18"), null, List.of());
        GroupElementIR notGroup = new GroupElementIR(GroupElementIR.Kind.NOT, List.of(inner));
        RuleIR ruleIR = new RuleIR("OnlyAdults", List.of(), List.of(notGroup),
                new ConsequenceIR("System.out.println(\"only adults\");"));
        CompilationUnitIR original = new CompilationUnitIR(
                "org.drools.drlx.parser",
                "MyUnit",
                List.of("org.drools.drlx.domain.Person", "org.drools.drlx.ruleunit.MyUnit"),
                List.of(ruleIR));

        Path tmpDir = Files.createTempDirectory("drlx-proto-roundtrip");
        try {
            String source = "// round-trip source";
            DrlxRuleAstParseResult.save(source, original, tmpDir);

            CompilationUnitIR reloaded = DrlxRuleAstParseResult.load(
                    source, DrlxRuleAstParseResult.parseResultFilePath(tmpDir));

            assertThat(reloaded).isEqualTo(original);
        } finally {
            Path pb = DrlxRuleAstParseResult.parseResultFilePath(tmpDir);
            Files.deleteIfExists(pb);
            Files.deleteIfExists(tmpDir);
        }
    }

    @Test
    void notWithInnerBinding_failsParse() {
        // `not var p : /persons[...]` — bindings inside `not` can never escape
        // to the outer scope. The grammar does not accept `var p :` inside
        // notElement, so the parser rejects at that level.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule IllegalInnerBinding {
                    not var p : /persons[ age < 18 ],
                    do { System.out.println("should not compile"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("parse error")
                .hasMessageContaining("var");
    }

    @Test
    void notMultiElementForm_failsParse() {
        // Multi-element `not(/a, /b)` is spec'd (DRLX §'not'/'exists' line 597)
        // but deferred to a follow-up issue. Grammar in this landing rejects it.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule MultiNotNotYet {
                    not(/persons[ age < 18 ], /persons[ age > 80 ]),
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, kieSession -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
