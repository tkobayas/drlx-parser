package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleItemIR;
import org.drools.drlx.parser.DrlxParser;
import org.drools.drlx.parser.DrlxParserBaseVisitor;

/**
 * Walks the DRLX ANTLR parse tree and produces the in-memory {@link DrlxRuleAstModel}
 * representation. This is the single ANTLR-aware step in the build pipeline;
 * downstream consumers ({@link DrlxRuleAstRuntimeBuilder}, {@link DrlxRuleAstParseResult})
 * work solely against the record model.
 */
public class DrlxToRuleAstVisitor extends DrlxParserBaseVisitor<Object> {

    private final TokenStream tokens;

    public DrlxToRuleAstVisitor() {
        this(null);
    }

    public DrlxToRuleAstVisitor(TokenStream tokens) {
        this.tokens = tokens;
    }

    @Override
    public CompilationUnitIR visitDrlxCompilationUnit(DrlxParser.DrlxCompilationUnitContext ctx) {
        String packageName = ctx.packageDeclaration() == null
                ? ""
                : ctx.packageDeclaration().qualifiedName().getText();

        List<String> imports = new ArrayList<>();
        if (ctx.importDeclaration() != null) {
            ctx.importDeclaration().forEach(importCtx -> {
                String target = importCtx.qualifiedName().getText();
                if (importCtx.MUL() != null) {
                    target = target + ".*";
                }
                imports.add(target);
            });
        }

        List<RuleIR> rules = new ArrayList<>();
        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().forEach(ruleCtx -> rules.add(buildRule(ruleCtx)));
        }

        return new CompilationUnitIR(packageName, List.copyOf(imports), List.copyOf(rules));
    }

    private RuleIR buildRule(DrlxParser.RuleDeclarationContext ctx) {
        String name = ctx.identifier().getText();
        List<RuleItemIR> items = new ArrayList<>();
        if (ctx.ruleBody() != null) {
            ctx.ruleBody().ruleItem().forEach(itemCtx -> items.add(buildItem(itemCtx)));
        }
        return new RuleIR(name, List.of(), List.copyOf(items));
    }

    private RuleItemIR buildItem(DrlxParser.RuleItemContext ctx) {
        if (ctx.rulePattern() != null) {
            return buildPattern(ctx.rulePattern());
        } else if (ctx.ruleConsequence() != null) {
            return new ConsequenceIR(extractConsequence(ctx.ruleConsequence()));
        }
        throw new IllegalArgumentException("Unsupported rule item: " + ctx.getText());
    }

    private PatternIR buildPattern(DrlxParser.RulePatternContext ctx) {
        String typeName = ctx.identifier(0).getText();
        String bindName = ctx.identifier(1).getText();
        DrlxParser.OopathExpressionContext oopathCtx = ctx.oopathExpression();
        String entryPoint = extractEntryPointFromOopathCtx(oopathCtx);
        String castTypeName = extractCastType(oopathCtx);
        List<String> conditions = extractConditions(oopathCtx);
        List<String> positionalArgs = extractPositionalArgs(oopathCtx);
        return new PatternIR(typeName, bindName, entryPoint, conditions, castTypeName, positionalArgs);
    }

    private String extractConsequence(DrlxParser.RuleConsequenceContext ctx) {
        String statementText = getText(ctx.statement());
        return trimBraces(statementText);
    }

    private static String extractEntryPointFromOopathCtx(DrlxParser.OopathExpressionContext ctx) {
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null) {
            return "";
        }
        return root.identifier(0).getText();
    }

    /**
     * Extract inline cast type from the oopath root, if present.
     * e.g., {@code /objects#Car[speed > 80]} → {@code "Car"}.
     */
    private static String extractCastType(DrlxParser.OopathExpressionContext ctx) {
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null) {
            return null;
        }
        if (root.identifier().size() > 1) {
            return root.identifier(1).getText();
        }
        return null;
    }

    private static List<String> extractPositionalArgs(DrlxParser.OopathExpressionContext ctx) {
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null || root.expression() == null || root.expression().isEmpty()) {
            return List.of();
        }
        return root.expression().stream()
                .map(ParserRuleContext::getText)
                .toList();
    }

    private List<String> extractConditions(DrlxParser.OopathExpressionContext ctx) {
        List<DrlxParser.OopathChunkContext> chunks = ctx.oopathChunk();
        if (!chunks.isEmpty()) {
            DrlxParser.OopathChunkContext lastChunk = chunks.get(chunks.size() - 1);
            return lastChunk.drlxExpression().stream()
                    .map(this::getText)
                    .toList();
        }
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null) {
            return List.of();
        }
        return root.drlxExpression().stream()
                .map(this::getText)
                .toList();
    }

    private static String trimBraces(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text;
        if (text.startsWith("{") && text.endsWith("}")) {
            stripped = text.substring(1, text.length() - 1);
        }
        return stripped.trim();
    }

    private String getText(ParserRuleContext ctx) {
        return tokens != null ? tokens.getText(ctx) : ctx.getText();
    }
}
