package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
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

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            persons1.insert(new Person("Alice", 30));
            persons1.insert(new Person("Bob", 40));
            persons2.insert(new Person("Alice", 25));
            persons2.insert(new Person("Carol", 50));

            // Exactly one pair joins: (Alice/persons1, Alice/persons2).
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("JoinByName");
        });
    }

    @Test
    void andBindingExplicitType() {
        // Explicit-type form: `Person p = /persons1`. Same join semantics,
        // different surface syntax. Confirms `boundOopath` handles both
        // `:` and `=` operators.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule JoinByNameExplicitType {
                    and(Person p = /persons1, /persons2[ name == p.name ]),
                    do { System.out.println("joined"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Alice", 25));

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("JoinByNameExplicitType");
        });
    }

    @Test
    void andBothChildrenBound() {
        // Both children carry bindings. Binding `p1` visible to second
        // child's condition; second child also binds `p2`. Confirms no
        // cross-binding leakage or ordering bugs.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule BothBound {
                    and(var p1 : /persons1, var p2 : /persons2[ name == p1.name ]),
                    do { System.out.println("bound pair"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Alice", 25));
            persons2.insert(new Person("Bob", 40));

            // One matching pair: (Alice, Alice).
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("BothBound");
        });
    }

    @Test
    void bareOopathStillWorks() {
        // Regression: the grammar refactor and the visitor bound-arm must
        // not break existing bare-oopath group children.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule BareOopathsInAnd {
                    and(/persons1[ age > 18 ], /persons2[ age > 18 ]),
                    do { System.out.println("both adult"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            final EntryPoint persons1 = kieSession.getEntryPoint("persons1");
            final EntryPoint persons2 = kieSession.getEntryPoint("persons2");

            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Bob", 40));

            // No join constraint — fires once per Cartesian match (1×1 = 1).
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("BareOopathsInAnd");
        });
    }
}
