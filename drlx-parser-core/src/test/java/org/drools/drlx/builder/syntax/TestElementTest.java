package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class TestElementTest extends DrlxBuilderTestSupport {

    @Test
    void test_filtersByExpression() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    test p.age > 30,
                    do { System.out.println(p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint persons = session.getEntryPoint("persons");
            persons.insert(new Person("Alice", 40));
            persons.insert(new Person("Bob", 25));
            assertThat(session.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R");
        });
    }

    @Test
    void test_betaJoinAcrossTwoBindings() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    var q : /persons,
                    test p.age > q.age,
                    do { System.out.println(p + " > " + q); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint persons = session.getEntryPoint("persons");
            persons.insert(new Person("Alice", 40));
            persons.insert(new Person("Bob", 25));
            // Pairs: (Alice,Alice) eq, (Alice,Bob) Alice>Bob ✓, (Bob,Alice) Bob<Alice ✗, (Bob,Bob) eq
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void test_refiresOnPropertyUpdate() {
        // DRLX configures patterns with PropertySpecificOption.ALWAYS
        // (DrlxRuleAstRuntimeBuilder.java:105), so every property is watched by
        // default. A 'test' guard referencing an outer-scope property naturally
        // re-evaluates on update — no explicit watch list required.
        //
        // Note: drools-base's EvalCondition.isPatternScopeDelimiter() == true
        // does NOT inhibit re-evaluation in this configuration; the pattern's
        // ALWAYS-mode mask covers all properties regardless of where they're
        // referenced.
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    test p.age > 30,
                    do { System.out.println(p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint persons = session.getEntryPoint("persons");
            Person alice = new Person("Alice", 25);
            org.kie.api.runtime.rule.FactHandle handle = persons.insert(alice);
            assertThat(session.fireAllRules()).isZero();

            alice.setAge(40);
            persons.update(handle, alice);
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void test_falseExpressionPreventsFiring() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    test p.age > 999,
                    do { System.out.println(p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint persons = session.getEntryPoint("persons");
            persons.insert(new Person("Alice", 40));
            assertThat(session.fireAllRules()).isZero();
        });
    }
}
