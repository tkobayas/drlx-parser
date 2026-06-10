package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.drools.drlx.builder.DrlxRuleAstModel.AccumulatePatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.AccumulatorIR;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.CustomAccumulateIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.InitVarIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleAnnotationIR.Kind;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleParameterIR;
import org.drools.drlx.builder.DrlxRuleAstModel.TemporalConditionIR;
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
    private static final String DATASOURCE_FQN = "org.drools.drlx.annotations.DataSource";
    private static final String NO_LOOP_FQN = "org.drools.drlx.annotations.NoLoop";
    private static final String LOCK_ON_ACTIVE_FQN = "org.drools.drlx.annotations.LockOnActive";
    private static final String DISABLED_FQN = "org.drools.drlx.annotations.Disabled";
    private static final String ACTIVATION_GROUP_FQN = "org.drools.drlx.annotations.ActivationGroup";
    private static final String TIMER_FQN = "org.drools.drlx.annotations.Timer";
    private static final String DURATION_FQN = "org.drools.drlx.annotations.Duration";

    private static final Map<String, Kind> SUPPORTED_ANNOTATION_KINDS = Map.ofEntries(
            Map.entry(SALIENCE_FQN, Kind.SALIENCE),
            Map.entry(DESCRIPTION_FQN, Kind.DESCRIPTION),
            Map.entry(DATASOURCE_FQN, Kind.DATASOURCE),
            Map.entry(NO_LOOP_FQN, Kind.NO_LOOP),
            Map.entry(LOCK_ON_ACTIVE_FQN, Kind.LOCK_ON_ACTIVE),
            Map.entry(DISABLED_FQN, Kind.DISABLED),
            Map.entry(ACTIVATION_GROUP_FQN, Kind.ACTIVATION_GROUP),
            Map.entry(TIMER_FQN, Kind.TIMER),
            Map.entry(DURATION_FQN, Kind.DURATION));

    private static final java.util.Set<String> TEMPORAL_OPERATORS = java.util.Set.of(
            "after", "before", "coincides", "during",
            "finishes", "finishedby", "includes",
            "meets", "metby", "overlaps", "overlappedby",
            "starts", "startedby");

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
        List<RuleParameterIR> parameters = List.of();
        if (ctx.ruleParameterList() != null) {
            parameters = ctx.ruleParameterList().ruleParameter().stream()
                    .map(p -> new RuleParameterIR(p.typeType().getText(), p.identifier().getText()))
                    .toList();
        }
        List<RuleAnnotationIR> annotations = buildRuleAnnotations(ctx, annotationImports);
        List<LhsItemIR> lhs = new ArrayList<>();
        ConsequenceIR rhs = null;
        // Fold state: a pending PatternIR and any AccumulatorIRs it has accrued.
        // Adjacent `pattern, accumulateItem(s)` collapse into one AccumulatePatternIR;
        // the fold is inline so no transient LhsItemIR subtype is introduced.
        PatternIR pendingPattern = null;
        List<AccumulatorIR> pendingAccs = new ArrayList<>();
        int inlineCounter = 0;
        if (ctx.ruleBody() != null) {
            for (DrlxParser.RuleItemContext itemCtx : ctx.ruleBody().ruleItem()) {
                if (itemCtx.accumulateItem() != null) {
                    DrlxParser.AccumulateCallContext call = itemCtx.accumulateItem().accumulateCall();
                    if (call.inlineFromOopath() != null) {
                        DrlxParser.InlineFromOopathContext inlineCtx = call.inlineFromOopath();
                        String functionName = call.qualifiedName().getText();
                        DrlxParser.OopathExpressionContext oopathCtx = inlineCtx.oopathExpression();
                        String finalDotIdent = inlineCtx.identifier() != null
                                ? inlineCtx.identifier().getText() : null;

                        if (finalDotIdent != null && !functionName.contains(".")) {
                            AccumulateFunctionRegistry.Resolution resolved =
                                    AccumulateFunctionRegistry.resolve(functionName);
                            if (resolved.acceptsZeroArgs()) {
                                throw new RuntimeException(
                                        "function '" + functionName
                                        + "' does not accept a final-dot extractor in rule '"
                                        + name + "'; use '" + functionName + "("
                                        + getText(oopathCtx) + ")' instead");
                            }
                        }

                        flushPending(lhs, pendingPattern, pendingAccs);
                        pendingPattern = null;
                        pendingAccs = new ArrayList<>();

                        String synthName = "$inline" + inlineCounter++;
                        PatternIR synthSrc = buildPatternFromOopath(oopathCtx, synthName);
                        AccumulatorIR accIr = buildAccumulator(
                                itemCtx.accumulateItem(), synthName, finalDotIdent);

                        lhs.add(new AccumulatePatternIR(synthSrc, List.of(accIr)));
                    } else {
                        if (pendingPattern == null) {
                            throw new RuntimeException(
                                    "accumulate item without a preceding pattern in rule '" + name + "'");
                        }
                        pendingAccs.add(buildAccumulator(itemCtx.accumulateItem()));
                    }
                    continue;
                }
                if (itemCtx.accKeywordItem() != null) {
                    flushPending(lhs, pendingPattern, pendingAccs);
                    pendingPattern = null;
                    pendingAccs = new ArrayList<>();
                    lhs.add(buildAccKeywordItem(itemCtx.accKeywordItem()));
                    continue;
                }
                // Any non-accumulate item flushes the pending pattern (with or without accs).
                flushPending(lhs, pendingPattern, pendingAccs);
                pendingPattern = null;
                pendingAccs = new ArrayList<>();

                if (itemCtx.ruleConsequence() != null) {
                    if (rhs != null) {
                        throw new RuntimeException(
                                "rule '" + name + "' has more than one consequence block");
                    }
                    rhs = new ConsequenceIR(extractConsequence(itemCtx.ruleConsequence()));
                } else if (itemCtx.rulePattern() != null) {
                    pendingPattern = buildPattern(itemCtx.rulePattern());
                } else if (itemCtx.oopathExpression() != null) {
                    lhs.add(buildPatternFromOopath(itemCtx.oopathExpression()));
                } else if (itemCtx.notElement() != null) {
                    lhs.add(buildNotElement(itemCtx.notElement()));
                } else if (itemCtx.existsElement() != null) {
                    lhs.add(buildExistsElement(itemCtx.existsElement()));
                } else if (itemCtx.andElement() != null) {
                    lhs.add(buildAndElement(itemCtx.andElement()));
                } else if (itemCtx.orElement() != null) {
                    lhs.add(buildOrElement(itemCtx.orElement()));
                } else if (itemCtx.testElement() != null) {
                    lhs.add(buildTestElement(itemCtx.testElement()));
                } else if (itemCtx.conditionalBranch() != null) {
                    lhs.add(buildConditionalBranch(itemCtx.conditionalBranch()));
                } else {
                    throw new IllegalArgumentException("Unsupported rule item: " + itemCtx.getText());
                }
            }
            flushPending(lhs, pendingPattern, pendingAccs);
        }
        return new RuleIR(name, annotations, parameters, List.copyOf(lhs), rhs);
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

            if ((kind == Kind.TIMER && seen.contains(Kind.DURATION))
                    || (kind == Kind.DURATION && seen.contains(Kind.TIMER))) {
                throw new RuntimeException(
                        "@Timer and @Duration cannot be used together at " + line + ":" + col);
            }

            String rawValue = extractAnnotationLiteral(annCtx, kind, line, col);
            annotations.add(new RuleAnnotationIR(kind, rawValue));
        }

        return List.copyOf(annotations);
    }

    private static String kindDisplayName(Kind kind) {
        StringBuilder sb = new StringBuilder();
        for (String part : kind.name().split("_")) {
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
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
                    + line + ":" + col + " — supported: @Salience, @Description, @DataSource, "
                    + "@NoLoop, @LockOnActive, @Disabled, "
                    + "@ActivationGroup, @Timer, @Duration");
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
        String displayName = kindDisplayName(kind);
        switch (kind.argShape) {
            case NONE -> {
                if (annCtx.elementValue() != null) {
                    throw new RuntimeException(
                            "@" + displayName + " takes no arguments at " + line + ":" + col);
                }
                return "";
            }
            case INT -> {
                if (annCtx.elementValue() == null) {
                    throw new RuntimeException(
                            "@" + displayName + " expects one argument at " + line + ":" + col);
                }
                String text = annCtx.elementValue().getText();
                try {
                    return String.valueOf(Integer.parseInt(text));
                } catch (NumberFormatException e) {
                    throw new RuntimeException(
                            "@" + displayName + " expects int literal, got '" + text + "' at " + line + ":" + col);
                }
            }
            case STRING -> {
                if (annCtx.elementValue() == null) {
                    throw new RuntimeException(
                            "@" + displayName + " expects one argument at " + line + ":" + col);
                }
                String text = annCtx.elementValue().getText();
                if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    String value = text.substring(1, text.length() - 1);
                    if (value.isEmpty()) {
                        throw new RuntimeException(
                                "@" + displayName + " expects non-empty string literal at " + line + ":" + col);
                    }
                    return value;
                }
                throw new RuntimeException(
                        "@" + displayName + " expects string literal, got '" + text + "' at " + line + ":" + col);
            }
            default -> throw new IllegalStateException("Unhandled arg shape: " + kind.argShape);
        }
    }

    private GroupElementIR buildNotElement(DrlxParser.NotElementContext ctx) {
        if (ctx.oopathExpression() != null) {
            // bare form: not /x
            return new GroupElementIR(GroupElementIR.Kind.NOT,
                    List.of(buildPatternFromOopath(ctx.oopathExpression())));
        }
        // paren form: not ( groupChild, ... )
        return buildGroupElementFromChildren(ctx.groupChild(), GroupElementIR.Kind.NOT);
    }

    private GroupElementIR buildExistsElement(DrlxParser.ExistsElementContext ctx) {
        if (ctx.oopathExpression() != null) {
            return new GroupElementIR(GroupElementIR.Kind.EXISTS,
                    List.of(buildPatternFromOopath(ctx.oopathExpression())));
        }
        return buildGroupElementFromChildren(ctx.groupChild(), GroupElementIR.Kind.EXISTS);
    }

    private GroupElementIR buildAndElement(DrlxParser.AndElementContext ctx) {
        return buildGroupElementFromChildren(ctx.groupChild(), GroupElementIR.Kind.AND);
    }

    private GroupElementIR buildOrElement(DrlxParser.OrElementContext ctx) {
        return buildGroupElementFromChildren(ctx.groupChild(), GroupElementIR.Kind.OR);
    }

    private EvalIR buildTestElement(DrlxParser.TestElementContext ctx) {
        String expression = getText(ctx.expression());
        return new EvalIR(expression, extractIdentifiers(expression));
    }

    private AccumulatorIR buildAccumulator(DrlxParser.AccumulateItemContext ctx) {
        String typeName = ctx.VAR() != null
                ? "var"
                : ctx.typeType().getText();
        String bindName = ctx.identifier().getText();
        DrlxParser.AccumulateCallContext call = ctx.accumulateCall();
        String functionName = call.qualifiedName().getText();
        List<String> args = call.expression() == null || call.expression().isEmpty()
                ? List.of()
                : call.expression().stream()
                      .map(this::getText)
                      .toList();
        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<>();
        for (String a : args) {
            refs.addAll(extractIdentifiers(a));
        }
        return new AccumulatorIR(typeName, bindName, functionName, args, List.copyOf(refs));
    }

    private AccumulatorIR buildAccumulator(DrlxParser.AccumulateItemContext ctx,
                                            String srcBindName,
                                            String finalDotIdent) {
        String typeName = ctx.VAR() != null
                ? "var"
                : ctx.typeType().getText();
        String bindName = ctx.identifier().getText();
        String functionName = ctx.accumulateCall().qualifiedName().getText();
        List<String> args;
        List<String> refs;
        if (finalDotIdent != null) {
            args = List.of(srcBindName + "." + finalDotIdent);
            refs = List.of(srcBindName);
        } else {
            args = List.of();
            refs = List.of();
        }
        return new AccumulatorIR(typeName, bindName, functionName, args, refs);
    }

    private static void flushPending(List<LhsItemIR> lhs,
                                     PatternIR pending,
                                     List<AccumulatorIR> accs) {
        if (pending == null) {
            if (!accs.isEmpty()) {
                throw new IllegalStateException("accumulator collected without a pending pattern");
            }
            return;
        }
        if (accs.isEmpty()) {
            lhs.add(pending);
        } else {
            lhs.add(new AccumulatePatternIR(pending, List.copyOf(accs)));
        }
    }

    private LhsItemIR buildAccKeywordItem(DrlxParser.AccKeywordItemContext ctx) {
        String keyword = ctx.identifier().getText();
        if (!"acc".equals(keyword)) {
            throw new RuntimeException(
                    "expected 'acc' keyword but found '" + keyword + "' at "
                    + ctx.getStart().getLine() + ":" + ctx.getStart().getCharPositionInLine());
        }

        LhsItemIR source;
        if (ctx.accSource().boundOopath() != null) {
            source = buildPatternFromBoundOopath(ctx.accSource().boundOopath());
        } else {
            source = buildGroupElementFromChildren(
                    ctx.accSource().andElement().groupChild(),
                    GroupElementIR.Kind.AND);
        }

        java.util.Set<String> sourceBindNames;
        if (source instanceof PatternIR pat) {
            sourceBindNames = java.util.Set.of(pat.bindName());
        } else if (source instanceof GroupElementIR group) {
            sourceBindNames = group.children().stream()
                    .filter(PatternIR.class::isInstance)
                    .map(c -> ((PatternIR) c).bindName())
                    .collect(java.util.stream.Collectors.toSet());
        } else {
            sourceBindNames = java.util.Set.of();
        }

        DrlxParser.AccBodyContext body = ctx.accBody();

        if (body.accFunctionList() != null) {
            return buildAccKeyword2Param(source, body.accFunctionList());
        }

        List<DrlxParser.AccActionBlockContext> actionBlocks = body.accActionBlock();
        boolean is5Param = actionBlocks.size() == 2;

        List<InitVarIR> initVars = buildInitVars(body.accInitVars(), sourceBindNames);

        String actionBlock;
        String reverseBlock;

        if (is5Param) {
            actionBlock = extractActionBlockText(actionBlocks.get(0), true);
            reverseBlock = extractActionBlockText(actionBlocks.get(1), true);
        } else {
            DrlxParser.AccActionBlockContext actionCtx = actionBlocks.get(0);
            if (actionCtx.expression().size() == 2 && actionCtx.getChild(0).getText().equals("(")) {
                actionBlock = getText(actionCtx.expression(0));
                reverseBlock = getText(actionCtx.expression(1));
            } else {
                actionBlock = extractActionBlockText(actionCtx, false);
                reverseBlock = null;
            }
        }

        DrlxParser.AccResultBindingContext resultCtx = body.accResultBinding();
        String resultTypeName = resultCtx.typeType().getText();
        String resultBindName = resultCtx.identifier().getText();
        String resultExpression = getText(resultCtx.expression());

        if (source instanceof PatternIR pat) {
            validateResultExpression(resultExpression, pat.bindName());
        }

        java.util.LinkedHashSet<String> refs = new java.util.LinkedHashSet<>();
        refs.addAll(extractIdentifiers(actionBlock));
        if (reverseBlock != null) {
            refs.addAll(extractIdentifiers(reverseBlock));
        }
        refs.addAll(extractIdentifiers(resultExpression));

        return new CustomAccumulateIR(source, initVars, actionBlock, reverseBlock,
                resultTypeName, resultBindName, resultExpression, List.copyOf(refs));
    }

    private LhsItemIR buildAccKeyword2Param(LhsItemIR source,
                                             DrlxParser.AccFunctionListContext funcListCtx) {
        List<AccumulatorIR> accumulators = new ArrayList<>();
        for (DrlxParser.AccumulateItemContext accItemCtx : funcListCtx.accumulateItem()) {
            accumulators.add(buildAccumulator(accItemCtx));
        }
        return new AccumulatePatternIR(source, accumulators);
    }

    private List<InitVarIR> buildInitVars(DrlxParser.AccInitVarsContext ctx,
                                           java.util.Set<String> sourceBindNames) {
        List<InitVarIR> result = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.LinkedHashSet<>();

        for (DrlxParser.AccInitVarContext initVarCtx : ctx.accInitVar()) {
            DrlxParser.LocalVariableDeclarationContext localVarCtx = initVarCtx.localVariableDeclaration();

            if (localVarCtx.VAR() != null) {
                throw new RuntimeException(
                        "custom accumulate init vars require explicit types — 'var' is not permitted");
            }

            String typeName = localVarCtx.typeType().getText();
            DrlxParser.VariableDeclaratorsContext declsCtx = localVarCtx.variableDeclarators();

            for (DrlxParser.VariableDeclaratorContext declCtx : declsCtx.variableDeclarator()) {
                String varName = declCtx.variableDeclaratorId().getText();

                if (sourceBindNames.contains(varName)) {
                    throw new RuntimeException(
                            "init var '" + varName + "' conflicts with source binding name");
                }
                if (!seenNames.add(varName)) {
                    throw new RuntimeException(
                            "duplicate init var name '" + varName + "'");
                }

                String initializer;
                if (declCtx.variableInitializer() != null) {
                    initializer = getText(declCtx.variableInitializer());
                    validateLiteralInitializer(initializer, typeName, varName);
                } else {
                    initializer = defaultValueFor(typeName);
                }

                result.add(new InitVarIR(typeName, varName, initializer));
            }
        }
        return result;
    }

    private String extractActionBlockText(DrlxParser.AccActionBlockContext ctx,
                                           boolean rejectPaired) {
        if (ctx.expression().size() == 2 && ctx.getChild(0).getText().equals("(")) {
            if (rejectPaired) {
                throw new RuntimeException(
                        "paired (action, reverse) block is not valid in 5-param acc — use separate action and reverse positions");
            }
            return getText(ctx.expression(0));
        }
        if (ctx.statement() != null && !ctx.statement().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (DrlxParser.StatementContext stCtx : ctx.statement()) {
                sb.append(getText(stCtx));
            }
            return sb.toString().trim();
        }
        return getText(ctx.expression(0));
    }

    private static void validateLiteralInitializer(String initializer, String typeName, String varName) {
        if (initializer.equals("null")) {
            if (isPrimitiveTypeName(typeName)) {
                throw new RuntimeException(
                        "init var '" + varName + "': cannot assign null to primitive type " + typeName);
            }
            return;
        }
        if (initializer.equals("true") || initializer.equals("false")) {
            if (!"boolean".equals(typeName) && !"Boolean".equals(typeName)) {
                throw new RuntimeException(
                        "init var '" + varName + "': cannot assign boolean literal to " + typeName);
            }
            return;
        }
        if (initializer.startsWith("\"") && initializer.endsWith("\"")) {
            if (!"String".equals(typeName) && !"java.lang.String".equals(typeName)) {
                throw new RuntimeException(
                        "init var '" + varName + "': cannot assign String literal to " + typeName);
            }
            return;
        }
        if (initializer.startsWith("'") && initializer.endsWith("'")) {
            if (!"char".equals(typeName) && !"Character".equals(typeName)) {
                throw new RuntimeException(
                        "init var '" + varName + "': cannot assign char literal to " + typeName);
            }
            return;
        }

        if (isNumericLiteral(initializer)) {
            validateNumericLiteralType(initializer, typeName, varName);
            return;
        }

        throw new RuntimeException(
                "custom accumulate init vars must be literals — complex initializers are not yet supported");
    }

    private static boolean isPrimitiveTypeName(String typeName) {
        return switch (typeName) {
            case "int", "long", "double", "float", "short", "byte", "boolean", "char" -> true;
            default -> false;
        };
    }

    private static boolean isNumericLiteral(String value) {
        if (value.isEmpty()) return false;
        String v = value;
        if (v.startsWith("-") || v.startsWith("+")) v = v.substring(1);
        if (v.isEmpty()) return false;
        String lower = v.toLowerCase();
        if (lower.endsWith("l") || lower.endsWith("f") || lower.endsWith("d")) {
            lower = lower.substring(0, lower.length() - 1);
        }
        if (lower.isEmpty()) return false;
        if (lower.contains(".")) {
            try { Double.parseDouble(lower); return true; }
            catch (NumberFormatException e) { return false; }
        }
        try { Long.parseLong(lower); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static void validateNumericLiteralType(String initializer, String typeName, String varName) {
        String lower = initializer.toLowerCase();
        boolean isLong = lower.endsWith("l");
        boolean isFloat = lower.endsWith("f");
        boolean isDouble = lower.endsWith("d") || (!isLong && !isFloat && lower.contains("."));

        switch (typeName) {
            case "int", "Integer" -> {
                if (isLong) throw new RuntimeException("init var '" + varName + "': cannot assign long literal to int");
                if (isFloat || isDouble) throw new RuntimeException("init var '" + varName + "': cannot assign floating-point literal to int");
                try {
                    long val = Long.parseLong(initializer);
                    if (val < Integer.MIN_VALUE || val > Integer.MAX_VALUE) {
                        throw new RuntimeException("init var '" + varName + "': literal value out of range for int");
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException("init var '" + varName + "': invalid numeric literal '" + initializer + "'");
                }
            }
            case "long", "Long" -> {
                if (isFloat || isDouble) throw new RuntimeException("init var '" + varName + "': cannot assign floating-point literal to long");
            }
            case "double", "Double" -> { /* any numeric literal widens to double */ }
            case "float", "Float" -> {
                if (isLong) throw new RuntimeException("init var '" + varName + "': cannot assign long literal to float");
            }
            case "short", "Short" -> {
                if (isLong) throw new RuntimeException("init var '" + varName + "': cannot assign long literal to short");
                if (isFloat || isDouble) throw new RuntimeException("init var '" + varName + "': cannot assign floating-point literal to short");
                try {
                    long val = Long.parseLong(initializer);
                    if (val < Short.MIN_VALUE || val > Short.MAX_VALUE) {
                        throw new RuntimeException("init var '" + varName + "': literal value out of range for short");
                    }
                } catch (NumberFormatException e) { /* non-numeric already filtered */ }
            }
            case "byte", "Byte" -> {
                if (isLong) throw new RuntimeException("init var '" + varName + "': cannot assign long literal to byte");
                if (isFloat || isDouble) throw new RuntimeException("init var '" + varName + "': cannot assign floating-point literal to byte");
                try {
                    long val = Long.parseLong(initializer);
                    if (val < Byte.MIN_VALUE || val > Byte.MAX_VALUE) {
                        throw new RuntimeException("init var '" + varName + "': literal value out of range for byte");
                    }
                } catch (NumberFormatException e) { /* non-numeric already filtered */ }
            }
            case "boolean", "Boolean" -> throw new RuntimeException("init var '" + varName + "': cannot assign numeric literal to boolean");
            case "char", "Character" -> throw new RuntimeException("init var '" + varName + "': cannot assign numeric literal to char");
            default -> { /* reference types accept numeric widening at runtime */ }
        }
    }

    private static void validateResultExpression(String resultExpr, String srcBindName) {
        List<String> ids = extractIdentifiers(resultExpr);
        if (ids.contains(srcBindName)) {
            throw new RuntimeException(
                    "result expression cannot reference source binding '" + srcBindName
                    + "' — only init vars are available");
        }
    }

    private static String defaultValueFor(String typeName) {
        return switch (typeName) {
            case "int", "Integer", "short", "Short", "byte", "Byte" -> "0";
            case "long", "Long" -> "0";
            case "double", "Double" -> "0.0";
            case "float", "Float" -> "0.0";
            case "boolean", "Boolean" -> "false";
            case "char", "Character" -> "' '";
            default -> "null";
        };
    }

    /**
     * Desugar `if (c1) { B1 } else if (c2) { B2 } else { B3 }` to
     * `OR(AND(EvalIR(c1), B1...), AND(EvalIR(!(c1)), EvalIR(c2), B2...),
     *     AND(EvalIR(!(c1)), EvalIR(!(c2)), B3...))`. Cumulative guards
     * split into separate sequential EvalIR nodes; semantically equivalent
     * to a compound `&&` but simpler to construct.
     */
    private GroupElementIR buildConditionalBranch(DrlxParser.ConditionalBranchContext ctx) {
        int conditionCount = ctx.expression().size();
        int bodyCount = ctx.branchBody().size();
        boolean hasFinalElse = bodyCount > conditionCount;

        // Reject empty bodies (matches no useful semantics).
        for (int i = 0; i < bodyCount; i++) {
            if (ctx.branchBody(i).branchItem().isEmpty()) {
                throw new RuntimeException("empty branch body");
            }
        }

        List<LhsItemIR> orChildren = new ArrayList<>();
        List<String> priorConditions = new ArrayList<>();

        for (int i = 0; i < conditionCount; i++) {
            String condition = getText(ctx.expression(i));
            DrlxParser.BranchBodyContext body = ctx.branchBody(i);

            List<LhsItemIR> andChildren = new ArrayList<>();
            for (String prior : priorConditions) {
                String negated = "!(" + prior + ")";
                andChildren.add(new EvalIR(negated, extractIdentifiers(negated)));
            }
            andChildren.add(new EvalIR(condition, extractIdentifiers(condition)));
            for (DrlxParser.BranchItemContext bi : body.branchItem()) {
                andChildren.add(buildBranchItem(bi));
            }
            orChildren.add(new GroupElementIR(GroupElementIR.Kind.AND, List.copyOf(andChildren)));
            priorConditions.add(condition);
        }

        if (hasFinalElse) {
            DrlxParser.BranchBodyContext elseBody = ctx.branchBody(bodyCount - 1);
            List<LhsItemIR> andChildren = new ArrayList<>();
            for (String prior : priorConditions) {
                String negated = "!(" + prior + ")";
                andChildren.add(new EvalIR(negated, extractIdentifiers(negated)));
            }
            for (DrlxParser.BranchItemContext bi : elseBody.branchItem()) {
                andChildren.add(buildBranchItem(bi));
            }
            orChildren.add(new GroupElementIR(GroupElementIR.Kind.AND, List.copyOf(andChildren)));
        }

        return new GroupElementIR(GroupElementIR.Kind.OR, List.copyOf(orChildren));
    }

    private LhsItemIR buildBranchItem(DrlxParser.BranchItemContext ctx) {
        if (ctx.boundOopath() != null)        return buildPatternFromBoundOopath(ctx.boundOopath());
        if (ctx.notElement() != null)         return buildNotElement(ctx.notElement());
        if (ctx.existsElement() != null)      return buildExistsElement(ctx.existsElement());
        if (ctx.andElement() != null)         return buildAndElement(ctx.andElement());
        if (ctx.orElement() != null)          return buildOrElement(ctx.orElement());
        if (ctx.testElement() != null)        return buildTestElement(ctx.testElement());
        if (ctx.conditionalBranch() != null)  return buildConditionalBranch(ctx.conditionalBranch());
        throw new IllegalArgumentException("Unsupported branch item: " + ctx.getText());
    }

    /**
     * Extract candidate binding identifiers from an expression text via word-boundary regex.
     * Over-collects (Java keywords, type names) but that's harmless — the runtime builder
     * filters against the live boundVariables map and silently drops names that don't resolve.
     */
    private static List<String> extractIdentifiers(String expression) {
        java.util.regex.Pattern idRegex =
                java.util.regex.Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        java.util.regex.Matcher m = idRegex.matcher(expression);
        java.util.LinkedHashSet<String> identifiers = new java.util.LinkedHashSet<>();
        while (m.find()) identifiers.add(m.group(1));
        return List.copyOf(identifiers);
    }

    private GroupElementIR buildGroupElementFromChildren(
            List<DrlxParser.GroupChildContext> childCtxs,
            GroupElementIR.Kind kind) {
        List<LhsItemIR> children = new ArrayList<>();
        for (DrlxParser.GroupChildContext c : childCtxs) {
            children.add(buildGroupChild(c));
        }
        return new GroupElementIR(kind, List.copyOf(children));
    }

    private LhsItemIR buildGroupChild(DrlxParser.GroupChildContext c) {
        if (c.boundOopath() != null)      return buildPatternFromBoundOopath(c.boundOopath());
        if (c.oopathExpression() != null) return buildPatternFromOopath(c.oopathExpression());
        if (c.notElement() != null)       return buildNotElement(c.notElement());
        if (c.existsElement() != null)    return buildExistsElement(c.existsElement());
        if (c.andElement() != null)       return buildAndElement(c.andElement());
        if (c.orElement() != null)        return buildOrElement(c.orElement());
        throw new IllegalArgumentException("Unsupported group child: " + c.getText());
    }

    private PatternIR buildPatternFromBoundOopath(DrlxParser.BoundOopathContext ctx) {
        String typeName = ctx.identifier(0).getText();
        String bindName = ctx.identifier(1).getText();
        DrlxParser.OopathExpressionContext oopathCtx = ctx.oopathExpression();
        String entryPoint = extractEntryPointFromOopathCtx(oopathCtx);
        String castTypeName = extractCastType(oopathCtx);
        List<String> conditions = extractConditions(oopathCtx);
        List<TemporalConditionIR> temporalConditions = extractTemporalConditions(oopathCtx);
        List<String> positionalArgs = extractPositionalArgs(oopathCtx);
        boolean passive = ctx.oopathExpression().QUESTION() != null;
        List<String> watchedProperties = extractWatchedProperties(ctx.oopathExpression());
        String windowType = null;
        String windowParameter = null;
        if (ctx.windowFilter() != null) {
            windowType = ctx.windowFilter().identifier().getText();
            if (!"length".equals(windowType) && !"time".equals(windowType)) {
                throw new IllegalArgumentException("Unknown window type: " + windowType
                        + ". Expected 'length' or 'time'.");
            }
            windowParameter = ctx.windowFilter().windowParam().getText();
        }
        return new PatternIR(typeName, bindName, entryPoint, conditions, temporalConditions,
                              castTypeName, positionalArgs, passive, watchedProperties,
                              windowType, windowParameter);
    }

    private PatternIR buildPatternFromOopath(DrlxParser.OopathExpressionContext oopathCtx) {
        String entryPoint = extractEntryPointFromOopathCtx(oopathCtx);
        String castTypeName = extractCastType(oopathCtx);
        List<String> conditions = extractConditions(oopathCtx);
        List<String> positionalArgs = extractPositionalArgs(oopathCtx);
        boolean passive = oopathCtx.QUESTION() != null;
        List<String> watchedProperties = extractWatchedProperties(oopathCtx);
        return new PatternIR("", "", entryPoint, conditions, List.of(), castTypeName, positionalArgs, passive, watchedProperties, null, null);
    }

    private PatternIR buildPatternFromOopath(DrlxParser.OopathExpressionContext oopathCtx,
                                              String syntheticBindName) {
        String entryPoint = extractEntryPointFromOopathCtx(oopathCtx);
        String castTypeName = extractCastType(oopathCtx);
        List<String> conditions = extractConditions(oopathCtx);
        List<String> positionalArgs = extractPositionalArgs(oopathCtx);
        boolean passive = oopathCtx.QUESTION() != null;
        List<String> watchedProperties = extractWatchedProperties(oopathCtx);
        return new PatternIR("", syntheticBindName, entryPoint, conditions, List.of(), castTypeName,
                              positionalArgs, passive, watchedProperties, null, null);
    }

    private PatternIR buildPattern(DrlxParser.RulePatternContext ctx) {
        return buildPatternFromBoundOopath(ctx.boundOopath());
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

    private List<String> extractPositionalArgs(DrlxParser.OopathExpressionContext ctx) {
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null || root.positionalArg() == null || root.positionalArg().isEmpty()) {
            return List.of();
        }
        return root.positionalArg().stream()
                .map(arg -> {
                    if (arg.VAR() != null) {
                        return "var " + arg.identifier().getText();
                    }
                    return getText(arg.expression());
                })
                .toList();
    }

    private static List<String> extractWatchedProperties(DrlxParser.OopathExpressionContext ctx) {
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null || root.watchItem() == null || root.watchItem().isEmpty()) {
            return List.of();
        }
        return root.watchItem().stream()
                .map(ParserRuleContext::getText)
                .toList();
    }

    private List<String> extractConditions(DrlxParser.OopathExpressionContext ctx) {
        return collectDrlxExpressions(ctx).stream()
                .filter(de -> de.customConstraint() == null)
                .map(this::getText)
                .toList();
    }

    private List<TemporalConditionIR> extractTemporalConditions(DrlxParser.OopathExpressionContext ctx) {
        List<TemporalConditionIR> result = new ArrayList<>();
        for (var de : collectDrlxExpressions(ctx)) {
            if (de.customConstraint() != null) {
                result.add(buildTemporalCondition(de.customConstraint()));
            }
        }
        return List.copyOf(result);
    }

    private List<DrlxParser.DrlxExpressionContext> collectDrlxExpressions(
            DrlxParser.OopathExpressionContext ctx) {
        List<DrlxParser.OopathChunkContext> chunks = ctx.oopathChunk();
        if (!chunks.isEmpty()) {
            return chunks.get(chunks.size() - 1).drlxExpression();
        }
        DrlxParser.OopathRootContext root = ctx.oopathRoot();
        if (root == null) {
            return List.of();
        }
        return root.drlxExpression();
    }

    private TemporalConditionIR buildTemporalCondition(DrlxParser.CustomConstraintContext ctx) {
        String operatorName = ctx.customOp().identifier().getText();
        if (!TEMPORAL_OPERATORS.contains(operatorName)) {
            throw new IllegalArgumentException(
                    "Unknown custom operator '" + operatorName
                    + "'. Supported temporal operators: " + TEMPORAL_OPERATORS);
        }
        boolean negated = ctx.NOT() != null;
        List<String> params = List.of();
        if (ctx.customOp().customOpParams() != null) {
            String raw = getText(ctx.customOp().customOpParams());
            params = java.util.Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        String rightBinding = getText(ctx.expression());
        return new TemporalConditionIR(operatorName, negated, params, rightBinding);
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
