package org.drools.drlx.builder.syntax;

import org.drools.core.impl.RuleBaseFactory;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Withdrawal;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.WithdrawalUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.conf.EventProcessingOption;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class WindowTest {

    private static final String WINDOW_RULE_TEMPLATE = """
            package org.drools.drlx.parser;
            import org.drools.drlx.domain.Withdrawal;
            import org.drools.drlx.ruleunit.WithdrawalUnit;
            unit WithdrawalUnit;
            rule R1 {
                var w : /withdrawals | %s,
                do {}
            }
            """;

    private KieBase buildWithStreamMode(String drlx) {
        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption(EventProcessingOption.STREAM);
        return new DrlxRuleBuilder().build(drlx, config);
    }

    @Test
    void lengthWindowRuleFiresAtSessionLevel() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("length[5]"));

        WithdrawalUnit unit = new WithdrawalUnit();
        unit.withdrawals.add(new Withdrawal("A1", 100.0));
        unit.withdrawals.add(new Withdrawal("A2", 200.0));

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire()).isEqualTo(2);
        }
    }

    @Test
    void lengthWindowEvictsOldestWhenFull() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("length[5]"));

        WithdrawalUnit unit = new WithdrawalUnit();
        for (int i = 1; i <= 7; i++) {
            unit.withdrawals.add(new Withdrawal("A" + i, i * 100.0));
        }

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            int fired = instance.fire();
            assertThat(fired).isEqualTo(5);
        }
    }

    @Test
    void timeWindowRuleFiresAtSessionLevel() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("time[5s]"));

        WithdrawalUnit unit = new WithdrawalUnit();
        unit.withdrawals.add(new Withdrawal("A1", 100.0));
        unit.withdrawals.add(new Withdrawal("A2", 200.0));
        unit.withdrawals.add(new Withdrawal("A3", 300.0));

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            assertThat(instance.fire()).isEqualTo(3);
        }
    }
}
