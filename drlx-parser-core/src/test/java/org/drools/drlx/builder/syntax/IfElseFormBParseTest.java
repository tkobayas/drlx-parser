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

    @Test
    void twoRules_correctLhsAndRhs() {
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

        RuleIR r0 = rules.get(0);
        assertThat(r0.lhs()).hasSize(3);
        assertThat(r0.lhs().get(0)).isInstanceOf(PatternIR.class);
        assertThat(r0.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("Rating.LOW"));
        assertThat(r0.lhs().get(2)).isInstanceOf(PatternIR.class);
        assertThat(r0.rhs()).isNotNull();
        assertThat(r0.rhs().block()).contains("System.out.println");

        RuleIR r1 = rules.get(1);
        assertThat(r1.lhs()).hasSize(3);
        assertThat(r1.lhs().get(0)).isInstanceOf(PatternIR.class);
        assertThat(r1.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!(").contains("Rating.LOW"));
        assertThat(r1.lhs().get(2)).isInstanceOf(PatternIR.class);
        assertThat(r1.rhs()).isNotNull();
    }

    @Test
    void elseIfChain_cumulativeGuards() {
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
                        do { System.out.println("low"); }
                    } else if (c.creditRating == Rating.MEDIUM) {
                        var p : /products[ rate == Rates.MEDIUM ],
                        do { System.out.println("medium"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("other"); }
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).name()).isEqualTo("R$0");
        assertThat(rules.get(1).name()).isEqualTo("R$1");
        assertThat(rules.get(2).name()).isEqualTo("R$2");

        RuleIR r1 = rules.get(1);
        assertThat(r1.lhs()).hasSize(4);
        assertThat(r1.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!(").contains("Rating.LOW"));
        assertThat(r1.lhs().get(2)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("Rating.MEDIUM").doesNotStartWith("!("));

        RuleIR r2 = rules.get(2);
        assertThat(r2.lhs()).hasSize(4);
        assertThat(r2.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!(").contains("Rating.LOW"));
        assertThat(r2.lhs().get(2)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!(").contains("Rating.MEDIUM"));
    }

    @Test
    void singleIf_noElse_oneRule() {
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
                        do { System.out.println(c); }
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("R$0");
        assertThat(rules.get(0).lhs()).hasSize(3);
    }

    @Test
    void bareExpressionConsequence_parsesCorrectly() {
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
                        System.out.println(c + " " + p)
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        System.out.println(c + " " + p)
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).rhs().block()).contains("System.out.println");
    }

    @Test
    void multipleConsequencesInBranch_combined() {
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
                        do { System.out.println("first"); },
                        do { System.out.println("second"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("else"); }
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules.get(0).rhs().block()).contains("first").contains("second");
    }

    @Test
    void mixedBareAndDoInSameBranch_combined() {
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
                        System.out.println("bare"),
                        do { System.out.println("explicit"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("else"); }
                    }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules.get(0).rhs().block()).contains("bare").contains("explicit");
    }

    @Test
    void formAUnchanged_singleRuleWithOr() {
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
                        var p : /products[ rate == Rates.HIGH ]
                    } else {
                        var p : /products[ rate == Rates.LOW ]
                    },
                    do { System.out.println(c + " " + p); }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).name()).isEqualTo("R");
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
