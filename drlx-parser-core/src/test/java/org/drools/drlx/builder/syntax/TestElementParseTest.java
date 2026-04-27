package org.drools.drlx.builder.syntax;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxToRuleAstVisitor;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestElementParseTest {

    @Test
    void parsesTestElement() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    test p.age > 30,
                    do { System.out.println(p); }
                }
                """;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(rule));
        DrlxParser parser = new DrlxParser(new CommonTokenStream(lexer));
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
        DrlxParser.RuleDeclarationContext rule0 = ctx.ruleDeclaration(0);
        assertThat(rule0.ruleBody().ruleItem()).anyMatch(
                ri -> ri.testElement() != null);
    }

    @Test
    void testElementProducesEvalIr() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    test p.age > 30,
                    do { System.out.println(p); }
                }
                """;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(rule));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();

        DrlxToRuleAstVisitor visitor = new DrlxToRuleAstVisitor(tokens);
        var unit = visitor.visitDrlxCompilationUnit(ctx);
        var rule0 = unit.rules().get(0);
        List<LhsItemIR> lhs = rule0.lhs();
        assertThat(lhs).hasSize(2);
        assertThat(lhs.get(1)).isInstanceOf(EvalIR.class);
        EvalIR e = (EvalIR) lhs.get(1);
        // Expression text is preserved (whitespace handling per ANTLR getText())
        assertThat(e.expression()).contains("p.age");
        assertThat(e.referencedBindings()).contains("p");
    }
}
