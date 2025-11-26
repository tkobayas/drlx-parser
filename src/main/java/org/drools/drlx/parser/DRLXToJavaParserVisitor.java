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

import com.github.javaparser.JavaToken;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VarType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.WildcardType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.mvel3.parser.ast.expr.BigDecimalLiteralExpr;
import org.mvel3.parser.ast.expr.BigIntegerLiteralExpr;
import org.mvel3.parser.ast.expr.InlineCastExpr;
import org.mvel3.parser.ast.expr.ModifyStatement;
import org.mvel3.parser.ast.expr.RuleDeclaration;
import org.mvel3.parser.ast.expr.RuleBody;
import org.mvel3.parser.ast.expr.RuleItem;
import org.mvel3.parser.ast.expr.RulePattern;
import org.mvel3.parser.ast.expr.RuleConsequence;
import org.mvel3.parser.ast.expr.OOPathExpr;
import org.mvel3.parser.ast.expr.OOPathChunk;
import org.mvel3.parser.ast.expr.WithStatement;
import org.mvel3.parser.ast.expr.DrlxExpression;

import java.util.List;

import static org.mvel3.parser.util.AstUtils.getBinaryExprOperator;
import java.util.concurrent.TimeUnit;
import org.mvel3.parser.ast.expr.DrlNameExpr;
import org.mvel3.parser.ast.expr.ListCreationLiteralExpression;
import org.mvel3.parser.ast.expr.ListCreationLiteralExpressionElement;
import org.mvel3.parser.ast.expr.MapCreationLiteralExpression;
import org.mvel3.parser.ast.expr.MapCreationLiteralExpressionKeyValuePair;
import org.mvel3.parser.ast.expr.NullSafeFieldAccessExpr;
import org.mvel3.parser.ast.expr.NullSafeMethodCallExpr;
import org.mvel3.parser.ast.expr.TemporalChunkExpr;
import org.mvel3.parser.ast.expr.TemporalLiteralChunkExpr;
import org.mvel3.parser.ast.expr.TemporalLiteralExpr;

/**
 * Visitor that converts DRLX ANTLR4 parse tree to JavaParser AST nodes.
 * This implementation can return various types of nodes (Expression, Statement, etc.).
 */
public class DRLXToJavaParserVisitor extends DRLXParserBaseVisitor<Node> {

