package org.drools.drlx.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.TemporalConditionIR;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalVisitorTest {

    @Test
    void parsesThisAfterBinding() {
        var rule = parseRule("""
                package p;
                unit MyUnit;
                rule R1 {
                    var a : /as,
                    var b : /bs[this after a],
                    do {}
                }
                """);
        var pattern = (PatternIR) rule.lhs().get(1);
        assertThat(pattern.temporalConditions()).hasSize(1);
        TemporalConditionIR tc = pattern.temporalConditions().get(0);
        assertThat(tc.operator()).isEqualTo("after");
        assertThat(tc.negated()).isFalse();
        assertThat(tc.parameters()).isEmpty();
        assertThat(tc.rightBinding()).isEqualTo("a");
        assertThat(pattern.conditions()).isEmpty();
    }

    @Test
    void parsesAfterWithParams() {
        var rule = parseRule("""
                package p;
                unit MyUnit;
                rule R1 {
                    var a : /as,
                    var b : /bs[this after[3m, 4m] a],
                    do {}
                }
                """);
        var pattern = (PatternIR) rule.lhs().get(1);
        TemporalConditionIR tc = pattern.temporalConditions().get(0);
        assertThat(tc.operator()).isEqualTo("after");
        assertThat(tc.parameters()).containsExactly("3m", "4m");
        assertThat(tc.rightBinding()).isEqualTo("a");
    }

    @Test
    void parsesNegatedBefore() {
        var rule = parseRule("""
                package p;
                unit MyUnit;
                rule R1 {
                    var a : /as,
                    var b : /bs[this not before a],
                    do {}
                }
                """);
        var pattern = (PatternIR) rule.lhs().get(1);
        TemporalConditionIR tc = pattern.temporalConditions().get(0);
        assertThat(tc.operator()).isEqualTo("before");
        assertThat(tc.negated()).isTrue();
        assertThat(tc.rightBinding()).isEqualTo("a");
    }

    @Test
    void mixedTemporalAndRegularConditions() {
        var rule = parseRule("""
                package p;
                unit MyUnit;
                rule R1 {
                    var a : /as,
                    var b : /bs[this after a, amount > 100],
                    do {}
                }
                """);
        var pattern = (PatternIR) rule.lhs().get(1);
        assertThat(pattern.temporalConditions()).hasSize(1);
        assertThat(pattern.temporalConditions().get(0).operator()).isEqualTo("after");
        assertThat(pattern.conditions()).containsExactly("amount > 100");
    }

    @Test
    void patternWithoutTemporalHasEmptyList() {
        var rule = parseRule("""
                package p;
                unit MyUnit;
                rule R1 {
                    var w : /withdrawals[amount > 100],
                    do {}
                }
                """);
        var pattern = (PatternIR) rule.lhs().get(0);
        assertThat(pattern.temporalConditions()).isEmpty();
        assertThat(pattern.conditions()).containsExactly("amount > 100");
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
