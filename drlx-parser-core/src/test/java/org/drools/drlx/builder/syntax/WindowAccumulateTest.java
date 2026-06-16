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
class WindowAccumulateTest {

    private static KieBase buildWithStreamMode(String drlx) {
        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption(EventProcessingOption.STREAM);
        return new DrlxRuleBuilder().build(drlx, config);
    }

    @Test
    void inlineTimeWindowAvg() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | time[5s],
                    var avgAmount = avg(w.amount),
                    do { results.add(avgAmount); }
                }
                """;
        KieBase kieBase = buildWithStreamMode(drlx);

        SessionConfiguration sessionConfig = RuleBaseFactory.newKnowledgeSessionConfiguration()
                .as(SessionConfiguration.KEY);
        sessionConfig.setClockType(ClockType.PSEUDO_CLOCK);

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit, sessionConfig)) {
            SessionPseudoClock clock = instance.getClock();

            unit.withdrawals.append(new Withdrawal("A1", 100.0));
            unit.withdrawals.append(new Withdrawal("A2", 200.0));
            unit.withdrawals.append(new Withdrawal("A3", 300.0));

            clock.advanceTime(6, TimeUnit.SECONDS);

            unit.withdrawals.append(new Withdrawal("A4", 400.0));
            instance.fire();

            assertThat(unit.results).containsExactly(400.0);
        }
    }

    @Test
    void inlineLengthWindowAvg() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | length[3],
                    var avgAmount = avg(w.amount),
                    do { results.add(avgAmount); }
                }
                """;
        KieBase kieBase = buildWithStreamMode(drlx);

        WithdrawalUnit unit = new WithdrawalUnit();

        try (DrlxRuleUnitInstance<WithdrawalUnit> instance =
                     DrlxRuleUnitInstance.create(kieBase, unit)) {
            unit.withdrawals.append(new Withdrawal("A1", 100.0));
            unit.withdrawals.append(new Withdrawal("A2", 200.0));
            unit.withdrawals.append(new Withdrawal("A3", 300.0));
            unit.withdrawals.append(new Withdrawal("A4", 400.0));
            unit.withdrawals.append(new Withdrawal("A5", 500.0));
            instance.fire();

            // length[3] keeps only last 3 events: 300, 400, 500 → avg = 400.0
            assertThat(unit.results).containsExactly(400.0);
        }
    }
}