    @Override
    public Node visitCompilationUnit(DRLXParser.CompilationUnitContext ctx) {
        CompilationUnit cu = new CompilationUnit();

        if (ctx.importDeclaration() != null) {
            for (DRLXParser.ImportDeclarationContext importDecl : ctx.importDeclaration()) {
                ImportDeclaration importDeclaration = (ImportDeclaration) visit(importDecl);
                if (importDeclaration != null) {
                    cu.addImport(importDeclaration);
                }
            }
        }

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
    public Node visitImportDeclaration(DRLXParser.ImportDeclarationContext ctx) {
        String importName = ctx.qualifiedName().getText();
        boolean isStatic = ctx.STATIC() != null;
        boolean isAsterisk = ctx.getChildCount() > 3 && "*".equals(ctx.getChild(ctx.getChildCount() - 2).getText());
        return new ImportDeclaration(importName, isStatic, isAsterisk);
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
                items.add(item);
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
        throw new IllegalArgumentException("Unknown rule item type: " + ctx.getText());
    }

    @Override
    public Node visitRulePattern(DRLXParser.RulePatternContext ctx) {
        // Get type and bind identifiers (fall back to placeholders when incomplete)
        String typeText = ctx.identifier().size() > 0 ? ctx.identifier(0).getText() : "var";
        String bindText = ctx.identifier().size() > 1 ? ctx.identifier(1).getText() : "_";

        SimpleName type = new SimpleName(typeText);
        SimpleName bind = new SimpleName(bindText);
        OOPathExpr expr = (OOPathExpr) visit(ctx.oopathExpression());
        
        RulePattern pattern = new RulePattern(null, type, bind, expr);
        
        // Set parent relationships
        type.setParentNode(pattern);
        bind.setParentNode(pattern);
        expr.setParentNode(pattern);
        
        return pattern;
    }

    @Override
    public Node visitRuleConsequence(DRLXParser.RuleConsequenceContext ctx) {
        Statement statement = (Statement) visit(ctx.statement());
        RuleConsequence consequence = new RuleConsequence(null, statement);
        
        // Set parent relationship
        statement.setParentNode(consequence);
        
        return consequence;
    }

    @Override
    public Node visitOopathExpression(DRLXParser.OopathExpressionContext ctx) {
        NodeList<OOPathChunk> chunks = new NodeList<>();
        
        if (ctx.oopathChunk() != null) {
            for (DRLXParser.OopathChunkContext chunkCtx : ctx.oopathChunk()) {
                OOPathChunk chunk = (OOPathChunk) visit(chunkCtx);
                chunks.add(chunk);
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
        SimpleName field = new SimpleName(ctx.identifier(0).getText());
        SimpleName inlineCast = ctx.identifier().size() > 1 ? new SimpleName(ctx.identifier(1).getText()) : null;

        NodeList<DrlxExpression> conditions = new NodeList<>();
        if (ctx.drlxExpression() != null) {
            for (DRLXParser.DrlxExpressionContext drlxCtx : ctx.drlxExpression()) {
                DrlxExpression condition = (DrlxExpression) visit(drlxCtx);
                conditions.add(condition);
            }
        }

        OOPathChunk chunk = new OOPathChunk(null, field, inlineCast, conditions);

        field.setParentNode(chunk);
        if (inlineCast != null) {
            inlineCast.setParentNode(chunk);
        }
        for (DrlxExpression condition : conditions) {
            condition.setParentNode(chunk);
        }

        return chunk;
    }

    @Override
    public Node visitDrlxExpression(DRLXParser.DrlxExpressionContext ctx) {
        SimpleName bind = ctx.identifier() != null ? new SimpleName(ctx.identifier().getText()) : null;
        if (bind != null) {
            bind.setTokenRange(createTokenRange(ctx.identifier()));
        }

        Expression expr = (Expression) visit(ctx.expression());
        if (!expr.getTokenRange().isPresent()) {
            expr.setTokenRange(createTokenRange(ctx.expression()));
        }

        DrlxExpression drlxExpression = new DrlxExpression(bind, expr);
        drlxExpression.setTokenRange(createTokenRange(ctx));
        if (bind != null) {
            bind.setParentNode(drlxExpression);
        }
        expr.setParentNode(drlxExpression);

        return drlxExpression;
    }

    //-------------------------------------------------------------
    //-------------------------------------------------------------
    //-------------------------------------------------------------
    //-------------------------------------------------------------
    //-------------------------------------------------------------
    //--- Below copied from Mvel3ToJavaParserVisitor in mvel3 -----
    
    /**
     * Create a JavaParser TokenRange from ANTLR ParserRuleContext.
     * This provides proper source location information instead of using TokenRange.INVALID.
     */
    protected TokenRange createTokenRange(ParserRuleContext ctx) {
        if (ctx == null) {
            return TokenRange.INVALID;
        }
        
        Token startToken = ctx.getStart();
        Token stopToken = ctx.getStop();
        
        if (startToken == null || stopToken == null) {
            return TokenRange.INVALID;
        }
        
        // Create JavaParser positions
        Position startPos = new Position(startToken.getLine(), startToken.getCharPositionInLine() + 1);
        Position stopPos = new Position(stopToken.getLine(), stopToken.getCharPositionInLine() + stopToken.getText().length());
        
        // Create JavaParser Range
        Range range = new Range(startPos, stopPos);
        
        // Create JavaParser JavaTokens (simplified - we use token type 0 and the actual text)
        JavaToken startJavaToken = new JavaToken(0, startToken.getText());
        startJavaToken.setRange(range);
        
        JavaToken stopJavaToken = new JavaToken(0, stopToken.getText());
        stopJavaToken.setRange(range);
        
        return new TokenRange(startJavaToken, stopJavaToken);
    }
    
    /**
     * Create a TokenRange from a single ANTLR token (for terminal nodes).
     */
    private TokenRange createTokenRange(Token token) {
        if (token == null) {
            return TokenRange.INVALID;
        }
        
        Position startPos = new Position(token.getLine(), token.getCharPositionInLine() + 1);
        Position stopPos = new Position(token.getLine(), token.getCharPositionInLine() + token.getText().length());
        Range range = new Range(startPos, stopPos);
        
        JavaToken javaToken = new JavaToken(0, token.getText());
        javaToken.setRange(range);
        
        return new TokenRange(javaToken, javaToken);
    }

    @Override
    public Node visitMvelStart(DRLXParser.MvelStartContext ctx) {
        return visit(ctx.mvelExpression());
    }

    @Override
    public Node visitMvelExpression(DRLXParser.MvelExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Node visitBinaryOperatorExpression(DRLXParser.BinaryOperatorExpressionContext ctx) {
        Expression left = (Expression) visit(ctx.expression(0));
        Expression right = (Expression) visit(ctx.expression(1));

        String operatorText = resolveOperatorText(ctx);

        // Handle assignment operators separately
        AssignExpr.Operator assignOp = getAssignOperator(operatorText);
        if (assignOp != null) {
            return new AssignExpr(createTokenRange(ctx), left, right, assignOp);
        }
        
        // Handle other binary operators
        BinaryExpr.Operator operator = getBinaryExprOperator(operatorText);
        return new BinaryExpr(createTokenRange(ctx), left, right, operator);
    }
    
    /**
     * Map operator text to AssignExpr.Operator, return null if not an assignment operator
     */
    private AssignExpr.Operator getAssignOperator(String operatorText) {
        switch (operatorText) {
            case "=": return AssignExpr.Operator.ASSIGN;
            case "+=": return AssignExpr.Operator.PLUS;
            case "-=": return AssignExpr.Operator.MINUS;
            case "*=": return AssignExpr.Operator.MULTIPLY;
            case "/=": return AssignExpr.Operator.DIVIDE;
            case "&=": return AssignExpr.Operator.BINARY_AND;
            case "|=": return AssignExpr.Operator.BINARY_OR;
            case "^=": return AssignExpr.Operator.XOR;
            case "%=": return AssignExpr.Operator.REMAINDER;
            case "<<=": return AssignExpr.Operator.LEFT_SHIFT;
            case ">>=": return AssignExpr.Operator.SIGNED_RIGHT_SHIFT;
            case ">>>=": return AssignExpr.Operator.UNSIGNED_RIGHT_SHIFT;
            default: return null;
        }
    }

    @Override
    public Node visitMemberReferenceExpression(DRLXParser.MemberReferenceExpressionContext ctx) {
        // Handle member reference like "java.math.MathContext.DECIMAL128"
        Expression scope = (Expression) visit(ctx.expression());
        
        if (ctx.identifier() != null) {
            // Simple field access: expression.identifier
            String fieldName = ctx.identifier().getText();
            FieldAccessExpr fieldAccess = new FieldAccessExpr(scope, fieldName);
            fieldAccess.setTokenRange(createTokenRange(ctx));
            return fieldAccess;
        } else if (ctx.methodCall() != null) {
            // Method call: expression.methodCall()
            String methodName = ctx.methodCall().identifier().getText();
            NodeList<Expression> args = parseArguments(ctx.methodCall().arguments());
            
            MethodCallExpr methodCall = new MethodCallExpr(scope, methodName);
            methodCall.setArguments(args);
            methodCall.setTokenRange(createTokenRange(ctx));
            return methodCall;
        } else if (ctx.THIS() != null) {
            // expression.this
            FieldAccessExpr fieldAccess = new FieldAccessExpr(scope, "this");
            fieldAccess.setTokenRange(createTokenRange(ctx));
            return fieldAccess;
        } else if (ctx.SUPER() != null && ctx.superSuffix() != null) {
            // expression.super.something
            // TODO: Implement super handling
            throw new UnsupportedOperationException("Super references not yet implemented");
        } else if (ctx.NEW() != null && ctx.innerCreator() != null) {
            // expression.new InnerClass()
            // TODO: Implement inner class creation
            throw new UnsupportedOperationException("Inner class creation not yet implemented");
        }
        
        throw new IllegalArgumentException("Unsupported member reference: " + ctx.getText());
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
            // Always use DrlNameExpr for identifiers to match JavaCC behavior
            // backReferencesCount defaults to 0 for normal identifiers
            DrlNameExpr nameExpr = new DrlNameExpr(ctx.identifier().getText());
            nameExpr.setTokenRange(createTokenRange(ctx));
            return nameExpr;
        } else if (ctx.LPAREN() != null && ctx.expression() != null && ctx.RPAREN() != null) {
            // Parenthesized expression
            EnclosedExpr enclosedExpr = new EnclosedExpr((Expression) visit(ctx.expression()));
            enclosedExpr.setTokenRange(createTokenRange(ctx));
            return enclosedExpr;
        } else if (ctx.THIS() != null) {
            ThisExpr thisExpr = new ThisExpr();
            thisExpr.setTokenRange(createTokenRange(ctx));
            return thisExpr;
        }
        
        // Handle other primary cases that might be needed
        return visitChildren(ctx);
    }

    @Override
    public Node visitInlineCastExpression(DRLXParser.InlineCastExpressionContext ctx) {
        // Handle inline cast: expr#Type#[member]
        Expression scope = (Expression) visit(ctx.expression(0));
        Type type = (Type) visit(ctx.typeType());

        InlineCastExpr inlineCastExpr = new InlineCastExpr(type, scope);
        inlineCastExpr.setTokenRange(createTokenRange(ctx));

        if (ctx.identifier() != null) {
            String name = ctx.identifier().getText();
            if (ctx.arguments() != null) {
                MethodCallExpr methodCall = new MethodCallExpr(inlineCastExpr, name);
                methodCall.setTokenRange(createTokenRange(ctx));
                methodCall.setArguments(parseArguments(ctx.arguments()));
                return methodCall;
            }

            FieldAccessExpr fieldAccess = new FieldAccessExpr(inlineCastExpr, name);
            fieldAccess.setTokenRange(createTokenRange(ctx));
            return fieldAccess;
        }

        if (ctx.LBRACK() != null) {
            Expression indexExpr = (Expression) visit(ctx.expression(1));
            MethodCallExpr methodCall = new MethodCallExpr(inlineCastExpr, "get");
            methodCall.setTokenRange(createTokenRange(ctx));
            methodCall.addArgument(indexExpr);
            return methodCall;
        }

        return inlineCastExpr;
    }

    @Override
    public Node visitListCreationLiteralExpression(DRLXParser.ListCreationLiteralExpressionContext ctx) {
        // Visit the listCreationLiteral rule
        return visit(ctx.listCreationLiteral());
    }

    @Override
    public Node visitListCreationLiteral(DRLXParser.ListCreationLiteralContext ctx) {
        NodeList<Expression> elements = new NodeList<>();

        // Process each list element
        if (ctx.listElement() != null) {
            for (DRLXParser.ListElementContext elementCtx : ctx.listElement()) {
                Expression expr = (Expression) visit(elementCtx.expression());
                // Wrap in ListCreationLiteralExpressionElement as per mvel.jj
                ListCreationLiteralExpressionElement element =
                    new ListCreationLiteralExpressionElement(expr);
                element.setTokenRange(createTokenRange(elementCtx));
                elements.add(element);
            }
        }

        ListCreationLiteralExpression listExpr = new ListCreationLiteralExpression(elements);
        listExpr.setTokenRange(createTokenRange(ctx));
        return listExpr;
    }

    @Override
    public Node visitMapCreationLiteralExpression(DRLXParser.MapCreationLiteralExpressionContext ctx) {
        // Visit the mapCreationLiteral rule
        return visit(ctx.mapCreationLiteral());
    }

    @Override
    public Node visitMapCreationLiteral(DRLXParser.MapCreationLiteralContext ctx) {
        NodeList<Expression> entries = new NodeList<>();

        // Check for empty map syntax [:]
        if (ctx.COLON() != null && ctx.mapEntry().isEmpty()) {
            // Empty map
            MapCreationLiteralExpression mapExpr = new MapCreationLiteralExpression(entries);
            mapExpr.setTokenRange(createTokenRange(ctx));
            return mapExpr;
        }

        // Process each map entry
        if (ctx.mapEntry() != null) {
            for (DRLXParser.MapEntryContext entryCtx : ctx.mapEntry()) {
                Expression key = (Expression) visit(entryCtx.expression(0));
                Expression value = (Expression) visit(entryCtx.expression(1));

                // Wrap in MapCreationLiteralExpressionKeyValuePair as per mvel.jj
                MapCreationLiteralExpressionKeyValuePair pair =
                    new MapCreationLiteralExpressionKeyValuePair(key, value);
                pair.setTokenRange(createTokenRange(entryCtx));
                entries.add(pair);
            }
        }

        MapCreationLiteralExpression mapExpr = new MapCreationLiteralExpression(entries);
        mapExpr.setTokenRange(createTokenRange(ctx));
        return mapExpr;
    }

    @Override
    public Node visitNullSafeExpression(DRLXParser.NullSafeExpressionContext ctx) {
        // Extract the scope (left side of !.)
        Expression scope = (Expression) visit(ctx.expression());

        // Extract the identifier name
        String name = ctx.identifier().getText();

        // Check if there are arguments (method call) or not (field access)
        if (ctx.arguments() != null) {
            // Method call: $p!.getName()
            NodeList<Expression> arguments = new NodeList<>();
            if (ctx.arguments().expressionList() != null) {
                for (DRLXParser.ExpressionContext argCtx : ctx.arguments().expressionList().expression()) {
                    arguments.add((Expression) visit(argCtx));
                }
            }

            // Extract type arguments if present
            NodeList<Type> typeArguments = new NodeList<>();
            if (ctx.typeArguments() != null) {
                for (DRLXParser.TypeArgumentContext typeArgCtx : ctx.typeArguments().typeArgument()) {
                    typeArguments.add((Type) visit(typeArgCtx));
                }
            }

            NullSafeMethodCallExpr methodCall = new NullSafeMethodCallExpr(
                scope,
                typeArguments.isEmpty() ? null : typeArguments,
                name,
                arguments
            );
            methodCall.setTokenRange(createTokenRange(ctx));
            return methodCall;
        } else {
            // Field access: $p!.name
            NullSafeFieldAccessExpr fieldAccess = new NullSafeFieldAccessExpr(scope, name);
            fieldAccess.setTokenRange(createTokenRange(ctx));
            return fieldAccess;
        }
    }

    @Override
    public Node visitCastExpression(DRLXParser.CastExpressionContext ctx) {
        NodeList<Type> parsedTypes = new NodeList<>();
        for (DRLXParser.TypeTypeContext typeCtx : ctx.typeType()) {
            parsedTypes.add((Type) visit(typeCtx));
        }

        Type targetType;
        if (parsedTypes.size() == 1) {
            targetType = parsedTypes.get(0);
        } else {
            NodeList<ReferenceType> referenceTypes = new NodeList<>();
            for (Type type : parsedTypes) {
                if (!(type instanceof ReferenceType)) {
                    throw new IllegalArgumentException("Intersection casts require reference types: " + ctx.getText());
                }
                referenceTypes.add((ReferenceType) type);
            }
            IntersectionType intersectionType = new IntersectionType(referenceTypes);
            intersectionType.setTokenRange(createTokenRange(ctx));
            targetType = intersectionType;
        }

        Expression expression = (Expression) visit(ctx.expression());

        CastExpr castExpr = new CastExpr(targetType, expression);
        castExpr.setTokenRange(createTokenRange(ctx));
        return castExpr;
    }

    @Override
    public Node visitLambdaExpression(DRLXParser.LambdaExpressionContext ctx) {
        LambdaParametersResult parametersResult = resolveLambdaParameters(ctx.lambdaParameters());
        Statement body = resolveLambdaBody(ctx.lambdaBody());

        LambdaExpr lambdaExpr = new LambdaExpr(parametersResult.parameters, body, parametersResult.enclosingParameters);
        lambdaExpr.setTokenRange(createTokenRange(ctx));
        return lambdaExpr;
    }

    @Override
    public Node visitLiteral(DRLXParser.LiteralContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            // Remove quotes
            String value = text.substring(1, text.length() - 1);
            StringLiteralExpr stringLiteral = new StringLiteralExpr(value);
            stringLiteral.setTokenRange(createTokenRange(ctx));
            return stringLiteral;
        } else if (ctx.DECIMAL_LITERAL() != null) {
            String text = ctx.DECIMAL_LITERAL().getText();
            // Check if it's a long literal (ends with 'L' or 'l')
            if (text.endsWith("L") || text.endsWith("l")) {
                LongLiteralExpr longLiteral = new LongLiteralExpr(text);
                longLiteral.setTokenRange(createTokenRange(ctx));
                return longLiteral;
            } else {
                IntegerLiteralExpr integerLiteral = new IntegerLiteralExpr(text);
                integerLiteral.setTokenRange(createTokenRange(ctx));
                return integerLiteral;
            }
        } else if (ctx.FLOAT_LITERAL() != null) {
            DoubleLiteralExpr doubleLiteral = new DoubleLiteralExpr(ctx.FLOAT_LITERAL().getText());
            doubleLiteral.setTokenRange(createTokenRange(ctx));
            return doubleLiteral;
        } else if (ctx.BOOL_LITERAL() != null) {
            BooleanLiteralExpr booleanLiteral = new BooleanLiteralExpr(Boolean.parseBoolean(ctx.BOOL_LITERAL().getText()));
            booleanLiteral.setTokenRange(createTokenRange(ctx));
            return booleanLiteral;
        } else if (ctx.NULL_LITERAL() != null) {
            NullLiteralExpr nullLiteral = new NullLiteralExpr();
            nullLiteral.setTokenRange(createTokenRange(ctx));
            return nullLiteral;
        } else if (ctx.CHAR_LITERAL() != null) {
            String text = ctx.CHAR_LITERAL().getText();
            char value = text.charAt(1); // Simple case, more complex handling needed for escape sequences
            CharLiteralExpr charLiteral = new CharLiteralExpr(value);
            charLiteral.setTokenRange(createTokenRange(ctx));
            return charLiteral;
        } else if (ctx.TEXT_BLOCK() != null) {
            String rawText = ctx.TEXT_BLOCK().getText();
            // Extract content between triple quotes: """content"""
            String content = rawText.substring(3, rawText.length() - 3);
            TextBlockLiteralExpr textBlockLiteral = new TextBlockLiteralExpr(content);
            textBlockLiteral.setTokenRange(createTokenRange(ctx));
            return textBlockLiteral;
        }
        
        // Handle MVEL-specific literals - create proper AST nodes like mvel.jj does
        if (ctx.BigDecimalLiteral() != null) {
            String text = ctx.BigDecimalLiteral().getText();
            // Create BigDecimalLiteralExpr node (transformation happens in MVELToJavaRewriter)
            return new BigDecimalLiteralExpr(createTokenRange(ctx), text);
        } else if (ctx.BigIntegerLiteral() != null) {
            String text = ctx.BigIntegerLiteral().getText();
            // Create BigIntegerLiteralExpr node (transformation happens in MVELToJavaRewriter)
            return new BigIntegerLiteralExpr(createTokenRange(ctx), text);
        } else if (ctx.temporalLiteral() != null) {
            return buildTemporalLiteral(ctx.temporalLiteral());
        }
        
        throw new IllegalArgumentException("Unknown literal type: " + ctx.getText());
    }

    private TemporalLiteralExpr buildTemporalLiteral(DRLXParser.TemporalLiteralContext ctx) {
        NodeList<TemporalChunkExpr> chunks = new NodeList<>();
        for (DRLXParser.TemporalLiteralChunkContext chunkCtx : ctx.temporalLiteralChunk()) {
            chunks.add(buildTemporalLiteralChunk(chunkCtx));
        }
        TemporalLiteralExpr temporalLiteralExpr = new TemporalLiteralExpr(createTokenRange(ctx), chunks);
        return temporalLiteralExpr;
    }

    private TemporalLiteralChunkExpr buildTemporalLiteralChunk(DRLXParser.TemporalLiteralChunkContext ctx) {
        Token token;
        TimeUnit timeUnit;

        if (ctx.MILLISECOND_LITERAL() != null) {
            token = ctx.MILLISECOND_LITERAL().getSymbol();
            timeUnit = TimeUnit.MILLISECONDS;
        } else if (ctx.SECOND_LITERAL() != null) {
            token = ctx.SECOND_LITERAL().getSymbol();
            timeUnit = TimeUnit.SECONDS;
        } else if (ctx.MINUTE_LITERAL() != null) {
            token = ctx.MINUTE_LITERAL().getSymbol();
            timeUnit = TimeUnit.MINUTES;
        } else if (ctx.HOUR_LITERAL() != null) {
            token = ctx.HOUR_LITERAL().getSymbol();
            timeUnit = TimeUnit.HOURS;
        } else {
            throw new IllegalArgumentException("Unsupported temporal literal chunk: " + ctx.getText());
        }

        return new TemporalLiteralChunkExpr(createTokenRange(ctx), token.getText(), timeUnit);
    }

    @Override
    public Node visitClassOrInterfaceType(DRLXParser.ClassOrInterfaceTypeContext ctx) {
        // Grammar: (identifier typeArguments? '.')* typeIdentifier typeArguments?
        
        ClassOrInterfaceType type = null;
        
        // Handle the optional qualified prefix (identifier typeArguments? '.')*
        // For now, we skip typeArguments in the prefix (rare case)
        if (ctx.identifier() != null && !ctx.identifier().isEmpty()) {
            for (int i = 0; i < ctx.identifier().size(); i++) {
                String name = ctx.identifier(i).getText();
                ClassOrInterfaceType newType = new ClassOrInterfaceType(type, name);
                newType.setTokenRange(createTokenRange(ctx));
                type = newType;
                // TODO: Handle typeArguments for intermediate identifiers if needed (rare case)
            }
        }
        
        // Handle the required typeIdentifier at the end
        if (ctx.typeIdentifier() != null) {
            String typeName = ctx.typeIdentifier().getText();
            ClassOrInterfaceType newType = new ClassOrInterfaceType(type, typeName);
            newType.setTokenRange(createTokenRange(ctx));
            type = newType;
            
            // Handle final typeArguments if present (the common case for generics like List<Foo>)
            if (ctx.typeArguments() != null && !ctx.typeArguments().isEmpty()) {
                // Get the LAST typeArguments (which should be for the typeIdentifier)
                DRLXParser.TypeArgumentsContext lastTypeArgs = ctx.typeArguments(ctx.typeArguments().size() - 1);
                NodeList<com.github.javaparser.ast.type.Type> typeArgs = new NodeList<>();
                
                for (DRLXParser.TypeArgumentContext typeArgCtx : lastTypeArgs.typeArgument()) {
                    com.github.javaparser.ast.type.Type typeArg = (com.github.javaparser.ast.type.Type) visit(typeArgCtx);
                    if (typeArg != null) {
                        typeArgs.add(typeArg);
                    }
                }
                
                if (!typeArgs.isEmpty()) {
                    newType.setTypeArguments(typeArgs);
                }
            }
        } else {
            throw new IllegalArgumentException("Missing typeIdentifier in ClassOrInterfaceType: " + ctx.getText());
        }
        
        return type;
    }

    @Override
    public Node visitTypeType(DRLXParser.TypeTypeContext ctx) {
        Type baseType = null;
        
        // Handle different type possibilities
        if (ctx.classOrInterfaceType() != null) {
            baseType = (Type) visit(ctx.classOrInterfaceType());
        } else if (ctx.primitiveType() != null) {
            baseType = (Type) visit(ctx.primitiveType());
        }
        
        if (baseType == null) {
            // Fall back to default behavior
            return visitChildren(ctx);
        }
        
        // Handle array dimensions: (annotation* '[' ']')*
        // Count the number of '[' ']' pairs to determine array dimensions
        int arrayDimensions = 0;
        if (ctx.children != null) {
            for (ParseTree child : ctx.children) {
                if (child instanceof TerminalNode && "[".equals(child.getText())) {
                    arrayDimensions++;
                }
            }
        }
        
        // Wrap base type in ArrayType for each dimension
        Type resultType = baseType;
        for (int i = 0; i < arrayDimensions; i++) {
            resultType = new ArrayType(resultType);
        }
        
        if (resultType instanceof ArrayType) {
            resultType.setTokenRange(createTokenRange(ctx));
        }
        
        return resultType;
    }

    @Override
    public Node visitPrimitiveType(DRLXParser.PrimitiveTypeContext ctx) {
        // Map ANTLR primitive types to JavaParser PrimitiveType
        String typeName = ctx.getText();
        PrimitiveType.Primitive primitive;
        
        switch (typeName) {
            case "boolean":
                primitive = PrimitiveType.Primitive.BOOLEAN;
                break;
            case "byte":
                primitive = PrimitiveType.Primitive.BYTE;
                break;
            case "short":
                primitive = PrimitiveType.Primitive.SHORT;
                break;
            case "int":
                primitive = PrimitiveType.Primitive.INT;
                break;
            case "long":
                primitive = PrimitiveType.Primitive.LONG;
                break;
            case "char":
                primitive = PrimitiveType.Primitive.CHAR;
                break;
            case "float":
                primitive = PrimitiveType.Primitive.FLOAT;
                break;
            case "double":
                primitive = PrimitiveType.Primitive.DOUBLE;
                break;
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + typeName);
        }
        
        PrimitiveType primitiveType = new PrimitiveType(primitive);
        primitiveType.setTokenRange(createTokenRange(ctx));
        return primitiveType;
    }

    @Override
    public Node visitSquareBracketExpression(DRLXParser.SquareBracketExpressionContext ctx) {
        // Handle array/list access: expression[index]
        Expression array = (Expression) visit(ctx.expression(0)); // The array/list expression
        Expression index = (Expression) visit(ctx.expression(1)); // The index expression
        
        // Create ArrayAccessExpr like mvel.jj does
        // The transformation to .get() method calls is handled by MVELToJavaRewriter
        ArrayAccessExpr arrayAccess = new ArrayAccessExpr(array, index);
        arrayAccess.setTokenRange(createTokenRange(ctx));
        return arrayAccess;
    }

    @Override
    public Node visitBlock(DRLXParser.BlockContext ctx) {
        BlockStmt blockStmt = new BlockStmt();
        blockStmt.setTokenRange(createTokenRange(ctx));
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
        if (ctx.localVariableDeclaration() != null) {
            // Handle local variable declaration
            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) visit(ctx.localVariableDeclaration());
            ExpressionStmt exprStmt = new ExpressionStmt(varDecl);
            exprStmt.setTokenRange(createTokenRange(ctx));
            return exprStmt;
        } else if (ctx.statement() != null) {
            return visit(ctx.statement());
        } else if (ctx.localTypeDeclaration() != null) {
            // TODO: Handle local type declarations if needed
            throw new UnsupportedOperationException("Local type declarations not yet implemented");
        }
        return null;
    }

