package org.drools.drlx.builder.syntax;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxToRuleAstVisitor;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfElseFormBParseTest {

    @Test
    void parsesFormB_twoRulesGenerated() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println(c + " " + p); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println(c + " " + p); }
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).name()).isEqualTo("R$0");
        assertThat(rules.get(1).name()).isEqualTo("R$1");
    }

    private static List<RuleIR> parseRules(String source) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
        DrlxToRuleAstVisitor visitor = new DrlxToRuleAstVisitor(tokens);
        CompilationUnitIR unit = visitor.visitDrlxCompilationUnit(ctx);
        return unit.rules();
    }
}
