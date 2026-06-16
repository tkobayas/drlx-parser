package org.drools.drlx.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class WindowVisitorTest {

    @Test
    void parsesLengthWindow() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals | length[5],
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var pattern = (DrlxRuleAstModel.PatternIR) rule.lhs().get(0);
        assertThat(pattern.entryPoint()).isEqualTo("withdrawals");
        assertThat(pattern.windowType()).isEqualTo("length");
        assertThat(pattern.windowParameter()).isEqualTo("5");
    }

    @Test
    void parsesTimeWindow() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals | time[5s],
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var pattern = (DrlxRuleAstModel.PatternIR) rule.lhs().get(0);
        assertThat(pattern.windowType()).isEqualTo("time");
        assertThat(pattern.windowParameter()).isEqualTo("5s");
    }

    @Test
    void parsesCompoundTimeWindow() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals | time[4d6h5m6s],
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var pattern = (DrlxRuleAstModel.PatternIR) rule.lhs().get(0);
        assertThat(pattern.windowType()).isEqualTo("time");
        assertThat(pattern.windowParameter()).isEqualTo("4d6h5m6s");
    }

    @Test
    void patternWithoutWindowHasNullFields() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals,
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var pattern = (DrlxRuleAstModel.PatternIR) rule.lhs().get(0);
        assertThat(pattern.windowType()).isNull();
        assertThat(pattern.windowParameter()).isNull();
    }

    @Test
    void windowWithConditionBeforeIt() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals[amount > 100] | length[5],
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var pattern = (DrlxRuleAstModel.PatternIR) rule.lhs().get(0);
        assertThat(pattern.conditions()).containsExactly("amount > 100");
        assertThat(pattern.windowType()).isEqualTo("length");
        assertThat(pattern.windowParameter()).isEqualTo("5");
    }

    @Test
    void rejectsUnknownWindowType() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals | count[5],
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .hasMessageContaining("Unknown window type");
    }

    @Test
    void lengthWindowProducesPatternWithSlidingLengthBehavior() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | length[5],
                    do {}
                }
                """;
        org.kie.api.KieBase kieBase = new DrlxRuleBuilder().build(drlx);
        org.kie.api.definition.rule.Rule rule = kieBase.getRule("org.drools.drlx.parser", "R1");
        assertThat(rule).isNotNull();
        org.drools.base.definitions.rule.impl.RuleImpl ruleImpl = (org.drools.base.definitions.rule.impl.RuleImpl) rule;
        org.drools.base.rule.Pattern pattern =
                (org.drools.base.rule.Pattern) ruleImpl.getLhs().getChildren().get(0);
        assertThat(pattern.getBehaviors()).hasSize(1);
        assertThat(pattern.getBehaviors().get(0))
                .isInstanceOf(org.drools.core.rule.SlidingLengthWindow.class);
        org.drools.core.rule.SlidingLengthWindow window =
                (org.drools.core.rule.SlidingLengthWindow) pattern.getBehaviors().get(0);
        assertThat(window.getSize()).isEqualTo(5);
    }

    @Test
    void timeWindowProducesPatternWithSlidingTimeBehavior() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | time[5s],
                    do {}
                }
                """;
        org.kie.api.KieBase kieBase = new DrlxRuleBuilder().build(drlx);
        org.drools.base.definitions.rule.impl.RuleImpl ruleImpl =
                (org.drools.base.definitions.rule.impl.RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");
        org.drools.base.rule.Pattern pattern =
                (org.drools.base.rule.Pattern) ruleImpl.getLhs().getChildren().get(0);
        assertThat(pattern.getBehaviors()).hasSize(1);
        assertThat(pattern.getBehaviors().get(0))
                .isInstanceOf(org.drools.core.rule.SlidingTimeWindow.class);
        org.drools.core.rule.SlidingTimeWindow window =
                (org.drools.core.rule.SlidingTimeWindow) pattern.getBehaviors().get(0);
        assertThat(window.getSize()).isEqualTo(5000L);
    }

    @Test
    void compoundTimeWindowParsesCorrectly() {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Withdrawal;
                import org.drools.drlx.ruleunit.WithdrawalUnit;
                unit WithdrawalUnit;
                rule R1 {
                    var w : /withdrawals | time[4d6h5m6s],
                    do {}
                }
                """;
        org.kie.api.KieBase kieBase = new DrlxRuleBuilder().build(drlx);
        org.drools.base.definitions.rule.impl.RuleImpl ruleImpl =
                (org.drools.base.definitions.rule.impl.RuleImpl) kieBase.getRule("org.drools.drlx.parser", "R1");
        org.drools.base.rule.Pattern pattern =
                (org.drools.base.rule.Pattern) ruleImpl.getLhs().getChildren().get(0);
        org.drools.core.rule.SlidingTimeWindow window =
                (org.drools.core.rule.SlidingTimeWindow) pattern.getBehaviors().get(0);
        long expected = 4L * 86400000 + 6L * 3600000 + 5L * 60000 + 6L * 1000;
        assertThat(window.getSize()).isEqualTo(expected);
    }

    @Test
    void parsesNamedWindowDeclaration() {
        String drlx = """
                package p;
                unit MyUnit;
                window WithdrawalWindow {
                    /withdrawals | time[10s]
                }
                rule R1 {
                    var w : /withdrawals,
                    do {}
                }
                """;
        var unit = parseUnit(drlx);
        assertThat(unit.windowDeclarations()).hasSize(1);
        var windowDecl = unit.windowDeclarations().get(0);
        assertThat(windowDecl.name()).isEqualTo("WithdrawalWindow");
        assertThat(windowDecl.pattern().entryPoint()).isEqualTo("withdrawals");
        assertThat(windowDecl.pattern().windowType()).isEqualTo("time");
        assertThat(windowDecl.pattern().windowParameter()).isEqualTo("10s");
    }

    @Test
    void parsesNamedWindowWithConstraints() {
        String drlx = """
                package p;
                unit MyUnit;
                window GoldWithdrawalWindow {
                    /withdrawals[customer == "GOLD"] | time[5s]
                }
                rule R1 {
                    var w : /withdrawals,
                    do {}
                }
                """;
        var unit = parseUnit(drlx);
        var windowDecl = unit.windowDeclarations().get(0);
        assertThat(windowDecl.name()).isEqualTo("GoldWithdrawalWindow");
        assertThat(windowDecl.pattern().conditions()).containsExactly("customer == \"GOLD\"");
        assertThat(windowDecl.pattern().windowType()).isEqualTo("time");
        assertThat(windowDecl.pattern().windowParameter()).isEqualTo("5s");
    }

    @Test
    void parsesNamedLengthWindowDeclaration() {
        String drlx = """
                package p;
                unit MyUnit;
                window RecentWithdrawals {
                    /withdrawals | length[5]
                }
                rule R1 {
                    var w : /withdrawals,
                    do {}
                }
                """;
        var unit = parseUnit(drlx);
        var windowDecl = unit.windowDeclarations().get(0);
        assertThat(windowDecl.name()).isEqualTo("RecentWithdrawals");
        assertThat(windowDecl.pattern().windowType()).isEqualTo("length");
        assertThat(windowDecl.pattern().windowParameter()).isEqualTo("5");
    }

    private static DrlxRuleAstModel.RuleIR parseRule(String drlx) {
        return parseUnit(drlx).rules().get(0);
    }

    private static DrlxRuleAstModel.CompilationUnitIR parseUnit(String drlx) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(drlx));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxStart().drlxCompilationUnit();
        return new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx);
    }
}
