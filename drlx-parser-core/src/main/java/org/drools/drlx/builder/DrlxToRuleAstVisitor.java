package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR.Kind;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.parser.DrlxParser;
import org.drools.drlx.parser.DrlxParserBaseVisitor;

/**
 * Walks the DRLX ANTLR parse tree and produces the in-memory {@link DrlxRuleAstModel}
 * representation. This is the single ANTLR-aware step in the build pipeline;
 * downstream consumers ({@link DrlxRuleAstRuntimeBuilder}, {@link DrlxRuleAstParseResult})
 * work solely against the record model.
 */
public class DrlxToRuleAstVisitor extends DrlxParserBaseVisitor<Object> {

    private static final String SALIENCE_FQN = "org.drools.drlx.annotations.Salience";
    private static final String DESCRIPTION_FQN = "org.drools.drlx.annotations.Description";

    private static final Map<String, Kind> SUPPORTED_ANNOTATION_KINDS = Map.of(
            SALIENCE_FQN, Kind.SALIENCE,
            DESCRIPTION_FQN, Kind.DESCRIPTION);

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

        Map<String, String> annotationImports = new LinkedHashMap<>();
        for (String imp : imports) {
            if (SUPPORTED_ANNOTATION_KINDS.containsKey(imp)) {
                String simpleName = imp.substring(imp.lastIndexOf('.') + 1);
                annotationImports.put(simpleName, imp);
            }
        }

        List<RuleIR> rules = new ArrayList<>();
        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().forEach(ruleCtx -> rules.add(buildRule(ruleCtx, annotationImports)));
        }

        String unitName = "";
        if (ctx.unitDeclaration() != null) {
            unitName = ctx.unitDeclaration().qualifiedName().getText();
        }

        return new CompilationUnitIR(packageName, unitName, List.copyOf(imports), List.copyOf(rules));
    }

    private RuleIR buildRule(DrlxParser.RuleDeclarationContext ctx,
                             Map<String, String> annotationImports) {
        String name = ctx.identifier().getText();
        List<RuleAnnotationIR> annotations = buildRuleAnnotations(ctx, annotationImports);
        List<LhsItemIR> lhs = new ArrayList<>();
        ConsequenceIR rhs = null;
        if (ctx.ruleBody() != null) {
            for (DrlxParser.RuleItemContext itemCtx : ctx.ruleBody().ruleItem()) {
                if (itemCtx.ruleConsequence() != null) {
                    if (rhs != null) {
                        throw new RuntimeException(
                                "rule '" + name + "' has more than one consequence block");
                    }
                    rhs = new ConsequenceIR(extractConsequence(itemCtx.ruleConsequence()));
                } else if (itemCtx.rulePattern() != null) {
                    lhs.add(buildPattern(itemCtx.rulePattern()));
                } else {
                    throw new IllegalArgumentException("Unsupported rule item: " + itemCtx.getText());
                }
            }
        }
        return new RuleIR(name, annotations, List.copyOf(lhs), rhs);
    }

    private List<RuleAnnotationIR> buildRuleAnnotations(DrlxParser.RuleDeclarationContext ctx,
                                                        Map<String, String> annotationImports) {
        if (ctx.annotation() == null || ctx.annotation().isEmpty()) {
            return List.of();
        }

        List<RuleAnnotationIR> annotations = new ArrayList<>();
        EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);

        for (DrlxParser.AnnotationContext annCtx : ctx.annotation()) {
            int line = annCtx.getStart().getLine();
            int col = annCtx.getStart().getCharPositionInLine();

            String nameText = annotationNameText(annCtx);
            Kind kind = resolveKind(nameText, annotationImports, line, col);

            if (!seen.add(kind)) {
                throw new RuntimeException(
                        "duplicate @" + kindDisplayName(kind) + " at " + line + ":" + col);
            }

            String rawValue = extractAnnotationLiteral(annCtx, kind, line, col);
            annotations.add(new RuleAnnotationIR(kind, rawValue));
        }

        return List.copyOf(annotations);
    }

    private static String kindDisplayName(Kind kind) {
        return kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
    }

    private static String annotationNameText(DrlxParser.AnnotationContext annCtx) {
        if (annCtx.qualifiedName() != null) {
            return annCtx.qualifiedName().getText();
        }
        if (annCtx.altAnnotationQualifiedName() != null) {
            return annCtx.altAnnotationQualifiedName().getText();
        }
        throw new RuntimeException("Malformed annotation at "
                + annCtx.getStart().getLine() + ":"
                + annCtx.getStart().getCharPositionInLine());
    }

    private static Kind resolveKind(String nameText,
                                    Map<String, String> annotationImports,
                                    int line, int col) {
        if (nameText.contains(".")) {
            Kind kind = SUPPORTED_ANNOTATION_KINDS.get(nameText);
            if (kind != null) {
                return kind;
            }
            throw new RuntimeException(
                    "unsupported DRLX rule annotation '@" + nameText + "' at "
                    + line + ":" + col + " — supported: @Salience, @Description");
        }
        String fqn = annotationImports.get(nameText);
        if (fqn != null) {
            return SUPPORTED_ANNOTATION_KINDS.get(fqn);
        }
        throw new RuntimeException(
                "unresolved annotation '@" + nameText + "' at "
                + line + ":" + col + " — missing import?");
    }

    private static String extractAnnotationLiteral(DrlxParser.AnnotationContext annCtx,
                                                   Kind kind, int line, int col) {
        if (annCtx.elementValue() == null) {
            throw new RuntimeException("@" + kindDisplayName(kind) + " expects one argument at " + line + ":" + col);
        }
        String text = annCtx.elementValue().getText();
        switch (kind) {
            case SALIENCE -> {
                try {
                    return String.valueOf(Integer.parseInt(text));
                } catch (NumberFormatException e) {
                    throw new RuntimeException(
                            "@Salience expects int literal, got '" + text + "' at " + line + ":" + col);
                }
            }
            case DESCRIPTION -> {
                if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    return text.substring(1, text.length() - 1);
                }
                throw new RuntimeException(
                        "@Description expects string literal, got '" + text + "' at " + line + ":" + col);
            }
            default -> throw new IllegalStateException("Unhandled annotation kind: " + kind);
        }
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
