package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;

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

        withInstance(rule, (instance, unit, listener) -> {
            assertThat(instance.fire()).isZero();

            unit.seniors.add(new Person("Grandpa", 75));
            assertThat(instance.fire()).isEqualTo(1);
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

        withInstance(rule, (instance, unit, listener) -> {
            unit.seniors.add(new Person("Grandpa", 75));
            unit.juniors.add(new Person("Kid", 10));

            // Two firings — one per branch — since LogicTransformer splits OR
            // into two rule instances. Each instance's conditions are
            // satisfied once by the single matching fact on its branch.
            assertThat(instance.fire()).isEqualTo(2);
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

        withInstance(rule, (instance, unit, listener) -> {
            // Middle-aged → neither branch.
            unit.seniors.add(new Person("Middle1", 40));
            unit.juniors.add(new Person("Middle2", 40));

            assertThat(instance.fire()).isZero();
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

        assertThatThrownBy(() -> withInstance(rule, (instance, unit, listener) -> { /* unreachable */ }))
                .hasMessageContaining("parse error");
    }
}
