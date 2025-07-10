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

import java.util.HashMap;
import java.util.Map;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.mvel3.parser.ast.expr.RuleDeclaration;
import org.mvel3.parser.ast.expr.RuleBody;
import org.mvel3.parser.ast.expr.RuleItem;
import org.mvel3.parser.ast.expr.RulePattern;
import org.mvel3.parser.ast.expr.RuleConsequence;
import org.mvel3.parser.ast.expr.OOPathExpr;
import org.mvel3.parser.ast.expr.OOPathChunk;
import org.mvel3.parser.ast.expr.DrlxExpression;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import java.util.Collections;

/**
 * Visitor that converts DRLX ANTLR4 parse tree to JavaParser AST nodes.
 * This implementation is tolerant of partial/incomplete syntax for code completion scenarios.
 * It can return various types of nodes (Expression, Statement, etc.).
 */
public class TolerantDRLXToJavaParserVisitor extends DRLXParserBaseVisitor<Node> {

    private static final String COMPLETION_PLACEHOLDER = "__COMPLETION__";
    public static final String COMPLETION_FIELD = "__COMPLETION_FIELD__";

    // Associate antlr tokenId with a JavaParser node for identifier, so it can be used for code completion.
    private final Map<Integer, Node> tokenIdJPNodeMap = new HashMap<>();

    public Map<Integer, Node> getTokenIdJPNodeMap() {
        return tokenIdJPNodeMap;
    }

    @Override
    public Node visitCompilationUnit(DRLXParser.CompilationUnitContext ctx) {
        CompilationUnit cu = new CompilationUnit();

        // Handle type declarations
        if (ctx.typeDeclaration() != null) {
            for (DRLXParser.TypeDeclarationContext typeDecl : ctx.typeDeclaration()) {
                Node node = visit(typeDecl);
                if (node instanceof TypeDeclaration) {
                    cu.addType((TypeDeclaration<?>) node);
                }
            }
        }

        return cu;
    }

    @Override
    public Node visitTypeDeclaration(DRLXParser.TypeDeclarationContext ctx) {
        // Handle class declarations for now
        if (ctx.classDeclaration() != null) {
            return visit(ctx.classDeclaration());
        }
        // TODO: Handle other type declarations (interface, enum, etc.)
        throw new UnsupportedOperationException("Type declaration not yet implemented: " + ctx.getText());
    }

    @Override
    public Node visitClassDeclaration(DRLXParser.ClassDeclarationContext ctx) {
        String className = ctx.identifier().getText();
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName(className);
        classDecl.setInterface(false);

        // Handle modifiers
        if (ctx.getParent() instanceof DRLXParser.TypeDeclarationContext) {
            DRLXParser.TypeDeclarationContext parent = (DRLXParser.TypeDeclarationContext) ctx.getParent();
            if (parent.classOrInterfaceModifier() != null) {
                for (DRLXParser.ClassOrInterfaceModifierContext modifier : parent.classOrInterfaceModifier()) {
                    if (modifier.PUBLIC() != null) {
                        classDecl.addModifier(Modifier.Keyword.PUBLIC);
                    }
                    // TODO: Handle other modifiers
                }
            }
        }

        // Handle class body
        if (ctx.classBody() != null) {
            visitClassBody(ctx.classBody(), classDecl);
        }

        return classDecl;
    }

    private void visitClassBody(DRLXParser.ClassBodyContext ctx, ClassOrInterfaceDeclaration classDecl) {
        if (ctx.classBodyDeclaration() != null) {
            for (DRLXParser.ClassBodyDeclarationContext bodyDecl : ctx.classBodyDeclaration()) {
                if (bodyDecl.memberDeclaration() != null) {
                    DRLXParser.MemberDeclarationContext memberDecl = bodyDecl.memberDeclaration();
                    if (memberDecl.methodDeclaration() != null) {
                        Node method = visitMethodDeclaration(memberDecl.methodDeclaration());
                        if (method instanceof MethodDeclaration) {
                            classDecl.addMember((MethodDeclaration) method);
                        }
                    } else if (memberDecl.ruleDeclaration() != null) {
                        Node rule = visitRuleDeclaration(memberDecl.ruleDeclaration());
                        if (rule instanceof RuleDeclaration) {
                            classDecl.addMember((RuleDeclaration) rule);
                        }
                    }
                    // TODO: Handle other member types (fields, nested classes, etc.)
                }
            }
        }
    }

