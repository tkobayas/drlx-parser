/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.drools.drlx.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DRLXParserTest {

    @Test
    public void testParseSimpleExpr() {
        String expr = "name == \"Mark\"";
        ParseTree tree = parseExpressionAsAntlrAST(expr);

        assertThat(tree).isNotNull();
        assertThat(tree).isInstanceOf(DRLXParser.MvelExpressionContext.class);

        DRLXParser.MvelExpressionContext mvelExpressionContext = (DRLXParser.MvelExpressionContext) tree;
        assertThat(mvelExpressionContext.getText()).isEqualTo("name==\"Mark\"");
    }

    @Test
    public void testParseSimpleClass() {
        String expr = """
                public class X {
                }
                """;

        ParseTree tree = parseClassAsAntlrAST(expr);

        assertThat(tree).isNotNull();
        assertThat(tree).isInstanceOf(DRLXParser.CompilationUnitContext.class);

        DRLXParser.CompilationUnitContext compilationUnitContext = (DRLXParser.CompilationUnitContext) tree;
        assertThat(compilationUnitContext).isNotNull();
        assertThat(compilationUnitContext.getText()).isEqualTo("publicclassX{}<EOF>");
    }

    @Test
    public void testParseSimpleRule() {
        String rule = """
                rule R1 {
                   var a : /as,
                   do { System.out.println(a == 3.2B);}
                }
                """;

        ParseTree tree = parseRuleAsAntlrAST(rule);

        assertThat(tree).isNotNull();
        assertThat(tree).isInstanceOf(DRLXParser.RuleCompilationUnitContext.class);

        DRLXParser.RuleCompilationUnitContext ruleCompilationUnitContext = (DRLXParser.RuleCompilationUnitContext) tree;
        assertThat(ruleCompilationUnitContext).isNotNull();
        assertThat(ruleCompilationUnitContext.drlxRule()).hasSize(1);

        DRLXParser.DrlxRuleContext ruleContext = ruleCompilationUnitContext.drlxRule(0);
        assertThat(ruleContext.identifier().getText()).isEqualTo("R1");

        // Verify rule body structure
        DRLXParser.RuleBodyContext ruleBody = ruleContext.ruleBody();
        assertThat(ruleBody.pattern()).hasSize(1);
        assertThat(ruleBody.consequence()).isNotNull();

        // Verify pattern structure
        DRLXParser.PatternContext pattern = ruleBody.pattern(0);
        assertThat(pattern.bindingVariable()).isNotNull();
        assertThat(pattern.bindingVariable().identifier().getText()).isEqualTo("a");
        assertThat(pattern.oopathExpression().getText()).isEqualTo("/as");

        // Verify consequence structure
        DRLXParser.ConsequenceContext consequence = ruleBody.consequence();
        assertThat(consequence.blockStatement()).isNotNull();
        assertThat(consequence.blockStatement().getText()).isEqualTo("{System.out.println(a==3.2B);}"); // 3.2B is a BigDecimal literal in Mvel3
    }

    private static ParseTree parseExpressionAsAntlrAST(final String expression) {
        return parseAntlrAST(expression, DRLXParser::mvelExpression);
    }

    private static ParseTree parseClassAsAntlrAST(final String classExpression) {
        return parseAntlrAST(classExpression, DRLXParser::compilationUnit);
    }

    private static ParseTree parseRuleAsAntlrAST(final String ruleExpression) {
        return parseAntlrAST(ruleExpression, DRLXParser::ruleCompilationUnit);
    }

    private static ParseTree parseAntlrAST(final String input,
                                           java.util.function.Function<DRLXParser, ParseTree> parseFunction) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(input);
            DRLXLexer lexer = new DRLXLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DRLXParser parser = new DRLXParser(tokens);

            // Add error handling
            List<String> errors = new ArrayList<>();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                        int line, int charPositionInLine, String msg, RecognitionException e) {
                    errors.add("Line " + line + ":" + charPositionInLine + " " + msg);
                }
            });

            // Parse using the provided parse function
            ParseTree tree = parseFunction.apply(parser);

            if (!errors.isEmpty()) {
                throw new RuntimeException("Parser errors: " + String.join(", ", errors));
            }

            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse : " + input, e);
        }
    }
}