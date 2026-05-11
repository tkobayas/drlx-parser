package org.drools.drlx.ruleunit;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DrlxRuleUnitInstanceTest {

    private static final String RULE =
            """
            package org.drools.drlx.parser;

            import org.drools.drlx.domain.Person;
            import org.drools.drlx.ruleunit.MyUnit;
            unit MyUnit;

            rule AdultMatch {
                Person p : /persons[ age > 30 ],
                do { System.out.println(p); }
            }
            """;

    @Test
    void firesRuleAgainstPrePopulatedDataStore() {
        KieBase kieBase = new DrlxRuleBuilder().build(RULE);

        MyUnit unit = new MyUnit();
        unit.persons.add(new Person("Alice", 40));
        unit.persons.add(new Person("Bob", 20));

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void exposesRuleUnitData() {
        KieBase kieBase = new DrlxRuleBuilder().build(RULE);
        MyUnit unit = new MyUnit();

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.ruleUnitData()).isSameAs(unit);
        }
    }

    @Test
    void factsAddedAfterConstructionAreVisibleToRules() {
        KieBase kieBase = new DrlxRuleBuilder().build(RULE);
        MyUnit unit = new MyUnit();

        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.persons.add(new Person("Alice", 40));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void closeIsIdempotentViaTryWithResources() {
        KieBase kieBase = new DrlxRuleBuilder().build(RULE);
        MyUnit unit = new MyUnit();
        DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit);
        instance.close();
        // second close must not throw — try-with-resources may call it again under exception paths
        instance.close();
    }
}
