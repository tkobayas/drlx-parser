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
