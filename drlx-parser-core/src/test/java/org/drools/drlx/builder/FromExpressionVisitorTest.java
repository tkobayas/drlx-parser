package org.drools.drlx.builder;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.FromExpressionIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FromExpressionVisitorTest {

    @Test
    void constructorFromExpression() {
        RuleIR rule = parseRule("""
                rule R1 {
                    var i : /new int[] {1, 2, 3},
                    fn { System.out.println(i); }
                }
                """);
        assertThat(rule.lhs()).hasSize(1);
        assertThat(rule.lhs().get(0)).isInstanceOf(FromExpressionIR.class);
        FromExpressionIR fe = (FromExpressionIR) rule.lhs().get(0);
        assertThat(fe.typeName()).isEqualTo("var");
        assertThat(fe.bindName()).isEqualTo("i");
        assertThat(fe.fromExpression()).isEqualTo("new int[] {1, 2, 3}");
    }

    @Test
    void methodCallFromExpression() {
        RuleIR rule = parseRule("""
                rule R1 {
                    var list : /java.util.Collections.emptyList(),
                    fn { System.out.println(list); }
                }
                """);
        assertThat(rule.lhs().get(0)).isInstanceOf(FromExpressionIR.class);
        FromExpressionIR fe = (FromExpressionIR) rule.lhs().get(0);
        assertThat(fe.fromExpression()).isEqualTo("java.util.Collections.emptyList()");
    }

    @Test
    void methodCallWithEarlierBinding() {
        RuleIR rule = parseRule("""
                rule R1 {
                    var p : /persons,
                    var addrs : /p.getAddresses(),
                    fn { System.out.println(addrs); }
                }
                """);
        assertThat(rule.lhs()).hasSize(2);
        assertThat(rule.lhs().get(0)).isInstanceOf(PatternIR.class);
        assertThat(rule.lhs().get(1)).isInstanceOf(FromExpressionIR.class);
        FromExpressionIR fe = (FromExpressionIR) rule.lhs().get(1);
        assertThat(fe.fromExpression()).isEqualTo("p.getAddresses()");
        assertThat(fe.referencedBindings()).contains("p");
    }

    @Test
    void explicitTypeFromExpression() {
        RuleIR rule = parseRule("""
                rule R1 {
                    Integer i : /new int[] {1, 2, 3},
                    fn { System.out.println(i); }
                }
                """);
        FromExpressionIR fe = (FromExpressionIR) rule.lhs().get(0);
        assertThat(fe.typeName()).isEqualTo("Integer");
        assertThat(fe.bindName()).isEqualTo("i");
    }

    @Test
    void dataStorePatternStillParsesAsPatternIR() {
        RuleIR rule = parseRule("""
                rule R1 {
                    var p : /persons[age > 18],
                    fn { System.out.println(p); }
                }
                """);
        assertThat(rule.lhs().get(0)).isInstanceOf(PatternIR.class);
    }

    private static RuleIR parseRule(String ruleBody) {
        String drlx = """
                package org.drools.drlx.parser;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                """ + ruleBody;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(drlx));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxStart().drlxCompilationUnit();
        return new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx).rules().get(0);
    }
}