    @Override
    public Node visitMethodDeclaration(DRLXParser.MethodDeclarationContext ctx) {
        // Get method name
        String methodName = ctx.identifier().getText();

        // Create method declaration
        MethodDeclaration methodDecl = new MethodDeclaration();
        methodDecl.setName(methodName);

        // Handle return type
        if (ctx.typeTypeOrVoid() != null) {
            if (ctx.typeTypeOrVoid().VOID() != null) {
                methodDecl.setType(new VoidType());
            } else if (ctx.typeTypeOrVoid().typeType() != null) {
                // TODO: Handle other types
                throw new UnsupportedOperationException("Non-void return types not yet implemented");
            }
        }

        // Handle modifiers (from parent context)
        if (ctx.getParent() instanceof DRLXParser.MemberDeclarationContext) {
            DRLXParser.MemberDeclarationContext memberCtx = (DRLXParser.MemberDeclarationContext) ctx.getParent();
            if (memberCtx.getParent() instanceof DRLXParser.ClassBodyDeclarationContext) {
                DRLXParser.ClassBodyDeclarationContext bodyDeclCtx = (DRLXParser.ClassBodyDeclarationContext) memberCtx.getParent();
                if (bodyDeclCtx.modifier() != null) {
                    for (DRLXParser.ModifierContext modifier : bodyDeclCtx.modifier()) {
                        if (modifier.classOrInterfaceModifier() != null) {
                            if (modifier.classOrInterfaceModifier().PUBLIC() != null) {
                                methodDecl.addModifier(Modifier.Keyword.PUBLIC);
                            }
                            // TODO: Handle other modifiers
                        }
                    }
                }
            }
        }

        // Handle method body
        if (ctx.methodBody() != null) {
            if (ctx.methodBody().block() != null) {
                BlockStmt body = (BlockStmt) visit(ctx.methodBody().block());
                methodDecl.setBody(body);
            }
        }

        return methodDecl;
    }

    @Override
    public Node visitBlock(DRLXParser.BlockContext ctx) {
        BlockStmt blockStmt = new BlockStmt();
        NodeList<Statement> statements = new NodeList<>();

        if (ctx.blockStatement() != null) {
            // Check if we have multiple incomplete statements that should be merged
            Expression chainedExpression = null;
            
            for (DRLXParser.BlockStatementContext blockStatementCtx : ctx.blockStatement()) {
                Node node = visit(blockStatementCtx);
                if (node instanceof Statement) {
                    Statement stmt = (Statement) node;
                    
                    // Check if this is an incomplete expression statement
                    if (stmt instanceof ExpressionStmt) {
                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                        Expression expr = exprStmt.getExpression();
                        
                        if (expr instanceof FieldAccessExpr) {
                            FieldAccessExpr fieldAccess = (FieldAccessExpr) expr;
                            if (COMPLETION_FIELD.equals(fieldAccess.getName().asString())) {
                                if (chainedExpression == null) {
                                    // First incomplete expression
                                    chainedExpression = fieldAccess;
                                } else {
                                    // Merge with previous expression
                                    chainedExpression = mergeFieldAccessExpressions(chainedExpression, fieldAccess);
                                }
                                continue; // Don't add this statement yet
                            }
                        }
                    }
                    
                    // Add any previous chained expression if we have one
                    if (chainedExpression != null) {
                        ExpressionStmt chainedStmt = new ExpressionStmt(chainedExpression);
                        chainedExpression.setParentNode(chainedStmt);
                        statements.add(chainedStmt);
                        chainedExpression = null;
                    }
                    
                    statements.add(stmt);
                }
            }
            
            // Add any remaining chained expression
            if (chainedExpression != null) {
                ExpressionStmt chainedStmt = new ExpressionStmt(chainedExpression);
                chainedExpression.setParentNode(chainedStmt);
                statements.add(chainedStmt);
            }
        }

        blockStmt.setStatements(statements);
        
        // Set parent relationships for all statements
        for (Statement stmt : statements) {
            stmt.setParentNode(blockStmt);
        }
        
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

        // handle error nodes
        if (areChildrenErrorNodes(ctx)) {
            // Build the complete field access chain for incomplete expressions
            Expression expr = buildIncompleteExpression(ctx);
            if (expr != null) {
                ExpressionStmt stmt = new ExpressionStmt(expr);
                expr.setParentNode(stmt);
                return stmt;
            }
        }

        if (ctx.children == null) {
            // invalid node. Ignore for now
            return null;
        }
        throw new IllegalArgumentException("Unknown blockStatement type: " + ctx.getText());
    }

