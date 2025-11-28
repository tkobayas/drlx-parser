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

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;
import org.mvel3.parser.ast.expr.DrlxExpression;
import org.mvel3.parser.ast.expr.InlineCastExpr;
import org.mvel3.parser.ast.expr.OOPathChunk;
import org.mvel3.parser.ast.expr.OOPathExpr;
import org.mvel3.parser.ast.expr.RuleBody;
import org.mvel3.parser.ast.expr.RuleDeclaration;
import org.mvel3.parser.ast.expr.RuleItem;
import org.mvel3.parser.ast.expr.RulePattern;
import org.mvel3.parser.ast.expr.RuleConsequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.drlx.parser.DRLXHelper.parseCompilationUnitAsAntlrAST;
import static org.drools.drlx.parser.DRLXHelper.parseCompilationUnitAsJavaParserAST;
import static org.drools.drlx.parser.DRLXHelper.parseDrlxCompilationUnitAsAntlrAST;
import static org.drools.drlx.parser.DRLXHelper.parseDrlxCompilationUnitAsJavaParserAST;

/**
 * Parse DRLX expressions and rules using the DRLXParser and convert the resulting antlr AST tree into JavaParser AST using the DRLXToJavaParserVisitor.
 * Verify the resulting JavaParser AST structure.
 */
class DRLXToJavaParserVisitorTest {

    @Test
    void testVisitCompilationUnit_class() {
        String classCompilationUnit = """
                public class Foo {
                    public void bar() {
                        System.out.println("Hello");
                    }
                }
                """;

        com.github.javaparser.ast.CompilationUnit compilationUnit = parseCompilationUnitAsJavaParserAST(classCompilationUnit);

        // Verify class declaration
        assertThat(compilationUnit.getTypes()).hasSize(1);
        assertThat(compilationUnit.getType(0).getName().asString()).isEqualTo("Foo");
        assertThat(compilationUnit.getType(0)).isInstanceOf(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class);

        // Verify method declaration
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl =
                (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) compilationUnit.getType(0);
        assertThat(classDecl.getMethods()).hasSize(1);
        assertThat(classDecl.getMethodsByName("bar")).hasSize(1);

        // Verify method body
        com.github.javaparser.ast.body.MethodDeclaration methodDecl = classDecl.getMethodsByName("bar").get(0);
        assertThat(methodDecl.getBody()).isPresent();
        BlockStmt blockStmt = methodDecl.getBody().get();
        assertThat(blockStmt.getStatements()).hasSize(1);

        // Verify System.out.println("Hello")
        Statement stmt = blockStmt.getStatements().get(0);
        assertThat(stmt).isInstanceOf(ExpressionStmt.class);

        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
        assertThat(exprStmt.getExpression()).isInstanceOf(MethodCallExpr.class);

        MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
        assertThat(methodCall.getName().asString()).isEqualTo("println");
        assertThat(methodCall.getScope()).isPresent();

        // Verify System.out
        assertThat(methodCall.getScope().get()).isInstanceOf(FieldAccessExpr.class);
        FieldAccessExpr fieldAccess = (FieldAccessExpr) methodCall.getScope().get();
        assertThat(fieldAccess.getName().asString()).isEqualTo("out");

        // Verify arguments
        assertThat(methodCall.getArguments()).hasSize(1);
        assertThat(methodCall.getArguments().get(0)).hasToString("\"Hello\"");
        
        // Setup JavaSymbolSolver for type resolution
        ReflectionTypeSolver typeSolver = new ReflectionTypeSolver(false);
        JavaSymbolSolver solver = new JavaSymbolSolver(typeSolver);
        
        // Inject symbol resolver into the compilation unit
        solver.inject(compilationUnit);
        
        // Test type resolution
        Expression scope = methodCall.getScope().get();
        ResolvedType resolvedType = scope.calculateResolvedType();

        // Verify the resolved type
        assertThat(resolvedType.describe()).isEqualTo("java.io.PrintStream");
    }