    @Override
    public Node visitStatement(DRLXParser.StatementContext ctx) {
        // Handle modify statement
        if (ctx.modifyStatement() != null) {
            return visit(ctx.modifyStatement());
        } else if (ctx.statementExpression != null) {
            // Handle expression statement: expression ';'
            Expression expr = (Expression) visit(ctx.statementExpression);
            ExpressionStmt exprStmt = new ExpressionStmt(expr);
            exprStmt.setTokenRange(createTokenRange(ctx));
            return exprStmt;
        } else if (ctx.blockLabel != null) {
            // Handle block statement
            return visit(ctx.blockLabel);
        } else if (ctx.IF() != null) {
            // Handle if statement: IF parExpression statement (ELSE statement)?
            Expression condition = (Expression) visit(ctx.parExpression().expression());
            Statement thenStmt = (Statement) visit(ctx.statement(0));
            
            IfStmt ifStmt = new IfStmt(condition, thenStmt, null);
            ifStmt.setTokenRange(createTokenRange(ctx));
            
            // Handle else clause if present
            if (ctx.ELSE() != null && ctx.statement().size() > 1) {
                Statement elseStmt = (Statement) visit(ctx.statement(1));
                ifStmt.setElseStmt(elseStmt);
            }
            
            return ifStmt;
        } else if (ctx.DO() != null) {
            // Handle do-while statement: DO statement WHILE parExpression ';'
            Statement body = (Statement) visit(ctx.statement(0));
            Expression condition = (Expression) visit(ctx.parExpression().expression());

            DoStmt doStmt = new DoStmt(body, condition);
            doStmt.setTokenRange(createTokenRange(ctx));
            return doStmt;
        } else if (ctx.WHILE() != null) {
            // Handle while statement: WHILE parExpression statement
            Expression condition = (Expression) visit(ctx.parExpression().expression());
            Statement body = (Statement) visit(ctx.statement(0));
            
            WhileStmt whileStmt = new WhileStmt(condition, body);
            whileStmt.setTokenRange(createTokenRange(ctx));
            return whileStmt;
        } else if (ctx.FOR() != null) {
            // Handle for statement: FOR '(' forControl ')' statement
            Statement body = (Statement) visit(ctx.statement(0));

            // Check if this is an enhanced for loop (foreach)
            if (ctx.forControl() != null && ctx.forControl().enhancedForControl() != null) {
                // Create ForEachStmt for enhanced for loops
                ForEachStmt forEachStmt = new ForEachStmt();
                forEachStmt.setBody(body);
                visitEnhancedForControlAndPopulate(ctx.forControl().enhancedForControl(), forEachStmt);
                forEachStmt.setTokenRange(createTokenRange(ctx));
                return forEachStmt;
            } else {
                // Create regular ForStmt for traditional for loops
                ForStmt forStmt = new ForStmt();
                forStmt.setBody(body);

                // Parse forControl if available
                if (ctx.forControl() != null) {
                    visitForControlAndPopulate(ctx.forControl(), forStmt);
                }

                forStmt.setTokenRange(createTokenRange(ctx));
                return forStmt;
            }
        } else if (ctx.SWITCH() != null) {
            // Handle switch statement: SWITCH parExpression '{' switchBlockStatementGroup* switchLabel* '}'
            Expression selector = (Expression) visit(ctx.parExpression().expression());
            
            SwitchStmt switchStmt = new SwitchStmt();
            switchStmt.setSelector(selector);
            switchStmt.setTokenRange(createTokenRange(ctx));
            
            NodeList<SwitchEntry> entries = new NodeList<>();
            
            // Process switchBlockStatementGroups
            if (ctx.switchBlockStatementGroup() != null) {
                for (DRLXParser.SwitchBlockStatementGroupContext groupCtx : ctx.switchBlockStatementGroup()) {
                    SwitchEntry entry = processSwitchBlockStatementGroup(groupCtx);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            
            // Process standalone switchLabels (if any)
            if (ctx.switchLabel() != null) {
                for (DRLXParser.SwitchLabelContext labelCtx : ctx.switchLabel()) {
                    SwitchEntry entry = processSwitchLabel(labelCtx);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            
            switchStmt.setEntries(entries);
            return switchStmt;
        } else if (ctx.RETURN() != null) {
            // Handle return statement: RETURN expression? ';'
            ReturnStmt returnStmt;
            // Get the first expression after RETURN (if any)
            if (ctx.expression() != null && !ctx.expression().isEmpty()) {
                Expression expr = (Expression) visit(ctx.expression(0));
                returnStmt = new ReturnStmt(expr);
            } else {
                returnStmt = new ReturnStmt();
            }
            returnStmt.setTokenRange(createTokenRange(ctx));
            return returnStmt;
        }
        // TODO: Handle other statement types as needed (TRY, THROW, BREAK, CONTINUE, etc.)
        // For now, fall back to default behavior
        return visitChildren(ctx);
    }
    
    @Override
    public Node visitFormalParameter(DRLXParser.FormalParameterContext ctx) {
        ModifiersAnnotations modifiersAnnotations = parseVariableModifiers(ctx.variableModifier());
        Type type = (Type) visit(ctx.typeType());
        Type adjustedType = applyArrayDimensions(type, ctx.variableDeclaratorId());
        SimpleName name = createSimpleName(ctx.variableDeclaratorId().identifier());

        Parameter parameter = new Parameter(modifiersAnnotations.modifiers,
                modifiersAnnotations.annotations,
                adjustedType,
                false,
                new NodeList<>(),
                name);
        parameter.setTokenRange(createTokenRange(ctx));
        return parameter;
    }

    @Override
    public Node visitLastFormalParameter(DRLXParser.LastFormalParameterContext ctx) {
        ModifiersAnnotations modifiersAnnotations = parseVariableModifiers(ctx.variableModifier());
        Type type = (Type) visit(ctx.typeType());
        boolean isVarArgs = ctx.ELLIPSIS() != null;

        NodeList<AnnotationExpr> varArgsAnnotations = new NodeList<>();
        if (ctx.annotation() != null) {
            for (DRLXParser.AnnotationContext annotationContext : ctx.annotation()) {
                varArgsAnnotations.add(parseAnnotationExpr(annotationContext));
            }
        }

        Type adjustedType = applyArrayDimensions(type, ctx.variableDeclaratorId());
        SimpleName name = createSimpleName(ctx.variableDeclaratorId().identifier());

        Parameter parameter = new Parameter(modifiersAnnotations.modifiers,
                modifiersAnnotations.annotations,
                adjustedType,
                isVarArgs,
                varArgsAnnotations,
                name);
        parameter.setTokenRange(createTokenRange(ctx));
        return parameter;
    }

    @Override
    public Node visitModifyStatement(DRLXParser.ModifyStatementContext ctx) {
        // modify ( identifier ) { statement* }
        String targetName = ctx.identifier().getText();
        NameExpr target = new NameExpr(targetName);
        target.setTokenRange(createTokenRange(ctx));

        // Create a NodeList for the statements  
        NodeList<Statement> statements = new NodeList<>();
        
        // Process each statement in the modify block
        // Keep assignments as simple names - MVELToJavaRewriter will add the target prefix
        for (DRLXParser.StatementContext stmtCtx : ctx.statement()) {
            Statement stmt = (Statement) visit(stmtCtx);
            statements.add(stmt);
        }
        
        // Create and return a ModifyStatement with proper TokenRange
        return new ModifyStatement(createTokenRange(ctx), target, statements);
    }

    @Override
    public Node visitWithStatement(DRLXParser.WithStatementContext ctx) {
        String targetName = ctx.identifier().getText();
        NameExpr target = new NameExpr(targetName);
        target.setTokenRange(createTokenRange(ctx));

        NodeList<Statement> statements = new NodeList<>();
        for (DRLXParser.StatementContext stmtCtx : ctx.statement()) {
            Statement stmt = (Statement) visit(stmtCtx);
            statements.add(stmt);
        }

        return new WithStatement(createTokenRange(ctx), target, statements);
    }

    @Override
    public Node visitLocalVariableDeclaration(DRLXParser.LocalVariableDeclarationContext ctx) {
        // Handle both: var x = expression; and Type name = expression;
        
        if (ctx.VAR() != null) {
            // Handle: var x = expression;
            Type varType = new VarType();
            varType.setTokenRange(createTokenRange(ctx));
            String varName = ctx.identifier().getText();
            
            VariableDeclarator varDeclarator = new VariableDeclarator(varType, varName);
            varDeclarator.setTokenRange(createTokenRange(ctx));
            
            // Handle initializer for var declaration
            if (ctx.expression() != null) {
                Expression initializer = (Expression) visit(ctx.expression());
                varDeclarator.setInitializer(initializer);
            }
            
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(varDeclarator);
            varDecl.setTokenRange(createTokenRange(ctx));
            return varDecl;
        } else if (ctx.typeType() != null && ctx.variableDeclarators() != null) {
            // Handle: Type name = expression;
            Type varType = (Type) visit(ctx.typeType());
            
            // Create NodeList for multiple declarators (though we usually have just one)
            NodeList<VariableDeclarator> declarators = new NodeList<>();
            
            for (DRLXParser.VariableDeclaratorContext declaratorCtx : ctx.variableDeclarators().variableDeclarator()) {
                // Get variable name
                String varName = declaratorCtx.variableDeclaratorId().identifier().getText();
                
                // Create variable declarator
                VariableDeclarator varDeclarator = new VariableDeclarator(varType, varName);
                varDeclarator.setTokenRange(createTokenRange(declaratorCtx));
                
                // Handle initializer if present
                if (declaratorCtx.variableInitializer() != null) {
                    Expression initializer = (Expression) visit(declaratorCtx.variableInitializer());
                    varDeclarator.setInitializer(initializer);
                }
                
                declarators.add(varDeclarator);
            }
            
            // Create the variable declaration expression with all declarators
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(declarators);
            varDecl.setTokenRange(createTokenRange(ctx));
            return varDecl;
        } else {
            throw new IllegalArgumentException("Unsupported local variable declaration: " + ctx.getText());
        }
    }

    @Override
    public Node visitVariableInitializer(DRLXParser.VariableInitializerContext ctx) {
        if (ctx.arrayInitializer() != null) {
            return visit(ctx.arrayInitializer());
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return null;
    }

    @Override
    public Node visitArrayInitializer(DRLXParser.ArrayInitializerContext ctx) {
        NodeList<Expression> values = new NodeList<>();
        
        if (ctx.variableInitializer() != null && !ctx.variableInitializer().isEmpty()) {
            for (DRLXParser.VariableInitializerContext initCtx : ctx.variableInitializer()) {
                Expression expr = (Expression) visit(initCtx);
                if (expr != null) {
                    values.add(expr);
                }
            }
        }
        
        ArrayInitializerExpr arrayInit = new ArrayInitializerExpr(values);
        arrayInit.setTokenRange(createTokenRange(ctx));
        return arrayInit;
    }

    @Override
    public Node visitObjectCreationExpression(DRLXParser.ObjectCreationExpressionContext ctx) {
        return visit(ctx.creator());
    }

    @Override
    public Node visitCreator(DRLXParser.CreatorContext ctx) {
        Node createdName = visit(ctx.createdName());
        
        if (ctx.arrayCreatorRest() != null) {
            // Handle array creation: new Type[] {...} or new Type[size]
            return visitArrayCreatorRest(ctx.arrayCreatorRest(), createdName);
        } else if (ctx.classCreatorRest() != null) {
            // Handle class creation: new Type(args)
            Type type = (Type) createdName;

            // Get constructor arguments
            NodeList<Expression> arguments = new NodeList<>();
            if (ctx.classCreatorRest().arguments() != null &&
                ctx.classCreatorRest().arguments().expressionList() != null) {
                for (DRLXParser.ExpressionContext exprCtx : ctx.classCreatorRest().arguments().expressionList().expression()) {
                    Expression arg = (Expression) visit(exprCtx);
                    arguments.add(arg);
                }
            }

            // Create ObjectCreationExpr
            ObjectCreationExpr objectCreation = new ObjectCreationExpr(null, (ClassOrInterfaceType) type, arguments);
            objectCreation.setTokenRange(createTokenRange(ctx));
            return objectCreation;
        }
        
        return createdName;
    }

    private Node visitArrayCreatorRest(DRLXParser.ArrayCreatorRestContext ctx, Node createdName) {
        Type elementType = (Type) createdName;
        
        if (ctx.arrayInitializer() != null) {
            // Handle: new Type[] { ... }
            ArrayInitializerExpr initializer = (ArrayInitializerExpr) visit(ctx.arrayInitializer());
            
            // Count the array dimensions from '[' ']' pairs
            int dimensions = 0;
            if (ctx.children != null) {
                for (ParseTree child : ctx.children) {
                    if (child instanceof TerminalNode && "[".equals(child.getText())) {
                        dimensions++;
                    }
                }
            }
            
            // Create ArrayCreationLevel objects for each dimension (empty for array initializer)
            NodeList<ArrayCreationLevel> levels = new NodeList<>();
            for (int i = 0; i < dimensions; i++) {
                ArrayCreationLevel level = new ArrayCreationLevel();
                level.setTokenRange(createTokenRange(ctx)); 
                levels.add(level);
            }
            
            // Create ArrayCreationExpr
            ArrayCreationExpr arrayCreation = new ArrayCreationExpr(elementType, levels, initializer);
            arrayCreation.setTokenRange(createTokenRange(ctx));
            return arrayCreation;
        } else {
            // Handle: new Type[size] or new Type[size1][size2]
            NodeList<ArrayCreationLevel> levels = new NodeList<>();
            
            if (ctx.expression() != null) {
                for (DRLXParser.ExpressionContext exprCtx : ctx.expression()) {
                    Expression dimExpr = (Expression) visit(exprCtx);
                    ArrayCreationLevel level = new ArrayCreationLevel(dimExpr);
                    level.setTokenRange(createTokenRange(exprCtx));
                    levels.add(level);
                }
            }
            
            // Create ArrayCreationExpr with dimensions
            ArrayCreationExpr arrayCreation = new ArrayCreationExpr(elementType, levels, null);
            arrayCreation.setTokenRange(createTokenRange(ctx));
            return arrayCreation;
        }
    }

    @Override
    public Node visitCreatedName(DRLXParser.CreatedNameContext ctx) {
        if (ctx.primitiveType() != null) {
            return visit(ctx.primitiveType());
        } else if (ctx.identifier() != null && !ctx.identifier().isEmpty()) {
            // Handle class/interface type creation - build qualified name with type arguments
            ClassOrInterfaceType type = null;

            // Build the qualified name from all identifiers with their type arguments
            for (int i = 0; i < ctx.identifier().size(); i++) {
                String name = ctx.identifier(i).getText();

                // Check if this identifier has type arguments or diamond operator
                NodeList<Type> typeArguments = null;
                if (i < ctx.typeArgumentsOrDiamond().size() && ctx.typeArgumentsOrDiamond(i) != null) {
                    typeArguments = handleTypeArgumentsOrDiamond(ctx.typeArgumentsOrDiamond(i));
                }

                type = new ClassOrInterfaceType(type, name);
                if (typeArguments != null) {
                    type.setTypeArguments(typeArguments);
                }
            }

            if (type != null) {
                type.setTokenRange(createTokenRange(ctx));
            }
            return type;
        }

        throw new IllegalArgumentException("Unsupported created name: " + ctx.getText());
    }

    private NodeList<Type> handleTypeArgumentsOrDiamond(DRLXParser.TypeArgumentsOrDiamondContext ctx) {
        if (ctx.typeArguments() != null) {
            // Handle full type arguments: <String, Integer>
            NodeList<Type> typeArgs = new NodeList<>();
            for (DRLXParser.TypeArgumentContext typeArgCtx : ctx.typeArguments().typeArgument()) {
                Type typeArg = (Type) visit(typeArgCtx);
                typeArgs.add(typeArg);
            }
            return typeArgs;
        } else {
            // Handle diamond operator: <> - return empty NodeList to represent diamond
            return new NodeList<>();
        }
    }


    @Override
    public Node visitMethodCall(DRLXParser.MethodCallContext ctx) {
        String methodName = ctx.identifier().getText();
        NodeList<Expression> args = parseArguments(ctx.arguments());
        
        // For method calls in member reference, we need the scope from the parent context
        // This will be handled by visitMemberReferenceExpression
        MethodCallExpr methodCall = new MethodCallExpr(null, methodName);
        methodCall.setTokenRange(createTokenRange(ctx));
        methodCall.setArguments(args);
        return methodCall;
    }

    protected NodeList<Expression> parseArguments(DRLXParser.ArgumentsContext ctx) {
        NodeList<Expression> args = new NodeList<>();
        if (ctx.expressionList() != null) {
            // Parse each expression in the argument list
            for (DRLXParser.ExpressionContext exprCtx : ctx.expressionList().expression()) {
                Expression arg = (Expression) visit(exprCtx);
                args.add(arg);
            }
        }
        return args;
    }

    @Override
    public Node visitUnaryOperatorExpression(DRLXParser.UnaryOperatorExpressionContext ctx) {
        // Handle unary operators: +expr, -expr, ++expr, --expr, ~expr, !expr
        Expression operand = (Expression) visit(ctx.expression());
        String operator = ctx.prefix.getText();

        UnaryExpr.Operator unaryOp;
        switch (operator) {
            case "+": unaryOp = UnaryExpr.Operator.PLUS; break;
            case "-": unaryOp = UnaryExpr.Operator.MINUS; break;
            case "++": unaryOp = UnaryExpr.Operator.PREFIX_INCREMENT; break;
            case "--": unaryOp = UnaryExpr.Operator.PREFIX_DECREMENT; break;
            case "~": unaryOp = UnaryExpr.Operator.BITWISE_COMPLEMENT; break;
            case "!": unaryOp = UnaryExpr.Operator.LOGICAL_COMPLEMENT; break;
            default:
                throw new IllegalArgumentException("Unknown unary operator: " + operator);
        }

        UnaryExpr unaryExpr = new UnaryExpr(operand, unaryOp);
        unaryExpr.setTokenRange(createTokenRange(ctx));
        return unaryExpr;
    }

    @Override
    public Node visitPostIncrementDecrementOperatorExpression(DRLXParser.PostIncrementDecrementOperatorExpressionContext ctx) {
        // Handle post-increment and post-decrement: expression++ or expression--
        Expression operand = (Expression) visit(ctx.expression());
        String operator = ctx.postfix.getText();
        
        UnaryExpr.Operator unaryOp;
        if ("++".equals(operator)) {
            unaryOp = UnaryExpr.Operator.POSTFIX_INCREMENT;
        } else if ("--".equals(operator)) {
            unaryOp = UnaryExpr.Operator.POSTFIX_DECREMENT;
        } else {
            throw new IllegalArgumentException("Unknown post-increment/decrement operator: " + operator);
        }
        
        UnaryExpr unaryExpr = new UnaryExpr(operand, unaryOp);
        unaryExpr.setTokenRange(createTokenRange(ctx));
        return unaryExpr;
    }

    // Handle constant expressions by visiting the contained expression
    private Node visitConstantExpr(DRLXParser.ExpressionContext ctx) {
        return visit(ctx);
    }

    private void visitForControlAndPopulate(DRLXParser.ForControlContext forControlCtx, ForStmt forStmt) {
        // FOR control can be: forInit? ';' expression? ';' forUpdate?
        // Or enhanced for: variableDeclarator ':' expression
        
        // Check if it's an enhanced for (for-each) loop
        if (forControlCtx.enhancedForControl() != null) {
            // Enhanced for: for (Type var : iterable)
            // TODO: Implement enhanced for loop parsing if needed
            return;
        }
        
        // Regular for loop: for (init; condition; update)
        NodeList<Expression> initialization = new NodeList<>();
        Expression compare = null;
        NodeList<Expression> update = new NodeList<>();
        
        // Parse forInit
        if (forControlCtx.forInit() != null) {
            // forInit can be localVariableDeclaration or expressionList
            if (forControlCtx.forInit().localVariableDeclaration() != null) {
                // Variable declaration like: int i = 0
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) visit(forControlCtx.forInit().localVariableDeclaration());
                if (varDecl != null) {
                    initialization.add(varDecl);
                }
            } else if (forControlCtx.forInit().expressionList() != null) {
                // Expression list like: i = 0, j = 1
                for (DRLXParser.ExpressionContext exprCtx : forControlCtx.forInit().expressionList().expression()) {
                    Expression expr = (Expression) visit(exprCtx);
                    if (expr != null) {
                        initialization.add(expr);
                    }
                }
            }
        }
        
        // Parse condition
        if (forControlCtx.expression() != null) {
            compare = (Expression) visit(forControlCtx.expression());
        }
        
        // Parse forUpdate
        if (forControlCtx.forUpdate != null) {
            for (DRLXParser.ExpressionContext exprCtx : forControlCtx.forUpdate.expression()) {
                Expression expr = (Expression) visit(exprCtx);
                if (expr != null) {
                    update.add(expr);
                }
            }
        }
        
        // Set the for loop components - only set non-empty lists
        if (!initialization.isEmpty()) {
            forStmt.setInitialization(initialization);
        }
        if (compare != null) {
            forStmt.setCompare(compare);
        }
        if (!update.isEmpty()) {
            forStmt.setUpdate(update);
        }
    }

    private void visitEnhancedForControlAndPopulate(DRLXParser.EnhancedForControlContext enhancedForCtx, ForEachStmt forEachStmt) {
        // enhancedForControl: variableModifier* (typeType | VAR) variableDeclaratorId ':' expression

        // Extract variable modifiers
        NodeList<Modifier> modifiers = new NodeList<>();
        if (enhancedForCtx.variableModifier() != null) {
            for (DRLXParser.VariableModifierContext modCtx : enhancedForCtx.variableModifier()) {
                // TODO: Handle variable modifiers if needed (final, etc.)
            }
        }

        // Extract type (typeType or VAR)
        Type variableType = null;
        if (enhancedForCtx.typeType() != null) {
            variableType = (Type) visit(enhancedForCtx.typeType());
        } else if (enhancedForCtx.VAR() != null) {
            // Handle var type - use VarType from JavaParser
            variableType = new VarType();
        }

        // Extract variable name from variableDeclaratorId
        String variableName = enhancedForCtx.variableDeclaratorId().identifier().getText();

        // Create VariableDeclarator
        VariableDeclarator variableDeclarator = new VariableDeclarator(variableType, variableName);

        // Create VariableDeclarationExpr like mvel.jj does
        NodeList<VariableDeclarator> variables = new NodeList<>();
        variables.add(variableDeclarator);
        VariableDeclarationExpr varDecl = new VariableDeclarationExpr(modifiers, variables);

        // Extract iterable expression
        Expression iterable = (Expression) visit(enhancedForCtx.expression());

        // Set the ForEachStmt components to match mvel.jj: ForEachStmt(range, varExpr, expr, body)
        forEachStmt.setVariable(varDecl);
        forEachStmt.setIterable(iterable);
    }

    private SwitchEntry processSwitchBlockStatementGroup(DRLXParser.SwitchBlockStatementGroupContext groupCtx) {
        // switchBlockStatementGroup: switchLabel+ blockStatement*
        if (groupCtx.switchLabel() == null || groupCtx.switchLabel().isEmpty()) {
            return null;
        }

        // Process the first switch label
        DRLXParser.SwitchLabelContext firstLabel = groupCtx.switchLabel(0);
        SwitchEntry entry = processSwitchLabel(firstLabel);
        
        if (entry != null) {
            // Process statements in this switch block
            NodeList<Statement> statements = new NodeList<>();
            if (groupCtx.blockStatement() != null) {
                for (DRLXParser.BlockStatementContext blockStmtCtx : groupCtx.blockStatement()) {
                    Node stmt = visit(blockStmtCtx);
                    if (stmt instanceof Statement) {
                        statements.add((Statement) stmt);
                    }
                }
            }
            entry.setStatements(statements);
        }
        
        return entry;
    }

    private SwitchEntry processSwitchLabel(DRLXParser.SwitchLabelContext labelCtx) {
        // switchLabel: CASE (constantExpression | enumConstantName | typeType varName=IDENTIFIER) ':'
        //            | DEFAULT ':'
        
        if (labelCtx.CASE() != null) {
            // Case label
            NodeList<Expression> labels = new NodeList<>();
            
            if (labelCtx.constantExpression != null) {
                // case constantExpression:
                Expression caseExpr = (Expression) visitConstantExpr(labelCtx.constantExpression);
                if (caseExpr != null) {
                    labels.add(caseExpr);
                }
            } else if (labelCtx.enumConstantName != null) {
                // case enumConstantName:
                String enumName = labelCtx.enumConstantName.getText();
                labels.add(new NameExpr(enumName));
            }
            // TODO: Handle typeType varName case if needed
            
            SwitchEntry entry = new SwitchEntry(labels, SwitchEntry.Type.STATEMENT_GROUP, new NodeList<>());
            entry.setTokenRange(createTokenRange(labelCtx));
            return entry;
            
        } else if (labelCtx.DEFAULT() != null) {
            // Default label
            SwitchEntry entry = new SwitchEntry(new NodeList<>(), SwitchEntry.Type.STATEMENT_GROUP, new NodeList<>());
            entry.setTokenRange(createTokenRange(labelCtx));
            return entry;
        }
        
        return null;
    }

    @Override
    public Node visitTypeArguments(DRLXParser.TypeArgumentsContext ctx) {
        // This is handled by visitClassOrInterfaceType
        // Just return the result of visiting the type arguments
        return visitChildren(ctx);
    }

    @Override
    public Node visitTypeArgument(DRLXParser.TypeArgumentContext ctx) {
        // Handle individual type argument like "Foo" in List<Foo>
        if (ctx.typeType() != null) {
            return visit(ctx.typeType());
        } else if (ctx.annotation() != null && !ctx.annotation().isEmpty()) {
            // Handle annotated wildcards - for now, just handle the basic wildcard case
            // TODO: Implement annotation handling if needed
            return new WildcardType();
        } else {
            // Handle wildcards: ? extends Type or ? super Type
            WildcardType wildcard = new WildcardType();
            if (ctx.EXTENDS() != null && ctx.typeType() != null) {
                Type extendedType = (Type) visit(ctx.typeType());
                // Cast to ReferenceType for JavaParser compatibility
                if (extendedType instanceof ClassOrInterfaceType) {
                    wildcard.setExtendedType((ClassOrInterfaceType) extendedType);
                }
            } else if (ctx.SUPER() != null && ctx.typeType() != null) {
                Type superType = (Type) visit(ctx.typeType());
                // Cast to ReferenceType for JavaParser compatibility
                if (superType instanceof ClassOrInterfaceType) {
                    wildcard.setSuperType((ClassOrInterfaceType) superType);
                }
            }
            wildcard.setTokenRange(createTokenRange(ctx));
            return wildcard;
        }
    }

    private LambdaParametersResult resolveLambdaParameters(DRLXParser.LambdaParametersContext ctx) {
        if (ctx == null) {
            return new LambdaParametersResult(new NodeList<>(), false);
        }

        NodeList<Parameter> parameters = new NodeList<>();
        boolean enclosingParameters = ctx.LPAREN() != null;

        if (!enclosingParameters && ctx.identifier() != null && !ctx.identifier().isEmpty()) {
            parameters.add(createInferredParameter(ctx.identifier(0)));
            return new LambdaParametersResult(parameters, false);
        }

        if (ctx.formalParameterList() != null) {
            parameters.addAll(collectFormalParameters(ctx.formalParameterList()));
        } else if (ctx.lambdaLVTIList() != null) {
            parameters.addAll(collectLambdaLVTIParameters(ctx.lambdaLVTIList()));
        } else if (ctx.identifier() != null && !ctx.identifier().isEmpty()) {
            for (DRLXParser.IdentifierContext identifierContext : ctx.identifier()) {
                parameters.add(createInferredParameter(identifierContext));
            }
        }

        return new LambdaParametersResult(parameters, enclosingParameters);
    }

    private NodeList<Parameter> collectFormalParameters(DRLXParser.FormalParameterListContext ctx) {
        NodeList<Parameter> parameters = new NodeList<>();
        if (ctx == null) {
            return parameters;
        }

        if (ctx.formalParameter() != null) {
            for (DRLXParser.FormalParameterContext formalParameterContext : ctx.formalParameter()) {
                parameters.add((Parameter) visit(formalParameterContext));
            }
        }

        if (ctx.lastFormalParameter() != null) {
            parameters.add((Parameter) visit(ctx.lastFormalParameter()));
        }

        return parameters;
    }

    private NodeList<Parameter> collectLambdaLVTIParameters(DRLXParser.LambdaLVTIListContext ctx) {
        NodeList<Parameter> parameters = new NodeList<>();
        if (ctx != null) {
            for (DRLXParser.LambdaLVTIParameterContext parameterContext : ctx.lambdaLVTIParameter()) {
                parameters.add(createLambdaVarParameter(parameterContext));
            }
        }
        return parameters;
    }

    private Parameter createInferredParameter(DRLXParser.IdentifierContext identifierContext) {
        UnknownType unknownType = new UnknownType();
        unknownType.setTokenRange(createTokenRange(identifierContext));

        SimpleName name = createSimpleName(identifierContext);

        Parameter parameter = new Parameter(new NodeList<>(), new NodeList<>(), unknownType, false, new NodeList<>(), name);
        parameter.setTokenRange(createTokenRange(identifierContext));
        return parameter;
    }

    private Parameter createLambdaVarParameter(DRLXParser.LambdaLVTIParameterContext ctx) {
        ModifiersAnnotations modifiersAnnotations = parseVariableModifiers(ctx.variableModifier());
        VarType varType = new VarType();
        varType.setTokenRange(createTokenRange(ctx));

        SimpleName name = createSimpleName(ctx.identifier());

        Parameter parameter = new Parameter(modifiersAnnotations.modifiers,
                modifiersAnnotations.annotations,
                varType,
                false,
                new NodeList<>(),
                name);
        parameter.setTokenRange(createTokenRange(ctx));
        return parameter;
    }

    private Statement resolveLambdaBody(DRLXParser.LambdaBodyContext ctx) {
        if (ctx.block() != null) {
            return (Statement) visit(ctx.block());
        }

        Expression expression = (Expression) visit(ctx.expression());
        ExpressionStmt expressionStmt = new ExpressionStmt(expression);
        expressionStmt.setTokenRange(createTokenRange(ctx));
        return expressionStmt;
    }

    private ModifiersAnnotations parseVariableModifiers(List<DRLXParser.VariableModifierContext> modifierContexts) {
        NodeList<Modifier> modifiers = new NodeList<>();
        NodeList<AnnotationExpr> annotations = new NodeList<>();

        if (modifierContexts != null) {
            for (DRLXParser.VariableModifierContext modifierContext : modifierContexts) {
                if (modifierContext.FINAL() != null) {
                    Modifier finalModifier = Modifier.finalModifier();
                    finalModifier.setTokenRange(createTokenRange(modifierContext));
                    modifiers.add(finalModifier);
                } else if (modifierContext.annotation() != null) {
                    annotations.add(parseAnnotationExpr(modifierContext.annotation()));
                }
            }
        }

        return new ModifiersAnnotations(modifiers, annotations);
    }

    private Type applyArrayDimensions(Type baseType, DRLXParser.VariableDeclaratorIdContext idContext) {
        if (idContext == null) {
            return baseType;
        }

        int dimensions = idContext.LBRACK() != null ? idContext.LBRACK().size() : 0;

        Type result = baseType;
        for (int i = 0; i < dimensions; i++) {
            ArrayType arrayType = new ArrayType(result);
            arrayType.setTokenRange(createTokenRange(idContext));
            result = arrayType;
        }

        return result;
    }

    private SimpleName createSimpleName(DRLXParser.IdentifierContext identifierContext) {
        SimpleName name = new SimpleName(identifierContext.getText());
        name.setTokenRange(createTokenRange(identifierContext));
        return name;
    }

    private AnnotationExpr parseAnnotationExpr(DRLXParser.AnnotationContext ctx) {
        AnnotationExpr annotationExpr = StaticJavaParser.parseAnnotation(ctx.getText());
        annotationExpr.setTokenRange(createTokenRange(ctx));
        return annotationExpr;
    }

    /**
     * The Java grammar we inherit emits shift operators as separate '<' and '>' tokens, so
     * {@code ctx.bop} remains {@code null}. JavaCC still produces "<<", ">>", ">>>", so we
     * synthesise that text here to keep the generated AST identical to the legacy pipeline.
     */
    private String resolveOperatorText(DRLXParser.BinaryOperatorExpressionContext ctx) {
        if (ctx.bop != null) {
            return ctx.bop.getText();
        }

        // This looks odd, but indeed it's expected by JavaParser.g4
        int ltCount = ctx.LT().size();
        int gtCount = ctx.GT().size();

        if (ltCount == 2) {
            return "<<";
        }
        if (gtCount == 3) {
            return ">>>";
        }
        if (gtCount == 2) {
            return ">>";
        }

        throw new IllegalArgumentException("Unknown binary operator: " + ctx.getText());
    }

    private static final class LambdaParametersResult {
        private final NodeList<Parameter> parameters;
        private final boolean enclosingParameters;

        private LambdaParametersResult(NodeList<Parameter> parameters, boolean enclosingParameters) {
            this.parameters = parameters;
            this.enclosingParameters = enclosingParameters;
        }
    }

    private static final class ModifiersAnnotations {
        private final NodeList<Modifier> modifiers;
        private final NodeList<AnnotationExpr> annotations;

        private ModifiersAnnotations(NodeList<Modifier> modifiers, NodeList<AnnotationExpr> annotations) {
            this.modifiers = modifiers;
            this.annotations = annotations;
        }
    }
}