    private boolean areChildrenErrorNodes(DRLXParser.BlockStatementContext ctx) {
        if (ctx.children == null || ctx.children.isEmpty()) {
            return false; // No children to check
        }
        for (ParseTree child : ctx.children) {
            if (child instanceof ErrorNode) {
                return true; // At least one child is an error node
            }
        }
        return false;
    }

    private Expression buildIncompleteExpression(DRLXParser.BlockStatementContext ctx) {
        if (ctx.children == null || ctx.children.isEmpty()) {
            return null;
        }

        // Build the expression by parsing the tokens
        Expression expr = null;
        boolean expectingIdentifier = true;
        
        for (ParseTree child : ctx.children) {
            String text = child.getText();
            
            if (expectingIdentifier && !text.equals(".")) {
                // This should be an identifier
                if (expr == null) {
                    // First identifier - create NameExpr
                    expr = new NameExpr(text);
                    if (child instanceof TerminalNodeImpl) {
                        tokenIdJPNodeMap.put(((TerminalNodeImpl)child).getSymbol().getTokenIndex(), expr);
                    }
                } else {
                    // Subsequent identifier - create FieldAccessExpr
                    FieldAccessExpr fieldAccess = new FieldAccessExpr(expr, text);
                    expr.setParentNode(fieldAccess);
                    expr = fieldAccess;
                    if (child instanceof TerminalNodeImpl) {
                        tokenIdJPNodeMap.put(((TerminalNodeImpl)child).getSymbol().getTokenIndex(), expr);
                    }
                }
                expectingIdentifier = false;
            } else if (text.equals(".")) {
                expectingIdentifier = true;
            }
        }
        
        // If we ended with a dot, add the completion marker
        if (expectingIdentifier && expr != null) {
            FieldAccessExpr completionField = new FieldAccessExpr(expr, COMPLETION_FIELD);
            expr.setParentNode(completionField);
            expr = completionField;
        }
        
        return expr;
    }

    private Expression mergeFieldAccessExpressions(Expression chainedExpression, FieldAccessExpr newFieldAccess) {
        // Extract the scope from the new field access (this should be the field name we want to chain)
        Expression scope = newFieldAccess.getScope();
        
        if (scope instanceof NameExpr) {
            // Replace the __COMPLETION_FIELD__ with the actual field name from the scope
            NameExpr fieldName = (NameExpr) scope;
            
            // Remove the completion marker from the chained expression and add the real field
            if (chainedExpression instanceof FieldAccessExpr) {
                FieldAccessExpr chainedFieldAccess = (FieldAccessExpr) chainedExpression;
                if (COMPLETION_FIELD.equals(chainedFieldAccess.getName().asString())) {
                    // Replace the completion marker with the actual field name
                    FieldAccessExpr newChained = new FieldAccessExpr(chainedFieldAccess.getScope(), fieldName.getName().asString());
                    chainedFieldAccess.getScope().setParentNode(newChained);
                    
                    // Update tokenIdJPNodeMap: find the old orphaned node and replace with the new merged node
                    updateTokenIdJPNodeMapForMerge(fieldName, newChained);
                    
                    // Add the completion marker back at the end
                    FieldAccessExpr finalExpr = new FieldAccessExpr(newChained, COMPLETION_FIELD);
                    newChained.setParentNode(finalExpr);
                    return finalExpr;
                }
            }
        }
        
        return chainedExpression; // Fallback - return original if we can't merge
    }
    
