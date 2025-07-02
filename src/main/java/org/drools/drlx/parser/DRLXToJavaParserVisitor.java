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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

/**
 * Visitor that converts DRLX ANTLR4 parse tree to JavaParser AST nodes.
 * This implementation can return various types of nodes (Expression, Statement, etc.).
 */
public class DRLXToJavaParserVisitor extends DRLXParserBaseVisitor<Node> {

    @Override
    public Node visitBlock(DRLXParser.BlockContext ctx) {
        BlockStmt blockStmt = new BlockStmt();
        NodeList<Statement> statements = new NodeList<>();
        
        if (ctx.blockStatement() != null) {
            for (DRLXParser.BlockStatementContext blockStatementCtx : ctx.blockStatement()) {
                Node node = visit(blockStatementCtx);
                if (node instanceof Statement) {
                    statements.add((Statement) node);
                }
            }
        }
        
        blockStmt.setStatements(statements);
        return blockStmt;
    }

    @Override
    public Node visitBlockStatement(DRLXParser.BlockStatementContext ctx) {
        if (ctx.statement() != null) {
            return visit(ctx.statement());
        } else if (ctx.localVariableDeclaration() != null) {
            // TODO: Handle local variable declarations
            throw new UnsupportedOperationException("Local variable declarations not yet implemented");
        } else if (ctx.localTypeDeclaration() != null) {
            // TODO: Handle local type declarations
            throw new UnsupportedOperationException("Local type declarations not yet implemented");
        }
        throw new IllegalArgumentException("Unknown blockStatement type: " + ctx.getText());
    }

    @Override
    public Node visitStatement(DRLXParser.StatementContext ctx) {
        if (ctx.blockLabel != null) {
            // block
            return visit(ctx.blockLabel);
        } else if (ctx.statementExpression != null) {
            // expression ';'
            Expression expr = (Expression) visit(ctx.statementExpression);
            return new ExpressionStmt(expr);
        }
        // TODO: Handle other statement types
        throw new UnsupportedOperationException("Statement type not yet implemented: " + ctx.getText());
    }

    @Override
    public Node visitMemberReferenceExpression(DRLXParser.MemberReferenceExpressionContext ctx) {
        // expression.identifier or expression.methodCall()
        Expression expr = (Expression) visit(ctx.expression());
        
        if (ctx.identifier() != null) {
            // Field access: expression.identifier
            return new FieldAccessExpr(expr, ctx.identifier().getText());
        } else if (ctx.methodCall() != null) {
            // Method call: expression.methodCall()
            return visitMethodCallWithScope(ctx.methodCall(), expr);
        }
        // TODO: Handle other member reference types
        throw new UnsupportedOperationException("Member reference type not yet implemented: " + ctx.getText());
    }

    @Override
    public Node visitMethodCallExpression(DRLXParser.MethodCallExpressionContext ctx) {
        // Handle method call without scope
        return visitMethodCallWithScope(ctx.methodCall(), null);
    }

    private MethodCallExpr visitMethodCallWithScope(DRLXParser.MethodCallContext ctx, Expression scope) {
        String methodName;
        if (ctx.identifier() != null) {
            methodName = ctx.identifier().getText();
        } else if (ctx.THIS() != null) {
            methodName = "this";
        } else if (ctx.SUPER() != null) {
            methodName = "super";
        } else {
            throw new IllegalArgumentException("Unknown method call type: " + ctx.getText());
        }
        
        MethodCallExpr methodCall = new MethodCallExpr(scope, methodName);
        
        // Handle arguments
        if (ctx.arguments() != null && ctx.arguments().expressionList() != null) {
            NodeList<Expression> arguments = new NodeList<>();
            for (DRLXParser.ExpressionContext argCtx : ctx.arguments().expressionList().expression()) {
                arguments.add((Expression) visit(argCtx));
            }
            methodCall.setArguments(arguments);
        }
        
        return methodCall;
    }

    @Override
    public Node visitPrimaryExpression(DRLXParser.PrimaryExpressionContext ctx) {
        return visit(ctx.primary());
    }

    @Override
    public Node visitPrimary(DRLXParser.PrimaryContext ctx) {
        if (ctx.literal() != null) {
            return visit(ctx.literal());
        } else if (ctx.identifier() != null) {
            return new NameExpr(ctx.identifier().getText());
        }
        // TODO: Handle other primary types
        throw new UnsupportedOperationException("Primary type not yet implemented: " + ctx.getText());
    }

    @Override
    public Node visitLiteral(DRLXParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            // Remove quotes
            String value = text.substring(1, text.length() - 1);
            return new StringLiteralExpr(value);
        }
        // TODO: Handle other literal types
        throw new UnsupportedOperationException("Literal type not yet implemented: " + ctx.getText());
    }
}