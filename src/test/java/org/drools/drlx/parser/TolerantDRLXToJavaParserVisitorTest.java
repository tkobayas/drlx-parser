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

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;
import org.mvel3.parser.ast.expr.OOPathChunk;
import org.mvel3.parser.ast.expr.RuleConsequence;
import org.mvel3.parser.ast.expr.RuleDeclaration;
import org.mvel3.parser.ast.expr.RulePattern;

import static org.assertj.core.api.Assertions.assertThat;

class TolerantDRLXToJavaParserVisitorTest {

    @Test
    void incompleteClass() {
        String compilationUnitString = """
                public class Foo {
                    public void bar() {
                        System.
                """;

        TolerantDRLXParser parser = new TolerantDRLXParser();
        CompilationUnit compilationUnit = parser.parseCompilationUnit(compilationUnitString);

        // Verify class declaration
        assertThat(compilationUnit.getTypes()).hasSize(1);
        assertThat(compilationUnit.getType(0).getName().asString()).isEqualTo("Foo");
        assertThat(compilationUnit.getType(0)).isInstanceOf(ClassOrInterfaceDeclaration.class);

        // Verify method declaration
        ClassOrInterfaceDeclaration classDecl =
                (ClassOrInterfaceDeclaration) compilationUnit.getType(0);
        assertThat(classDecl.getMethods()).hasSize(1);
        assertThat(classDecl.getMethodsByName("bar")).hasSize(1);

        // Verify method body
        com.github.javaparser.ast.body.MethodDeclaration methodDecl = classDecl.getMethodsByName("bar").get(0);
        assertThat(methodDecl.getBody()).isPresent();
        BlockStmt blockStmt = methodDecl.getBody().get();
        assertThat(blockStmt.getStatements()).hasSize(1);

        // Verify that we can get System identifier in a partial statement
        Statement stmt = blockStmt.getStatements().get(0);
        assertThat(stmt).isInstanceOf(ExpressionStmt.class);

        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
        Expression expr = exprStmt.getExpression();
        assertThat(expr).isInstanceOf(FieldAccessExpr.class);

        FieldAccessExpr fieldAccess = (FieldAccessExpr) expr;
        assertThat(fieldAccess.getScope()).isInstanceOf(NameExpr.class);
        assertThat(((NameExpr) fieldAccess.getScope()).getName().asString()).isEqualTo("System");

        // Setup JavaSymbolSolver for type resolution
        ReflectionTypeSolver typeSolver = new ReflectionTypeSolver(false);
        JavaSymbolSolver solver = new JavaSymbolSolver(typeSolver);

        // Inject symbol resolver into the compilation unit
        solver.inject(compilationUnit);

        // Test type resolution of System
        Expression systemExpr = fieldAccess.getScope();
        ResolvedType systemType = systemExpr.calculateResolvedType();
        System.out.println("System type: " + systemType.describe());

        // Verify the resolved type
        assertThat(systemType.describe()).isEqualTo("java.lang.System");

        // Now we can suggest completions for System.* by getting all public static fields
        // This would be where code completion suggestions would come from
    }

    @Test
    void incompleteRule() {
        String compilationUnitString = """
                class Foo {
                    rule R1 {
                       var a : /as,
                       do { System.
                """;

        TolerantDRLXParser parser = new TolerantDRLXParser();
        CompilationUnit compilationUnit = parser.parseCompilationUnit(compilationUnitString);

        // Verify class declaration
        assertThat(compilationUnit.getTypes()).hasSize(1);
        assertThat(compilationUnit.getType(0).getName().asString()).isEqualTo("Foo");
        assertThat(compilationUnit.getType(0)).isInstanceOf(ClassOrInterfaceDeclaration.class);

        // Verify rule declaration
        ClassOrInterfaceDeclaration classDecl =
                (ClassOrInterfaceDeclaration) compilationUnit.getType(0);
        assertThat(classDecl.getMembers()).hasSize(1);
        assertThat(classDecl.getMember(0)).isInstanceOf(RuleDeclaration.class);

        RuleDeclaration ruleDecl = (RuleDeclaration) classDecl.getMember(0);
        assertThat(ruleDecl.getName().asString()).isEqualTo("R1");

        // Verify rule body - should be tolerant of incomplete parsing
        org.mvel3.parser.ast.expr.RuleBody ruleBody = ruleDecl.getRuleBody();
        assertThat(ruleBody).isNotNull();
        
        // The rule body should contain at least the pattern that was successfully parsed
        // The incomplete consequence might not be parsed fully, but we should get some items
        assertThat(ruleBody.getItems()).isNotEmpty();
        
        // Find the pattern item (should be present since it was complete)
        RulePattern pattern = (RulePattern) ruleBody.getItems().get(0);
        RuleConsequence consequence = (RuleConsequence) ruleBody.getItems().get(1);

        // Verify pattern (should be complete)
        assertThat(pattern).isNotNull();
        assertThat(pattern.getType().asString()).isEqualTo("var");
        assertThat(pattern.getBind().asString()).isEqualTo("a");
        
        // Verify OOPath expression
        org.mvel3.parser.ast.expr.OOPathExpr oopathExpr = pattern.getExpr();
        assertThat(oopathExpr).isNotNull();
        assertThat(oopathExpr.getChunks()).hasSize(1);
        assertThat(oopathExpr.getChunks().get(0)).isInstanceOf(OOPathChunk.class);
        
        OOPathChunk chunk = oopathExpr.getChunks().get(0);
        assertThat(chunk.getField().asString()).isEqualTo("as");
        
        // Verify consequence - should be present with the incomplete System. reference
        assertThat(consequence).isNotNull();
        assertThat(consequence.getStatement()).isNotNull();
        assertThat(consequence.getStatement()).isInstanceOf(BlockStmt.class);
        
        BlockStmt consequenceBlock = (BlockStmt) consequence.getStatement();
        assertThat(consequenceBlock.getStatements()).hasSize(1);
        
        Statement stmt = consequenceBlock.getStatements().get(0);
        assertThat(stmt).isInstanceOf(ExpressionStmt.class);
        
        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
        Expression expr = exprStmt.getExpression();
        assertThat(expr).isInstanceOf(FieldAccessExpr.class);
        
        // Check if we can identify the System reference for code completion
        FieldAccessExpr fieldAccess = (FieldAccessExpr) expr;
        assertThat(fieldAccess.getScope()).isInstanceOf(NameExpr.class);
        assertThat(fieldAccess.getName().asString()).isEqualTo("__COMPLETION_FIELD__");
        
        NameExpr nameExpr = (NameExpr) fieldAccess.getScope();
        assertThat(nameExpr.getName().asString()).isEqualTo("System");
        
        // Test tolerant parsing - even with incomplete input, we should get a valid AST structure
        // This is crucial for code completion scenarios where the user is still typing
        assertThat(ruleDecl).isNotNull();
        assertThat(ruleBody).isNotNull();
        assertThat(pattern).isNotNull();
    }
}