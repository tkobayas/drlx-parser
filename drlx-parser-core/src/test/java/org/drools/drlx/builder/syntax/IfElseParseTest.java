package org.drools.drlx.builder.syntax;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxToRuleAstVisitor;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IfElseParseTest {

    @Test
    void parsesBinaryIfElse() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 60) {
                        var s : /seniors[ age == p.age ]
                    } else {
                        var j : /juniors[ age == p.age ]
                    },
                    do { System.out.println(p); }
                }
                """;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(rule));
        DrlxParser parser = new DrlxParser(new CommonTokenStream(lexer));
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
        DrlxParser.RuleDeclarationContext rule0 = ctx.ruleDeclaration(0);
        assertThat(rule0.ruleBody().ruleItem()).anyMatch(
                ri -> ri.conditionalBranch() != null);
    }

    @Test
    void parsesElseIfChain() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 60) { var s : /seniors[ age == p.age ] }
                    else if (p.age > 30) { var a : /adults[ age == p.age ] }
                    else { var j : /juniors[ age == p.age ] },
                    do { System.out.println(p); }
                }
                """;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(rule));
        DrlxParser parser = new DrlxParser(new CommonTokenStream(lexer));
        parser.drlxCompilationUnit();
        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }

    @Test
    void parsesIfWithoutElse() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 60) { var s : /seniors[ age == p.age ] },
                    do { System.out.println(p); }
                }
                """;
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(rule));
        DrlxParser parser = new DrlxParser(new CommonTokenStream(lexer));
        parser.drlxCompilationUnit();
        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }

    @Test
    void desugarsBinaryIfElseToOrAnd() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 30) { var s : /seniors[ age == p.age ] }
                    else { var j : /juniors[ age == p.age ] },
                    do { System.out.println(p); }
                }
                """;
        RuleIR ruleIr = parseSingleRule(rule);
        List<LhsItemIR> lhs = ruleIr.lhs();
        assertThat(lhs).hasSize(2);                                    // [Pattern(p), OR(...)]
        assertThat(lhs.get(1)).isInstanceOf(GroupElementIR.class);
        GroupElementIR or = (GroupElementIR) lhs.get(1);
        assertThat(or.kind()).isEqualTo(GroupElementIR.Kind.OR);
        assertThat(or.children()).hasSize(2);

        // Branch 1: AND of [EvalIR("p.age > 30"), Pattern(s)]
        GroupElementIR b1 = (GroupElementIR) or.children().get(0);
        assertThat(b1.kind()).isEqualTo(GroupElementIR.Kind.AND);
        assertThat(b1.children().get(0)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("p.age").contains(">").contains("30"));
        assertThat(b1.children().get(1)).isInstanceOf(PatternIR.class);

        // Branch 2 (final else): AND of [EvalIR("!(p.age > 30)"), Pattern(j)]
        GroupElementIR b2 = (GroupElementIR) or.children().get(1);
        assertThat(b2.kind()).isEqualTo(GroupElementIR.Kind.AND);
        assertThat(b2.children().get(0)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("p.age").contains("30").startsWith("!("));
        assertThat(b2.children().get(1)).isInstanceOf(PatternIR.class);
    }

    @Test
    void desugarsElseIfChainWithCumulativeGuards() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 60) { var s : /seniors[ age == p.age ] }
                    else if (p.age > 30) { var a : /adults[ age == p.age ] }
                    else { var j : /juniors[ age == p.age ] },
                    do { System.out.println(p); }
                }
                """;
        RuleIR ruleIr = parseSingleRule(rule);
        GroupElementIR or = (GroupElementIR) ruleIr.lhs().get(1);
        assertThat(or.children()).hasSize(3);

        // Branch 2: [EvalIR("!(p.age > 60)"), EvalIR("p.age > 30"), Pattern(a)]
        GroupElementIR b2 = (GroupElementIR) or.children().get(1);
        assertThat(b2.children().get(0)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("60").startsWith("!("));
        assertThat(b2.children().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("30").doesNotStartWith("!("));
        assertThat(b2.children().get(2)).isInstanceOf(PatternIR.class);

        // Branch 3 (final else): [EvalIR("!(p.age > 60)"), EvalIR("!(p.age > 30)"), Pattern(j)]
        GroupElementIR b3 = (GroupElementIR) or.children().get(2);
        assertThat(b3.children().get(0)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("60").startsWith("!("));
        assertThat(b3.children().get(1)).isInstanceOfSatisfying(EvalIR.class,
                e -> assertThat(e.expression()).contains("30").startsWith("!("));
        assertThat(b3.children().get(2)).isInstanceOf(PatternIR.class);
    }

    @Test
    void emptyBranchBodyIsRejected() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    var p : /persons,
                    if (p.age > 30) { } else { var j : /juniors[ age == p.age ] },
                    do { System.out.println(p); }
                }
                """;
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> parseSingleRule(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty branch body");
    }

    private static RuleIR parseSingleRule(String source) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(source));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxCompilationUnit();
        DrlxToRuleAstVisitor visitor = new DrlxToRuleAstVisitor(tokens);
        CompilationUnitIR unit = visitor.visitDrlxCompilationUnit(ctx);
        return unit.rules().get(0);
    }
}
