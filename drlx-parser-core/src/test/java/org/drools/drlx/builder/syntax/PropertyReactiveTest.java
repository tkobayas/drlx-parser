package org.drools.drlx.builder.syntax;

import org.drools.drlx.builder.DataStoreSupport;
import org.drools.drlx.domain.ReactiveEmployee;
import org.drools.ruleunits.api.DataHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyReactiveTest extends DrlxBuilderTestSupport {

    private static final String SALARY_CONSTRAINT_RULE = """
            package org.drools.drlx.parser;

            import org.drools.drlx.domain.ReactiveEmployee;
            import org.drools.drlx.ruleunit.MyUnit;

            unit MyUnit;

            rule R1 {
                var e : /reactiveEmployees[salary > 5000],
                do { }
            }
            """;

    @Test
    void externalUpdate_firesOnConstraintProperty() {
        withMyUnitInstance(SALARY_CONSTRAINT_RULE, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            DataHandle dh = unit.reactiveEmployees.add(emp);

            assertThat(instance.fire()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // salary IS used in the constraint → re-fire expected
            emp.setSalary(7000);
            DataStoreSupport.update(unit.reactiveEmployees, dh, emp,
                    instance.getRuleBase(), "salary");
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void externalUpdate_doesNotFireOnUnrelatedProperty() {
        withMyUnitInstance(SALARY_CONSTRAINT_RULE, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            DataHandle dh = unit.reactiveEmployees.add(emp);

            assertThat(instance.fire()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // bonusPay NOT used in the constraint → no re-fire
            emp.setBonusPay(2000);
            DataStoreSupport.update(unit.reactiveEmployees, dh, emp,
                    instance.getRuleBase(), "bonusPay");
            assertThat(instance.fire()).isEqualTo(0);
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void consequenceUpdate_compactWith_firesOnConstraintProperty() {
        // R1 modifies salary (read by R2's constraint) via CompactWithExpression
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[basePay > 3000],
                    do { reactiveEmployees.update(e{salary = 9999}); }
                }

                rule R2 {
                    var e : /reactiveEmployees[salary > 9000],
                    do { }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(5000, 4000, 1000);
            unit.reactiveEmployees.add(emp);

            // R1 fires (basePay 4000 > 3000), sets salary to 9999.
            // R2 should fire because salary (modified by R1) is in R2's constraint.
            // Use fire(10) to cap iterations — before property reactivity is working,
            // AllSetBitMask would cause R1 to re-fire infinitely.
            instance.fire(10);
            assertThat(listener.getAfterMatchFired()).contains("R1", "R2");
        });
    }

    @Test
    void consequenceUpdate_compactWith_doesNotFireOnUnrelatedProperty() {
        // R1 modifies bonusPay (NOT read by R2's constraint) via CompactWithExpression
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[basePay > 3000],
                    do { reactiveEmployees.update(e{bonusPay = 9999}); }
                }

                rule R2 {
                    var e : /reactiveEmployees[salary > 9000],
                    do { }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(5000, 4000, 1000);
            unit.reactiveEmployees.add(emp);

            // R1 fires (basePay 4000 > 3000), sets bonusPay to 9999.
            // R2 should NOT fire because bonusPay is not in R2's constraint
            // and salary (5000) does not match salary > 9000.
            instance.fire(10);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void consequenceUpdate_plainSetter_treatsAllPropertiesAsChanged() {
        // R1 modifies bonusPay (NOT read by R2's constraint) via plain setter + update.
        // Because the rewriter cannot detect which properties were modified in
        // arbitrary control flow preceding the update() call, AllSetBitMask is used.
        // This means R2 re-fires even though only bonusPay changed — a safe default
        // that preserves correctness at the cost of unnecessary re-evaluations.
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[basePay > 3000],
                    do { e.setBonusPay(9999); reactiveEmployees.update(e); }
                }

                rule R2 {
                    var e : /reactiveEmployees[salary > 4000],
                    do { }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(5000, 4000, 1000);
            unit.reactiveEmployees.add(emp);

            // R1 fires (basePay 4000 > 3000) and calls update with AllSetBitMask.
            // R2 fires because AllSetBitMask treats all properties (including salary)
            // as changed, even though only bonusPay was actually modified.
            // Use fire(10) to cap iterations — AllSetBitMask causes R1 to re-fire.
            instance.fire(10);
            assertThat(listener.getAfterMatchFired()).contains("R1", "R2");
        });
    }

    @Test
    void externalUpdate_withoutPropertyNames_firesAlways() {
        withMyUnitInstance(SALARY_CONSTRAINT_RULE, (instance, unit, listener) -> {
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            DataHandle dh = unit.reactiveEmployees.add(emp);

            assertThat(instance.fire()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // No property names → AllSetBitMask → re-fire (backward compat)
            emp.setBonusPay(2000);
            unit.reactiveEmployees.update(dh, emp);
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }
}
