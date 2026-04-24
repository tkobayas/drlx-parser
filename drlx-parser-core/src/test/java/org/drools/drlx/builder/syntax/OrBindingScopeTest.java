package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrBindingScopeTest extends DrlxBuilderTestSupport {

    @Test
    void orBranchBindingLocal() {
        // `or(and(var p : /persons1, /persons2[name == p.name]),
        //    and(var p : /persons2, /persons3[name == p.name]))`
        // Each branch has its own `p`; each branch is an independent join.
        // Top-level fires once per satisfied branch (Drools LogicTransformer
        // expands top-level OR into separate rule instances).
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule BranchLocalJoin {
                    or(and(var p : /persons1, /persons2[ name == p.name ]),
                       and(var p : /persons2, /persons3[ name == p.name ])),
                    do { System.out.println("branch fired"); }
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
            final EntryPoint persons3 = kieSession.getEntryPoint("persons3");

            // First branch satisfied: Alice in persons1 and persons2.
            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Alice", 25));
            // Second branch satisfied: Bob in persons2 and persons3.
            persons2.insert(new Person("Bob", 40));
            persons3.insert(new Person("Bob", 50));

            // Each branch fires once (Drools OR-expansion).
            assertThat(kieSession.fireAllRules()).isEqualTo(2);
            assertThat(fired).hasSize(2);
        });
    }

    @Test
    void orDirectBindingBranchLocal() {
        // `or(var p : /persons1, var p : /persons2)` — same bind name on
        // both branches, different streams. Each `p` is distinct; no
        // unification across branches. Fires once per matching branch.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule DirectBranchLocal {
                    or(var p : /persons1, var p : /persons2),
                    do { System.out.println("branch fired"); }
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

            persons1.insert(new Person("Alice", 30));
            persons2.insert(new Person("Bob", 40));

            // Each branch fires once — both matched.
            assertThat(kieSession.fireAllRules()).isEqualTo(2);
            assertThat(fired).hasSize(2);
        });
    }

    @Test
    void orBindingNotVisibleAfterGroup() {
        // `or(var p : /persons1), /persons2[ name == p.name ]` — `p`
        // is branch-local, NOT visible to the sibling after `or(...)`.
        // Drools surfaces this at KieBase build (or KieSession creation)
        // as an unresolved reference on `p`. Assert any RuntimeException.
        // This documents the branch-local stance at the system boundary
        // (Decision #2 in the spec). If Drools ever changes to produce a
        // silent zero-fire instead, update this test accordingly.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule UnresolvedAfterOr {
                    or(var p : /persons1),
                    /persons2[ name == p.name ],
                    do { System.out.println("should not build"); }
                }
                """;

        assertThatThrownBy(() -> withSession(rule, (ks, listener) -> { /* never runs */ }))
                .isInstanceOf(RuntimeException.class);
    }
}
