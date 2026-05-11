package org.drools.drlx.ruleunit;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Probe for issue #37 (DataStore CRUD): exercises a consequence that calls
 * {@code persons1.add(p)} on a DataStore reference, using
 * {@link DrlxRuleUnitInstance} to provide the runtime surface.
 *
 * <p><b>Status:</b> Currently disabled. The probe surfaced the gap in DRLX:
 * unit-class fields are not exposed as globals on the package, so the
 * JavaParser symbol solver fails to resolve {@code persons1} in the
 * consequence body with {@code UnsolvedSymbol}. Mirror the upstream
 * exec-model behaviour ({@code PackageModel.addRuleUnitVariable}) in
 * {@code DrlxRuleAstRuntimeBuilder}: every unit-class field should register
 * as a global on the rule package, in addition to the existing entry-point
 * map. Once that lands, remove {@link Disabled} on the method below — the
 * test should pass without further changes.
 */
@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DataStoreAddProbeTest {

    @Test
    @Disabled("blocked on #37 — unit-class fields not yet registered as globals; "
            + "mirror PackageModel.addRuleUnitVariable in DrlxRuleAstRuntimeBuilder")
    void consequenceCanCallDataStoreAdd() {
        String rule =
                """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CopyAdults {
                    Person p : /persons[ age > 30 ],
                    do { persons1.add(p); }
                }
                """;
        KieBase kieBase = new DrlxRuleBuilder().build(rule);

        MyUnit unit = new MyUnit();
        Person alice = new Person("Alice", 40);
        Person bob = new Person("Bob", 20);
        unit.persons.add(alice);
        unit.persons.add(bob);

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TestDataObserver<Person> obs = TestDataObserver.subscribeTo(unit.persons1);

            assertThat(instance.fire()).isEqualTo(1);
            assertThat(obs.inserted()).containsExactly(alice);
        }
    }
}
