package org.drools.drlx.builder.syntax;

import java.util.concurrent.TimeUnit;

import org.drools.core.ClockType;
import org.drools.core.SessionConfiguration;
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
import org.kie.api.time.SessionPseudoClock;

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

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.withdrawals.append(new Withdrawal("A1", 100.0));
            unit.withdrawals.append(new Withdrawal("A2", 200.0));
            assertThat(instance.fire()).isEqualTo(2);
        }
    }

    @Test
    void lengthWindowEvictsOldestWhenFull() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("length[5]"));

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            for (int i = 1; i <= 7; i++) {
                unit.withdrawals.append(new Withdrawal("A" + i, i * 100.0));
            }
            int fired = instance.fire();
            assertThat(fired).isEqualTo(5);
        }
    }

    @Test
    void timeWindowRuleFiresAtSessionLevel() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("time[5s]"));

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.withdrawals.append(new Withdrawal("A1", 100.0));
            unit.withdrawals.append(new Withdrawal("A2", 200.0));
            unit.withdrawals.append(new Withdrawal("A3", 300.0));
            assertThat(instance.fire()).isEqualTo(3);
        }
    }

    @Test
    void timeWindowExpiresOldEventsWithPseudoClock() {
        KieBase kieBase = buildWithStreamMode(WINDOW_RULE_TEMPLATE.formatted("time[5s]"));

        SessionConfiguration sessionConfig = RuleBaseFactory.newKnowledgeSessionConfiguration()
                .as(SessionConfiguration.KEY);
        sessionConfig.setClockType(ClockType.PSEUDO_CLOCK);

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit, sessionConfig)) {
            SessionPseudoClock clock = instance.getClock();

            unit.withdrawals.append(new Withdrawal("A1", 100.0));
            unit.withdrawals.append(new Withdrawal("A2", 200.0));

            clock.advanceTime(6, TimeUnit.SECONDS);

            unit.withdrawals.append(new Withdrawal("A3", 300.0));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void constraintBeforeWindowFiltersEntries() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals[customer == "GOLD"] | length[3],
                    do {}
                }
                """;
        KieBase kieBase = buildWithStreamMode(drlx);

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.withdrawals.append(new Withdrawal("A1", 100.0, "GOLD"));
            unit.withdrawals.append(new Withdrawal("A2", 200.0, "STANDARD"));
            unit.withdrawals.append(new Withdrawal("A3", 300.0, "GOLD"));
            unit.withdrawals.append(new Withdrawal("A4", 400.0, "STANDARD"));
            unit.withdrawals.append(new Withdrawal("A5", 500.0, "GOLD"));
            assertThat(instance.fire()).isEqualTo(3);
        }
    }

    @Test
    void constraintAfterWindowFiltersContents() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | length[3],
                    test w.customer == "GOLD",
                    do {}
                }
                """;
        KieBase kieBase = buildWithStreamMode(drlx);

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.withdrawals.append(new Withdrawal("A1", 100.0, "GOLD"));
            unit.withdrawals.append(new Withdrawal("A2", 200.0, "STANDARD"));
            unit.withdrawals.append(new Withdrawal("A3", 300.0, "GOLD"));
            unit.withdrawals.append(new Withdrawal("A4", 400.0, "STANDARD"));
            unit.withdrawals.append(new Withdrawal("A5", 500.0, "GOLD"));
            assertThat(instance.fire()).isEqualTo(2);
        }
    }
}
