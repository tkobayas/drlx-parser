package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Person;
import org.drools.ruleunits.api.DataHandle;
import org.junit.jupiter.api.Test;

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
        withInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 25));
            assertThat(instance.fire()).isEqualTo(1);
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
        withInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 25));
            // Pairs: (Alice,Alice) eq, (Alice,Bob) Alice>Bob ✓, (Bob,Alice) Bob<Alice ✗, (Bob,Bob) eq
            assertThat(instance.fire()).isEqualTo(1);
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
        withInstance(rule, (instance, unit, listener) -> {
            Person alice = new Person("Alice", 25);
            DataHandle handle = unit.persons.add(alice);
            assertThat(instance.fire()).isZero();

            alice.setAge(40);
            unit.persons.update(handle, alice);
            assertThat(instance.fire()).isEqualTo(1);
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
        withInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 40));
            assertThat(instance.fire()).isZero();
        });
    }
}