    private void updateTokenIdJPNodeMapForMerge(NameExpr orphanedNode, FieldAccessExpr newMergedNode) {
        // Find the token ID that was pointing to the orphaned node and update it to point to the merged node
        for (Map.Entry<Integer, Node> entry : tokenIdJPNodeMap.entrySet()) {
            if (entry.getValue() == orphanedNode) {
                entry.setValue(newMergedNode);
                break;
            }
        }
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
            String fieldName = ctx.identifier().getText();
            // Field access: expression.identifier
            return new FieldAccessExpr(expr, fieldName);
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
            String identifierText = ctx.identifier().getText();
            // Handle completion placeholder
            if (COMPLETION_PLACEHOLDER.equals(identifierText)) {
                return new NameExpr("__COMPLETION_IDENTIFIER__");
            }
            return new NameExpr(identifierText);
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

    @Override
    public Node visitRuleDeclaration(DRLXParser.RuleDeclarationContext ctx) {
        // Create rule declaration
        SimpleName name = new SimpleName(ctx.identifier().getText());
        RuleBody body = (RuleBody) visit(ctx.ruleBody());
        NodeList<AnnotationExpr> annotations = new NodeList<>();
        
        RuleDeclaration ruleDecl = new RuleDeclaration(null, annotations, name, body);
        
        // Set parent relationships
        name.setParentNode(ruleDecl);
        body.setParentNode(ruleDecl);
        
        return ruleDecl;
    }

    @Override
    public Node visitRuleBody(DRLXParser.RuleBodyContext ctx) {
        NodeList<RuleItem> items = new NodeList<>();
        
        if (ctx.ruleItem() != null) {
            for (DRLXParser.RuleItemContext itemCtx : ctx.ruleItem()) {
                RuleItem item = (RuleItem) visit(itemCtx);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        
        RuleBody ruleBody = new RuleBody(null, items);
        
        // Set parent relationships
        for (RuleItem item : items) {
            item.setParentNode(ruleBody);
        }
        
        return ruleBody;
    }

    @Override
    public Node visitRuleItem(DRLXParser.RuleItemContext ctx) {
        if (ctx.rulePattern() != null) {
            return visit(ctx.rulePattern());
        } else if (ctx.ruleConsequence() != null) {
            return visit(ctx.ruleConsequence());
        }
        // For tolerant parsing, return null for unknown items
        return null;
    }

    @Override
    public Node visitRulePattern(DRLXParser.RulePatternContext ctx) {
        try {
            // Get type and bind identifiers
            SimpleName type = new SimpleName(ctx.identifier(0).getText());
            SimpleName bind = new SimpleName(ctx.identifier(1).getText());
            OOPathExpr expr = (OOPathExpr) visit(ctx.oopathExpression());
            
            RulePattern pattern = new RulePattern(null, type, bind, expr);
            
            // Set parent relationships
            type.setParentNode(pattern);
            bind.setParentNode(pattern);
            expr.setParentNode(pattern);
            
            return pattern;
        } catch (Exception e) {
            // For tolerant parsing, return null if we can't parse the pattern
            return null;
        }
    }

    @Override
    public Node visitRuleConsequence(DRLXParser.RuleConsequenceContext ctx) {
        try {
            Statement statement = (Statement) visit(ctx.statement());
            RuleConsequence consequence = new RuleConsequence(null, statement);
            
            // Set parent relationship
            statement.setParentNode(consequence);
            
            return consequence;
        } catch (Exception e) {
            // For tolerant parsing, return null if we can't parse the consequence
            return null;
        }
    }

    @Override
    public Node visitOopathExpression(DRLXParser.OopathExpressionContext ctx) {
        NodeList<OOPathChunk> chunks = new NodeList<>();
        
        if (ctx.oopathChunk() != null) {
            for (DRLXParser.OopathChunkContext chunkCtx : ctx.oopathChunk()) {
                OOPathChunk chunk = (OOPathChunk) visit(chunkCtx);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            }
        }
        
        OOPathExpr oopathExpr = new OOPathExpr(null, chunks);
        
        // Set parent relationships
        for (OOPathChunk chunk : chunks) {
            chunk.setParentNode(oopathExpr);
        }
        
        return oopathExpr;
    }

    @Override
    public Node visitOopathChunk(DRLXParser.OopathChunkContext ctx) {
        try {
            SimpleName field = new SimpleName(ctx.identifier().getText());
            SimpleName inlineCast = null; // No inline cast for simple case
            
            OOPathChunk chunk = new OOPathChunk(null, field, inlineCast, Collections.emptyList());
            
            // Set parent relationship
            field.setParentNode(chunk);
            
            return chunk;
        } catch (Exception e) {
            // For tolerant parsing, return null if we can't parse the chunk
            return null;
        }
    }
}