package org.drools.drlx.builder.syntax;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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
}
