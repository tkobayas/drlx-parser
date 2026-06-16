package org.drools.drlx.builder.syntax;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxToRuleAstVisitor;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchParseTest {

    private static final String PREAMBLE = """
            package org.drools.drlx.parser;
            import org.drools.drlx.domain.Customer;
            import org.drools.drlx.domain.Product;
            import org.drools.drlx.domain.Rating;
            import org.drools.drlx.domain.Rates;
            import org.drools.drlx.ruleunit.CreditUnit;
            unit CreditUnit;
            """;

    private static final String MY_UNIT_PREAMBLE = """
            package org.drools.drlx.parser;
            import org.drools.drlx.domain.Vehicle;
            import org.drools.drlx.domain.Car;
            import org.drools.drlx.ruleunit.MyUnit;
            unit MyUnit;
            """;

    @Test
    void valueMatch_blockBody_threeRules() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            var p : /products[ rate == Rates.HIGH ],
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            var p : /products[ rate == Rates.MEDIUM ],
                            do { System.out.println("medium"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).name()).isEqualTo("R$0");
        assertThat(rules.get(1).name()).isEqualTo("R$1");
        assertThat(rules.get(2).name()).isEqualTo("R$2");
    }

    @Test
    void valueMatch_correctConditionsAndGuards() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            var p : /products[ rate == Rates.HIGH ],
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            var p : /products[ rate == Rates.MEDIUM ],
                            do { System.out.println("medium"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);

        RuleIR r0 = rules.get(0);
        assertThat(r0.lhs().get(0)).isInstanceOf(PatternIR.class);
        assertThat(r0.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo("c.creditRating == Rating.LOW"));
        assertThat(r0.lhs().get(2)).isInstanceOf(PatternIR.class);

        RuleIR r1 = rules.get(1);
        assertThat(r1.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo("!(c.creditRating == Rating.LOW)"));
        assertThat(r1.lhs().get(2)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo("c.creditRating == Rating.MEDIUM"));

        RuleIR r2 = rules.get(2);
        assertThat(r2.lhs()).hasSize(3);
        assertThat(r2.lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!("));
        assertThat(r2.lhs().get(2)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).startsWith("!("));
    }

    @Test
    void typeMatch_instanceofCondition() {
        String rule = MY_UNIT_PREAMBLE + """
                rule R {
                    var o : /objects,
                    match (o)
                        case #Car {
                            do { System.out.println("car"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo("o instanceof Car"));
    }

    @Test
    void typeMatchWithConstraints_instanceofAndCast() {
        String rule = MY_UNIT_PREAMBLE + """
                rule R {
                    var o : /objects,
                    match (o)
                        case #Car[speed > 80] {
                            do { System.out.println("fast car"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo("o instanceof Car && ((Car)o).speed > 80"));
    }

    @Test
    void typeMatchWithMultipleConstraints() {
        String rule = MY_UNIT_PREAMBLE + """
                rule R {
                    var o : /objects,
                    match (o)
                        case #Car[speed > 80, vin == "ABC"] {
                            do { System.out.println("fast ABC car"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules.get(0).lhs().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).isEqualTo(
                        "o instanceof Car && ((Car)o).speed > 80 && ((Car)o).vin == \"ABC\""));
    }

    @Test
    void noDefault_twoRules() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            do { System.out.println("medium"); }
                        }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
    }

    @Test
    void doStatementBody() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW do { System.out.println("low"); }
                        default do { System.out.println("other"); }
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).rhs().block()).contains("low");
    }

    @Test
    void bareExpressionBody() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW System.out.println("low")
                        default System.out.println("other")
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).rhs().block()).contains("low");
    }

    @Test
    void trailingComma_accepted() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        },
                }
                """;
        List<RuleIR> rules = parseRules(rule);
        assertThat(rules).hasSize(2);
    }

    @Test
    void error_emptyCaseBody() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {}
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        assertThatThrownBy(() -> parseRules(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("at least one action");
    }

    @Test
    void error_trailingDoConflict() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        },
                    do { System.out.println("trailing"); }
                }
                """;
        assertThatThrownBy(() -> parseRules(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("trailing");
    }

    @Test
    void error_itemsAfterMatch() {
        String rule = PREAMBLE + """
                rule R {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        },
                    var p : /products,
                }
                """;
        assertThatThrownBy(() -> parseRules(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not supported");
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
