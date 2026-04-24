package org.drools.drlx.builder.syntax;

import java.nio.file.Files;
import java.nio.file.Path;
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

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            // No under-18 → rule fires once.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("OnlyAdults");

            // Insert an under-18 → the NOT becomes unsatisfied; no further firings.
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(listener.getAfterMatchFired()).containsExactly("OnlyAdults");
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

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");
            final EntryPoint orders = kieSession.getEntryPoint("orders");

            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("OrphanedPerson");

            orders.insert(new Order("O1", 30, 100));
            assertThat(kieSession.fireAllRules()).isZero();
        });
    }

    @Test
    void protoRoundTrip_withNot() throws Exception {
        PatternIR inner = new PatternIR("", "", "persons", List.of("age < 18"), null, List.of(), false);
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

        assertThatThrownBy(() -> withSession(rule, (kieSession, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error")
                .hasMessageContaining("var");
    }

    @Test
    void notParens_singleElement() {
        // Spec Decision #1 — single-element in parens is accepted and behaves
        // identically to the bare form. `not(/persons[age<18])` ≡ `not /persons[age<18]`.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule OnlyAdultsParen {
                    not(/persons[ age < 18 ]),
                    do { System.out.println("only adults"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");

            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("OnlyAdultsParen");

            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();
        });
    }

    @Test
    void notMultiElement_crossProduct() {
        // `not(/persons[age<18], /orders[amount>1000])` — NOT-with-AND semantics:
        // suppresses when BOTH an under-18 person AND a high-value order exist;
        // re-fires when either is removed.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Order;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule NoUnderageHighValuePair {
                    not(/persons[ age < 18 ], /orders[ amount > 1000 ]),
                    do { System.out.println("no risky pair"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons = kieSession.getEntryPoint("persons");
            final EntryPoint orders = kieSession.getEntryPoint("orders");

            // Neither side present → NOT satisfied, rule fires once.
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("NoUnderageHighValuePair");

            // Only under-18 person → NOT still satisfied (no order match), no new firing.
            persons.insert(new Person("Charlie", 10));
            assertThat(kieSession.fireAllRules()).isZero();

            // Add high-value order → both sides match, NOT unsatisfied, still no firing.
            orders.insert(new Order("O1", 99, 5000));
            assertThat(kieSession.fireAllRules()).isZero();

            // Add orphan adult person — doesn't affect NOT state.
            persons.insert(new Person("Alice", 30));
            assertThat(kieSession.fireAllRules()).isZero();
        });
    }

    @Test
    void notEmpty_failsParse() {
        // `not()` — parens with zero inner oopaths. Grammar's first
        // oopathExpression is not optional, so this is a parse error.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule EmptyNot {
                    not(),
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, (kieSession, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
