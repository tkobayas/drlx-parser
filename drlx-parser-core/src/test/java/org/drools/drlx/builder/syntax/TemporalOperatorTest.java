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
class TemporalOperatorTest {

    @Test
    void afterOperatorFiresWhenSecondEventComesLater() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this after a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            SessionPseudoClock clock = instance.getClock();
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            clock.advanceTime(1, TimeUnit.SECONDS);
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void afterOperatorDoesNotFireWhenSimultaneous() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this after a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            assertThat(instance.fire()).isEqualTo(0);
        }
    }

    @Test
    void afterWithRangeFiresInRange() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this after[2s, 5s] a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            SessionPseudoClock clock = instance.getClock();
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            clock.advanceTime(3, TimeUnit.SECONDS);
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void afterWithRangeDoesNotFireOutOfRange() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this after[2s, 5s] a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            SessionPseudoClock clock = instance.getClock();
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            clock.advanceTime(10, TimeUnit.SECONDS);
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            assertThat(instance.fire()).isEqualTo(0);
        }
    }

    @Test
    void beforeOperatorFires() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this before a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            SessionPseudoClock clock = instance.getClock();
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            clock.advanceTime(1, TimeUnit.SECONDS);
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    @Test
    void notAfterFiresWhenSimultaneous() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var a : /withdrawals[customer == "A"],
                    var b : /withdrawals[this not after a, customer == "B"],
                    do {}
                }
                """;
        try (var instance = createStreamInstance(drlx)) {
            instance.unit().withdrawals.append(new Withdrawal("X", 100.0, "A"));
            instance.unit().withdrawals.append(new Withdrawal("Y", 200.0, "B"));
            assertThat(instance.fire()).isEqualTo(1);
        }
    }

    private record StreamInstance<U extends org.drools.ruleunits.api.RuleUnitData>(
            DrlxRuleUnitInstance<U> delegate, U unit) implements AutoCloseable {
        SessionPseudoClock getClock() { return delegate.getClock(); }
        int fire() { return delegate.fire(); }
        @Override public void close() { delegate.close(); }
    }

    private static StreamInstance<WithdrawalUnit> createStreamInstance(String drlx) {
        KieBaseConfiguration kbConfig = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbConfig.setOption(EventProcessingOption.STREAM);
        KieBase kieBase = new DrlxRuleBuilder().build(drlx, kbConfig);

        SessionConfiguration sessionConfig = RuleBaseFactory.newKnowledgeSessionConfiguration()
                .as(SessionConfiguration.KEY);
        sessionConfig.setClockType(ClockType.PSEUDO_CLOCK);

        WithdrawalUnit unit = new WithdrawalUnit();
        DrlxRuleUnitInstance<WithdrawalUnit> instance =
                DrlxRuleUnitInstance.create(kieBase, unit, sessionConfig);
        return new StreamInstance<>(instance, unit);
    }
}
