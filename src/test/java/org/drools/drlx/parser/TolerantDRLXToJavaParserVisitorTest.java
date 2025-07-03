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

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TolerantDRLXToJavaParserVisitorTest {

    @Test
    public void testVisitCompilationUnit_partial() {
        String classCompilationUnit = """
                public class Foo {
                    public void bar() {
                        System.
                """;

        // Parse the compilation unit
        ParseTree tree = parseCompilationUnitTolerant(classCompilationUnit);

        // Visit and convert to JavaParser AST
        TolerantDRLXToJavaParserVisitor visitor = new TolerantDRLXToJavaParserVisitor();
        Node result = visitor.visit(tree);

        // Verify the result
        assertThat(result).isInstanceOf(com.github.javaparser.ast.CompilationUnit.class);
        com.github.javaparser.ast.CompilationUnit compilationUnit = (com.github.javaparser.ast.CompilationUnit) result;

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

        // Verify that we can get System identifier in a partial statement
        Statement stmt = blockStmt.getStatements().get(0);
        assertThat(stmt).isInstanceOf(ExpressionStmt.class);
        
        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
        Expression expr = exprStmt.getExpression();
        assertThat(expr).isInstanceOf(FieldAccessExpr.class);
        
        FieldAccessExpr fieldAccess = (FieldAccessExpr) expr;
        assertThat(fieldAccess.getScope()).isInstanceOf(com.github.javaparser.ast.expr.NameExpr.class);
        assertThat(((com.github.javaparser.ast.expr.NameExpr) fieldAccess.getScope()).getName().asString()).isEqualTo("System");
        
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

    private static ParseTree parseCompilationUnitTolerant(final String compilationUnit) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(compilationUnit);
            DRLXLexer lexer = new DRLXLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DRLXParser parser = new DRLXParser(tokens);

            // Parse as a compilation unit
            ParseTree tree = parser.compilationUnit();

            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse compilation unit: " + compilationUnit, e);
        }
    }
}