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

    private static DrlxRuleAstModel.RuleIR parseRule(String drlx) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(drlx));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxStart().drlxCompilationUnit();
        var unit = new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx);
        return unit.rules().get(0);
    }
}