    @Test
    void testVisitCompilationUnit_rule() {
        String ruleCompilationUnit = """
                class Foo {
                    rule R1 {
                       var a : /as,
                       do { System.out.println(a == 3.2B);}
                    }
                }
                """;

        com.github.javaparser.ast.CompilationUnit compilationUnit = parseCompilationUnitAsJavaParserAST(ruleCompilationUnit);

        // Verify class declaration
        assertThat(compilationUnit.getTypes()).hasSize(1);
        assertThat(compilationUnit.getType(0).getName().asString()).isEqualTo("Foo");
        assertThat(compilationUnit.getType(0)).isInstanceOf(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class);

        // Verify rule declaration
        com.github.javaparser.ast.body.ClassOrInterfaceDeclaration classDecl =
                (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) compilationUnit.getType(0);
        assertThat(classDecl.getMembers()).hasSize(1);
        assertThat(classDecl.getMember(0)).isInstanceOf(org.mvel3.parser.ast.expr.RuleDeclaration.class);

        org.mvel3.parser.ast.expr.RuleDeclaration ruleDecl = (org.mvel3.parser.ast.expr.RuleDeclaration) classDecl.getMember(0);
        assertThat(ruleDecl.getName().asString()).isEqualTo("R1");

        // Verify rule body
        org.mvel3.parser.ast.expr.RuleBody ruleBody = ruleDecl.getRuleBody();
        assertThat(ruleBody.getItems()).hasSize(2); // 1 pattern + 1 consequence

        // Verify pattern (first item)
        org.mvel3.parser.ast.expr.RuleItem firstItem = ruleBody.getItems().get(0);
        assertThat(firstItem).isInstanceOf(org.mvel3.parser.ast.expr.RulePattern.class);
        
        org.mvel3.parser.ast.expr.RulePattern pattern = (org.mvel3.parser.ast.expr.RulePattern) firstItem;
        assertThat(pattern.getType().asString()).isEqualTo("var");
        assertThat(pattern.getBind().asString()).isEqualTo("a");
        
        // Verify OOPath expression
        org.mvel3.parser.ast.expr.OOPathExpr oopathExpr = pattern.getExpr();
        assertThat(oopathExpr.getChunks()).hasSize(1);
        assertThat(oopathExpr.getChunks().get(0)).isInstanceOf(org.mvel3.parser.ast.expr.OOPathChunk.class);

        // Verify consequence (second item)
        org.mvel3.parser.ast.expr.RuleItem secondItem = ruleBody.getItems().get(1);
        assertThat(secondItem).isInstanceOf(org.mvel3.parser.ast.expr.RuleConsequence.class);
        
        org.mvel3.parser.ast.expr.RuleConsequence consequence = (org.mvel3.parser.ast.expr.RuleConsequence) secondItem;
        assertThat(consequence.getStatement()).isInstanceOf(com.github.javaparser.ast.stmt.BlockStmt.class);
        
        // Verify consequence block content
        com.github.javaparser.ast.stmt.BlockStmt consequenceBlock = (com.github.javaparser.ast.stmt.BlockStmt) consequence.getStatement();
        assertThat(consequenceBlock.getStatements()).hasSize(1);
        assertThat(consequenceBlock.getStatements().get(0)).isInstanceOf(com.github.javaparser.ast.stmt.ExpressionStmt.class);
        
        // Verify the System.out.println call
        com.github.javaparser.ast.stmt.ExpressionStmt exprStmt = (com.github.javaparser.ast.stmt.ExpressionStmt) consequenceBlock.getStatements().get(0);
        assertThat(exprStmt.getExpression()).isInstanceOf(com.github.javaparser.ast.expr.MethodCallExpr.class);
        
        com.github.javaparser.ast.expr.MethodCallExpr methodCall = (com.github.javaparser.ast.expr.MethodCallExpr) exprStmt.getExpression();
        assertThat(methodCall.getName().asString()).isEqualTo("println");
        assertThat(methodCall.getArguments()).hasSize(1);
    }

