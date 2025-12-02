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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.drlx.util.DrlxHelper.parseCompilationUnitAsAntlrAST;
import static org.drools.drlx.util.DrlxHelper.parseDrlxCompilationUnitAsAntlrAST;
import static org.drools.drlx.util.DrlxHelper.parseExpressionAsAntlrAST;

/**
 * Parse DRLX expressions and rules using the DrlxParser and verify the resulting antlr AST structure.
 */
class DrlxParserTest {

    @Test
    void testParseSimpleExpr() {
        String expr = "name == \"Mark\"";
        DrlxParser.ExpressionContext expressionContext = parseExpressionAsAntlrAST(expr);

        assertThat(expressionContext.getText()).isEqualTo("name==\"Mark\"");
    }

    @Test
    void testParseSimpleClass() {
        String expr = """
                public class X {
                }
                """;

        DrlxParser.CompilationUnitContext compilationUnitContext = parseCompilationUnitAsAntlrAST(expr);

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

        DrlxParser.CompilationUnitContext compilationUnitContext = parseCompilationUnitAsAntlrAST(rule);

        assertThat(compilationUnitContext).isNotNull();
        assertThat(compilationUnitContext.typeDeclaration()).hasSize(1);

        DrlxParser.ClassDeclarationContext classDeclarationContext = compilationUnitContext.typeDeclaration(0).classDeclaration();
        DrlxParser.RuleDeclarationContext ruleDeclarationContext = classDeclarationContext.classBody().classBodyDeclaration(0).memberDeclaration().ruleDeclaration();

        // Verify rule body structure
        DrlxParser.RuleBodyContext ruleBody = ruleDeclarationContext.ruleBody();
        assertThat(ruleBody.ruleItem()).hasSize(2); // 1 pattern + 1 consequence

        // Verify pattern structure (first rule item)
        DrlxParser.RuleItemContext firstItem = ruleBody.ruleItem(0);
        assertThat(firstItem.rulePattern()).isNotNull();
        DrlxParser.RulePatternContext pattern = firstItem.rulePattern();
        assertThat(pattern.identifier(0).getText()).isEqualTo("var"); // type
        assertThat(pattern.identifier(1).getText()).isEqualTo("a"); // bind
        assertThat(pattern.oopathExpression().getText()).isEqualTo("/as");

        // Verify consequence structure (second rule item)
        DrlxParser.RuleItemContext secondItem = ruleBody.ruleItem(1);
        assertThat(secondItem.ruleConsequence()).isNotNull();
        DrlxParser.RuleConsequenceContext consequence = secondItem.ruleConsequence();
        assertThat(consequence.statement()).isNotNull();
        assertThat(consequence.statement().getText()).isEqualTo("{System.out.println(a==3.2B);}"); // 3.2B is a BigDecimal literal in Mvel3
    }

    @Test
    void testParseBasicRule() {
        // very basic rule, not inside a class
        String rule = """
                package org.drools.drlx.parser;
                
                import org.drools.drlx.domain.Person;
                
                unit MyUnit;
                
                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        DrlxParser.DrlxCompilationUnitContext drlxCompilationUnitContext = parseDrlxCompilationUnitAsAntlrAST(rule);

        assertThat(drlxCompilationUnitContext.ruleDeclaration()).hasSize(1);

        DrlxParser.RuleDeclarationContext ruleDeclarationContext = drlxCompilationUnitContext.ruleDeclaration(0);

        // Verify rule body structure
        DrlxParser.RuleBodyContext ruleBody = ruleDeclarationContext.ruleBody();
        assertThat(ruleBody.ruleItem()).hasSize(2); // 1 pattern + 1 consequence

        // Verify pattern structure (first rule item)
        DrlxParser.RuleItemContext firstItem = ruleBody.ruleItem(0);
        assertThat(firstItem.rulePattern()).isNotNull();
        DrlxParser.RulePatternContext pattern = firstItem.rulePattern();
        assertThat(pattern.identifier(0).getText()).isEqualTo("Person"); // type
        assertThat(pattern.identifier(1).getText()).isEqualTo("p"); // bind
        assertThat(pattern.oopathExpression().getText()).isEqualTo("/persons[age>18]");

        // Verify consequence structure (second rule item)
        DrlxParser.RuleItemContext secondItem = ruleBody.ruleItem(1);
        assertThat(secondItem.ruleConsequence()).isNotNull();
        DrlxParser.RuleConsequenceContext consequence = secondItem.ruleConsequence();
        assertThat(consequence.statement()).isNotNull();
        assertThat(consequence.statement().getText()).isEqualTo("{System.out.println(p);}");
    }
}
