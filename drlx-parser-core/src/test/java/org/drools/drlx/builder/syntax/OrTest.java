package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrTest extends DrlxBuilderTestSupport {

    @Test
    void orFirstBranchFires() {
        // `or(/seniors[age>60], /juniors[age<18])` — either branch suffices.
        // Insert only a senior → rule fires once for the first branch.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule SeniorOrJunior {
                    or(/seniors[ age > 60 ], /juniors[ age < 18 ]),
                    do { System.out.println("senior or junior"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint seniors = kieSession.getEntryPoint("seniors");

            assertThat(kieSession.fireAllRules()).isZero();

            seniors.insert(new Person("Grandpa", 75));
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("SeniorOrJunior");
        });
    }

    @Test
    void orBothBranchesFire() {
        // Both branches match → LogicTransformer expands top-level OR into
        // two rules internally; fire count reflects both branches firing
        // once each (2 total).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule SeniorOrJunior {
                    or(/seniors[ age > 60 ], /juniors[ age < 18 ]),
                    do { System.out.println("senior or junior"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint seniors = kieSession.getEntryPoint("seniors");
            final EntryPoint juniors = kieSession.getEntryPoint("juniors");

            seniors.insert(new Person("Grandpa", 75));
            juniors.insert(new Person("Kid", 10));

            // Two firings — one per branch — since LogicTransformer splits OR
            // into two rule instances. Each instance's conditions are
            // satisfied once by the single matching fact on its branch.
            assertThat(kieSession.fireAllRules()).isEqualTo(2);
            assertThat(listener.getAfterMatchFired()).containsExactly("SeniorOrJunior", "SeniorOrJunior");
        });
    }

    @Test
    void orBlocksWhenNeither() {
        // Neither branch satisfied → rule does not fire.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule SeniorOrJunior {
                    or(/seniors[ age > 60 ], /juniors[ age < 18 ]),
                    do { System.out.println("senior or junior"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint seniors = kieSession.getEntryPoint("seniors");
            final EntryPoint juniors = kieSession.getEntryPoint("juniors");

            // Middle-aged → neither branch.
            seniors.insert(new Person("Middle1", 40));
            juniors.insert(new Person("Middle2", 40));

            assertThat(kieSession.fireAllRules()).isZero();
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void orEmpty_failsParse() {
        // `or()` — empty child list. Grammar requires at least one groupChild.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule EmptyOr {
                    or(),
                    do { System.out.println("unreachable"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, (kieSession, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