    @Test
    void testInlineCast() {
        String classCompilationUnit = """
                import java.util.List;
                import java.util.ArrayList;
                
                public class Foo {
                    public void bar(List list) {
                        list#ArrayList#.removeRange(0, 10);
                    }
                }
                """;

        com.github.javaparser.ast.CompilationUnit compilationUnit = parseCompilationUnitAsJavaParserAST(classCompilationUnit);

        MethodCallExpr methodCall = compilationUnit.getType(0)
                .asClassOrInterfaceDeclaration().getMethodsByName("bar").get(0).getBody().get().getStatements().get(0)
                .asExpressionStmt().getExpression()
                .asMethodCallExpr();

        assertThat(methodCall.getName().asString()).isEqualTo("removeRange");
        assertThat(methodCall.getScope()).isPresent();

        assertThat(methodCall.getScope().get()).isInstanceOf(InlineCastExpr.class);
        InlineCastExpr inlineCastExpr = (InlineCastExpr) methodCall.getScope().get();
        assertThat(inlineCastExpr.getExpression().asNameExpr().getName().asString()).isEqualTo("list");
        assertThat(inlineCastExpr.getType().asString()).isEqualTo("ArrayList");

        // Setup JavaSymbolSolver for type resolution
        ReflectionTypeSolver typeSolver = new ReflectionTypeSolver(false);
        JavaSymbolSolver solver = new JavaSymbolSolver(typeSolver);

        // Inject symbol resolver into the compilation unit
        solver.inject(compilationUnit);

        // Test type resolution
        ResolvedType resolvedType = inlineCastExpr.calculateResolvedType();

        // Verify the resolved type
        assertThat(resolvedType.describe()).isEqualTo("java.util.ArrayList");
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

        com.github.javaparser.ast.CompilationUnit compilationUnit = parseDrlxCompilationUnitAsJavaParserAST(rule);

        assertThat(compilationUnit.getPackageDeclaration())
                .map(PackageDeclaration::getNameAsString)
                .hasValue("org.drools.drlx.parser");

        assertThat(compilationUnit.getImports())
                .map(ImportDeclaration::getNameAsString)
                .containsExactly("org.drools.drlx.parser.domain.Person");

        assertThat(compilationUnit.getTypes()).hasSize(1);

        RuleDeclaration ruleDeclaration = (RuleDeclaration) compilationUnit.getTypes().get(0);
        assertThat(ruleDeclaration.getNameAsString()).isEqualTo("CheckAge");

        RuleBody ruleBody = ruleDeclaration.getRuleBody();
        NodeList<RuleItem> items = ruleBody.getItems();
        assertThat(items).hasSize(2);

        RulePattern pattern = (RulePattern) items.get(0);
        assertThat(pattern.getType().asString()).isEqualTo("Person");
        assertThat(pattern.getBind().asString()).isEqualTo("p");

        OOPathExpr ooPathExpr = pattern.getExpr();
        assertThat(ooPathExpr.getChunks()).hasSize(1);
        OOPathChunk firstChunk = ooPathExpr.getChunks().get(0);
        assertThat(firstChunk.getField().asString()).isEqualTo("people");
        assertThat(firstChunk.getConditions()).hasSize(1);

        DrlxExpression condition = firstChunk.getConditions().get(0);
        assertThat(condition.getBind()).isNull();
        assertThat(condition.getExpr()).isInstanceOf(BinaryExpr.class);
        BinaryExpr binaryExpr = (BinaryExpr) condition.getExpr();
        assertThat(binaryExpr.getLeft()).isInstanceOf(NameExpr.class);
        assertThat(((NameExpr) binaryExpr.getLeft()).getNameAsString()).isEqualTo("age");
        assertThat(binaryExpr.getRight().toString()).isEqualTo("18");

        RuleConsequence consequence = (RuleConsequence) items.get(1);
        Statement stmt = consequence.getStatement();
        assertThat(stmt).isInstanceOf(BlockStmt.class);
        BlockStmt blockStmt = (BlockStmt) stmt;
        assertThat(blockStmt.getStatements()).hasSize(1);
        assertThat(blockStmt.getStatement(0).toString()).contains("System.out.println(p)");

    }
}
