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

/**
 * Parse DRLX expressions and rules using the DRLXParser and verify the resulting antlr AST structure.
 */
class DRLXParserTest {

    @Test
    void testParseSimpleExpr() {
        String expr = "name == \"Mark\"";
        ParseTree tree = parseExpressionAsAntlrAST(expr);

        assertThat(tree)
                .isNotNull()
                .isInstanceOf(DRLXParser.MvelExpressionContext.class);

        DRLXParser.MvelExpressionContext mvelExpressionContext = (DRLXParser.MvelExpressionContext) tree;
        assertThat(mvelExpressionContext.getText()).isEqualTo("name==\"Mark\"");
    }

    @Test
    void testParseSimpleClass() {
        String expr = """
                public class X {
                }
                """;

        ParseTree tree = parseClassAsAntlrAST(expr);

        assertThat(tree)
                .isNotNull()
                .isInstanceOf(DRLXParser.CompilationUnitContext.class);

        DRLXParser.CompilationUnitContext compilationUnitContext = (DRLXParser.CompilationUnitContext) tree;
        assertThat(compilationUnitContext).isNotNull();
        assertThat(compilationUnitContext.getText()).isEqualTo("publicclassX{}<EOF>");
    }

    @Test
    void testParseSimpleRuleInClass() {
        String rule = """
                class Foo {
                    rule R1 {
                       var a : /as,
                       do { System.out.println(a == 3.2B);}
                    }
                }
                """;

        ParseTree tree = parseRuleAsAntlrAST(rule);

        assertThat(tree)
                .isNotNull()
                .isInstanceOf(DRLXParser.CompilationUnitContext.class);

        DRLXParser.CompilationUnitContext compilationUnitContext = (DRLXParser.CompilationUnitContext) tree;
        assertThat(compilationUnitContext).isNotNull();
        assertThat(compilationUnitContext.typeDeclaration()).hasSize(1);

        DRLXParser.ClassDeclarationContext classDeclarationContext = compilationUnitContext.typeDeclaration(0).classDeclaration();
        DRLXParser.RuleDeclarationContext ruleDeclarationContext = classDeclarationContext.classBody().classBodyDeclaration(0).memberDeclaration().ruleDeclaration();

        // Verify rule body structure
        DRLXParser.RuleBodyContext ruleBody = ruleDeclarationContext.ruleBody();
        assertThat(ruleBody.ruleItem()).hasSize(2); // 1 pattern + 1 consequence

        // Verify pattern structure (first rule item)
        DRLXParser.RuleItemContext firstItem = ruleBody.ruleItem(0);
        assertThat(firstItem.rulePattern()).isNotNull();
        DRLXParser.RulePatternContext pattern = firstItem.rulePattern();
        assertThat(pattern.identifier(0).getText()).isEqualTo("var"); // type
        assertThat(pattern.identifier(1).getText()).isEqualTo("a"); // bind
        assertThat(pattern.oopathExpression().getText()).isEqualTo("/as");

        // Verify consequence structure (second rule item)
        DRLXParser.RuleItemContext secondItem = ruleBody.ruleItem(1);
        assertThat(secondItem.ruleConsequence()).isNotNull();
        DRLXParser.RuleConsequenceContext consequence = secondItem.ruleConsequence();
        assertThat(consequence.statement()).isNotNull();
        assertThat(consequence.statement().getText()).isEqualTo("{System.out.println(a==3.2B);}"); // 3.2B is a BigDecimal literal in Mvel3
    }

    private static ParseTree parseExpressionAsAntlrAST(final String expression) {
        return parseAntlrAST(expression, DRLXParser::mvelExpression);
    }

    private static ParseTree parseClassAsAntlrAST(final String classExpression) {
        return parseAntlrAST(classExpression, DRLXParser::compilationUnit);
    }

    private static ParseTree parseRuleAsAntlrAST(final String ruleExpression) {
        return parseAntlrAST(ruleExpression, DRLXParser::compilationUnit);
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

    @Test
    void testParseBasicRule() {
        // very basic rule, not inside a class
        String rule = """
                package org.drools.drlx.parser;
                
                import org.drools.drlx.parser.domain.Person;
                
                unit MyUnit;
                
                rule CheckAge {
                    Person p : /people[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        ParseTree tree = parseRuleAsAntlrAST(rule);

        assertThat(tree)
                .isNotNull()
                .isInstanceOf(DRLXParser.CompilationUnitContext.class);

        DRLXParser.CompilationUnitContext compilationUnitContext = (DRLXParser.CompilationUnitContext) tree;
        assertThat(compilationUnitContext).isNotNull();
        assertThat(compilationUnitContext.ruleDeclaration()).hasSize(1);

        DRLXParser.RuleDeclarationContext ruleDeclarationContext = compilationUnitContext.ruleDeclaration(0);

        // Verify rule body structure
        DRLXParser.RuleBodyContext ruleBody = ruleDeclarationContext.ruleBody();
        assertThat(ruleBody.ruleItem()).hasSize(2); // 1 pattern + 1 consequence

        // Verify pattern structure (first rule item)
        DRLXParser.RuleItemContext firstItem = ruleBody.ruleItem(0);
        assertThat(firstItem.rulePattern()).isNotNull();
        DRLXParser.RulePatternContext pattern = firstItem.rulePattern();
        assertThat(pattern.identifier(0).getText()).isEqualTo("Person"); // type
        assertThat(pattern.identifier(1).getText()).isEqualTo("p"); // bind
        assertThat(pattern.oopathExpression().getText()).isEqualTo("/people[age>18]");

        // Verify consequence structure (second rule item)
        DRLXParser.RuleItemContext secondItem = ruleBody.ruleItem(1);
        assertThat(secondItem.ruleConsequence()).isNotNull();
        DRLXParser.RuleConsequenceContext consequence = secondItem.ruleConsequence();
        assertThat(consequence.statement()).isNotNull();
        assertThat(consequence.statement().getText()).isEqualTo("{System.out.println(p);}");
    }
}
