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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DRLXToJavaParserVisitorTest {

    @Test
    public void testVisitBlockStatement() {
        String blockStatement = "{ System.out.println(\"Hello\"); }";
        
        // Parse the block statement
        ParseTree tree = parseBlockStatement(blockStatement);
        
        // Visit and convert to JavaParser AST
        DRLXToJavaParserVisitor visitor = new DRLXToJavaParserVisitor();
        Node result = visitor.visit(tree);
        
        // Verify the result
        assertThat(result).isInstanceOf(BlockStmt.class);
        BlockStmt blockStmt = (BlockStmt) result;
        assertThat(blockStmt.getStatements()).hasSize(1);
        
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
        assertThat(methodCall.getArguments().get(0).toString()).isEqualTo("\"Hello\"");
    }

    private static ParseTree parseBlockStatement(final String blockStatement) {
        try {
            // Create ANTLR4 lexer and parser
            CharStream charStream = CharStreams.fromString(blockStatement);
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

            // Parse as a block
            ParseTree tree = parser.block();

            if (!errors.isEmpty()) {
                throw new RuntimeException("Parser errors: " + String.join(", ", errors));
            }

            return tree;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse block statement: " + blockStatement, e);
        }
    }
}