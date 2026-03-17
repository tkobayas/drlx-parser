package org.drools.drlx.builder;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.definitions.impl.KnowledgePackageImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElementFactory;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.Constraint;
import org.drools.drlx.parser.DrlxParser;
import org.drools.drlx.parser.DrlxParserBaseVisitor;
import org.drools.util.TypeResolver;
import org.kie.api.definition.KiePackage;
import org.mvel3.ClassManager;
import org.mvel3.CompilerParameters;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;
import org.mvel3.MVELCompiler;
import org.mvel3.Type;
import org.mvel3.javacompiler.KieMemoryCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visitor that walks a DRLX parse tree and directly builds RuleImpl/KiePackage,
 * skipping the intermediate Descr generation step.
 * Returns List&lt;KiePackage&gt; from the top-level visit.
 */
public class DrlxToRuleImplVisitor extends DrlxParserBaseVisitor<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxToRuleImplVisitor.class);

    private final TokenStream tokens;

    private int patternId = 0;

    private DrlxLambdaMetadata preBuildMetadata; // null = normal build

    protected String currentRuleName;

    protected int lambdaCounter;

    // Batch compilation fields
    protected boolean batchMode = false;
    protected ClassManager sharedClassManager;
    protected final Map<String, String> pendingSources = new LinkedHashMap<>();
    protected final List<PendingLambda> pendingLambdas = new ArrayList<>();
    protected int batchCounter = 0;

    // Shared ClassManager for loading pre-compiled evaluators (avoids creating a new ClassLoader per lambda)
    private ClassManager preBuildClassManager;

    // Cache: transpiled Java source -> fqn (for batch dedup of identical lambdas)
    private final Map<String, String> sourceToFqn = new HashMap<>();

    // Cache: fqn -> loaded Class (avoids redundant ClassManager.define() calls on the load path)
    private final Map<String, Class<?>> loadedClassCache = new HashMap<>();

    record PendingLambda(String fqn, Object target) {}

    private static final ConcurrentHashMap<Class<?>, org.mvel3.transpiler.context.Declaration<?>[]> DECLARATION_CACHE = new ConcurrentHashMap<>();

    static org.mvel3.transpiler.context.Declaration<?>[] extractDeclarations(Class<?> patternType) {
        return DECLARATION_CACHE.computeIfAbsent(patternType, clz -> {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(clz, Object.class);
                org.mvel3.transpiler.context.Declaration<?>[] declarations = Arrays.stream(beanInfo.getPropertyDescriptors())
                        .filter(pd -> pd.getReadMethod() != null)
                        .map(pd -> org.mvel3.transpiler.context.Declaration.of(pd.getName(), pd.getReadMethod().getReturnType()))
                        .toArray(org.mvel3.transpiler.context.Declaration[]::new);
                if (declarations.length == 0) {
                    LOG.warn("No JavaBean properties found for type {}", clz.getName());
                }
                return declarations;
            } catch (IntrospectionException e) {
                throw new RuntimeException("Failed to introspect " + clz.getName(), e);
            }
        });
    }

    public DrlxToRuleImplVisitor() {
        this(null);
    }

    public DrlxToRuleImplVisitor(TokenStream tokens) {
        this.tokens = tokens;
    }

    public void setPreBuildMetadata(DrlxLambdaMetadata preBuildMetadata) {
        this.preBuildMetadata = preBuildMetadata;
    }

    public void enableBatchMode(ClassManager classManager) {
        this.batchMode = true;
        this.sharedClassManager = classManager;
    }

    public void compileBatch(ClassLoader classLoader) {
        if (pendingSources.isEmpty()) {
            return;
        }
        LOG.info("Batch-compiling {} lambda sources", pendingSources.size());
        KieMemoryCompiler.compile(sharedClassManager, pendingSources, classLoader);
        for (PendingLambda pl : pendingLambdas) {
            if (pl.target() instanceof DrlxLambdaConstraint c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            } else if (pl.target() instanceof DrlxLambdaBetaConstraint c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            } else if (pl.target() instanceof DrlxLambdaConsequence c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            }
        }
        pendingSources.clear();
        pendingLambdas.clear();
    }

    @Override
    public List<KiePackage> visitDrlxCompilationUnit(DrlxParser.DrlxCompilationUnitContext ctx) {
        // package name
        String packageName = "";
        if (ctx.packageDeclaration() != null) {
            packageName = ctx.packageDeclaration().qualifiedName().getText();
        }

        // create package
        KnowledgePackageImpl pkg = new KnowledgePackageImpl(packageName);
        pkg.setClassLoader(Thread.currentThread().getContextClassLoader());

        // imports
        if (ctx.importDeclaration() != null) {
            ctx.importDeclaration().forEach(importCtx -> {
                String target = importCtx.qualifiedName().getText();
                if (importCtx.MUL() != null) {
                    target = target + ".*";
                }
                pkg.addImport(new ImportDeclaration(target));
            });
        }

        // rules
        if (ctx.ruleDeclaration() != null) {
            ctx.ruleDeclaration().forEach(ruleCtx -> {
                RuleImpl rule = buildRule(ruleCtx, pkg.getTypeResolver());
                pkg.addRule(rule);
            });
        }

        List<KiePackage> kiePackages = new ArrayList<>();
        kiePackages.add(pkg);
        return kiePackages;
    }

    // Tracks bound variables from previously-built patterns within the current rule
    record BoundVariable(String name, Class<?> type, Pattern pattern) {}

    protected RuleImpl buildRule(DrlxParser.RuleDeclarationContext ctx, TypeResolver typeResolver) {
        String ruleName = ctx.identifier().getText();
        currentRuleName = ruleName;
        lambdaCounter = 0;

        RuleImpl rule = new RuleImpl(ruleName);
        rule.setResource(rule.getResource());

        GroupElement ge = GroupElementFactory.newAndInstance();

        // Track bound variables as patterns are built
        Map<String, BoundVariable> boundVariables = new LinkedHashMap<>();

        if (ctx.ruleBody() != null) {
            ctx.ruleBody().ruleItem().forEach(item -> {
                if (item.rulePattern() != null) {
                    Pattern pattern = buildPattern(item.rulePattern(), typeResolver, boundVariables);
                    ge.addChild(pattern);
                    // Register this pattern's binding for subsequent patterns
                    Declaration decl = pattern.getDeclaration();
                    if (decl != null) {
                        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
                        boundVariables.put(decl.getIdentifier(), new BoundVariable(decl.getIdentifier(), patternClass, pattern));
                    }
                } else if (item.ruleConsequence() != null) {
                    String consequence = extractConsequence(item.ruleConsequence());
                    Map<String, Type<?>> types = getTypeMap(ge);
                    rule.setConsequence(createLambdaConsequence(consequence, types));
                }
            });
        }

        rule.setLhs(ge);

        return rule;
    }

    protected Pattern buildPattern(DrlxParser.RulePatternContext ctx, TypeResolver typeResolver, Map<String, BoundVariable> boundVariables) {
        String typeName = ctx.identifier(0).getText();
        String bindName = ctx.identifier(1).getText();

        // resolve type
        ObjectType objectType;
        try {
            Class<?> type = typeResolver.resolveType(typeName);
            objectType = new ClassObjectType(type, false);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Pattern pattern = new Pattern(patternId++, 0, 0, objectType, bindName, false);

        // entry point from oopath
        String entryPointText = extractEntryPointFromOopath(getText(ctx.oopathExpression()));
        pattern.setSource(new EntryPointId(entryPointText));

        // constraints from oopath
        Class<?> patternClass = ((ClassObjectType) pattern.getObjectType()).getClassType();
        org.mvel3.transpiler.context.Declaration<?>[] declarations = extractDeclarations(patternClass);
        List<String> conditions = extractConditions(ctx.oopathExpression());
        for (String expression : conditions) {
            // Check if expression references any bound variable from previous patterns
            List<BoundVariable> referencedBindings = findReferencedBindings(expression, boundVariables);
            if (referencedBindings.isEmpty()) {
                Constraint constraint = createLambdaConstraint(expression, patternClass, declarations);
                pattern.addConstraint(constraint);
            } else {
                Constraint constraint = createBetaLambdaConstraint(expression, patternClass, declarations, referencedBindings);
                pattern.addConstraint(constraint);
            }
        }

        return pattern;
    }

    /**
     * Find bound variables from previous patterns that are referenced in the expression.
     * Uses word-boundary matching to avoid false positives.
     */
    private List<BoundVariable> findReferencedBindings(String expression, Map<String, BoundVariable> boundVariables) {
        List<BoundVariable> referenced = new ArrayList<>();
        for (Map.Entry<String, BoundVariable> entry : boundVariables.entrySet()) {
            String varName = entry.getKey();
            // Word-boundary check: varName must not be preceded/followed by identifier characters
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                    "(?<![a-zA-Z0-9_])" + java.util.regex.Pattern.quote(varName) + "(?![a-zA-Z0-9_])");
            if (regex.matcher(expression).find()) {
                referenced.add(entry.getValue());
            }
        }
        return referenced;
    }

    protected DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        int capturedCounter = lambdaCounter++;
        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(expression)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Object, Void, Boolean> evaluator = (Evaluator<Object, Void, Boolean>) loadPreCompiledEvaluator(entry.fqn(), entry.classFilePath());
                    LOG.info("Loaded pre-compiled constraint evaluator for {}.{}", currentRuleName, capturedCounter);
                    return new DrlxLambdaConstraint(expression, patternType, evaluator);
                } catch (Exception e) {
                    LOG.warn("Failed to load pre-compiled constraint for {}.{}, falling back to compilation", currentRuleName, capturedCounter, e);
                }
            } else if (entry == null) {
                LOG.warn("No pre-built metadata for {}.{}, falling back to compilation", currentRuleName, capturedCounter);
            } else {
                LOG.warn("Expression mismatch for {}.{}: expected '{}' but found '{}', falling back to compilation",
                        currentRuleName, capturedCounter, expression, entry.expression());
            }
        }
        if (batchMode) {
            return createBatchConstraint(expression, patternType, declarations);
        }
        return new DrlxLambdaConstraint(expression, patternType, declarations);
    }

    protected Constraint createBetaLambdaConstraint(String expression, Class<?> patternType,
                                                       org.mvel3.transpiler.context.Declaration<?>[] patternDeclarations,
                                                       List<BoundVariable> referencedBindings) {
        int capturedCounter = lambdaCounter++;

        // Build MVEL declarations: current pattern's properties + external binding objects
        List<org.mvel3.transpiler.context.Declaration<?>> allDecls = new ArrayList<>(Arrays.asList(patternDeclarations));
        for (BoundVariable bv : referencedBindings) {
            allDecls.add(org.mvel3.transpiler.context.Declaration.of(bv.name(), bv.type()));
        }
        org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations = allDecls.toArray(new org.mvel3.transpiler.context.Declaration[0]);

        // Build Drools required declarations from the referenced patterns
        Declaration[] requiredDeclarations = referencedBindings.stream()
                .map(bv -> bv.pattern().getDeclaration())
                .toArray(Declaration[]::new);

        // Check pre-build metadata first
        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(expression)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Map<String, Object>, Void, Boolean> evaluator =
                            (Evaluator<Map<String, Object>, Void, Boolean>) loadPreCompiledEvaluator(entry.fqn(), entry.classFilePath());
                    LOG.info("Loaded pre-compiled beta constraint evaluator for {}.{}", currentRuleName, capturedCounter);
                    return new DrlxLambdaBetaConstraint(expression, patternType, evaluator, requiredDeclarations);
                } catch (Exception e) {
                    LOG.warn("Failed to load pre-compiled beta constraint for {}.{}, falling back to compilation", currentRuleName, capturedCounter, e);
                }
            } else if (entry == null) {
                LOG.warn("No pre-built metadata for {}.{}, falling back to compilation", currentRuleName, capturedCounter);
            } else {
                LOG.warn("Expression mismatch for {}.{}: expected '{}' but found '{}', falling back to compilation",
                        currentRuleName, capturedCounter, expression, entry.expression());
            }
        }

        if (batchMode) {
            return createBatchBetaConstraint(expression, patternType, mvelDeclarations, requiredDeclarations);
        }
        return new DrlxLambdaBetaConstraint(expression, patternType, mvelDeclarations, requiredDeclarations);
    }

    private DrlxLambdaConstraint createBatchConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        CompilerParameters<Object, Void, Boolean> evalInfo = MVEL.pojo(patternType,
                        declarations[0], Arrays.copyOfRange(declarations, 1, declarations.length))
                .<Boolean>out(Boolean.class)
                .expression(expression)
                .classManager(sharedClassManager)
                .generatedClassName("GeneratorEvaluator__" + batchCounter++)
                .build();
        MVELCompiler.TranspiledSource ts = new MVELCompiler().transpileToSource(evalInfo);
        String fqn = deduplicateSource(ts);
        DrlxLambdaConstraint constraint = new DrlxLambdaConstraint(expression, patternType, (Evaluator<Object, Void, Boolean>) null);
        pendingLambdas.add(new PendingLambda(fqn, constraint));
        return constraint;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private DrlxLambdaBetaConstraint createBatchBetaConstraint(String expression, Class<?> patternType,
                                                                  org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations,
                                                                  Declaration[] requiredDeclarations) {
        CompilerParameters<Map<String, Object>, Void, Boolean> evalInfo =
                (CompilerParameters) MVEL.<Object>map(mvelDeclarations)
                        .<Boolean>out(Boolean.class)
                        .expression(expression)
                        .classManager(sharedClassManager)
                        .generatedClassName("GeneratorEvaluator__" + batchCounter++)
                        .build();
        MVELCompiler.TranspiledSource ts = new MVELCompiler().transpileToSource(evalInfo);
        String fqn = deduplicateSource(ts);
        DrlxLambdaBetaConstraint constraint = new DrlxLambdaBetaConstraint(expression, patternType,
                (Evaluator<Map<String, Object>, Void, Boolean>) null, requiredDeclarations);
        pendingLambdas.add(new PendingLambda(fqn, constraint));
        return constraint;
    }

    protected DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int capturedCounter = lambdaCounter++;
        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(consequenceBlock)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Map<String, Object>, Void, String> evaluator = (Evaluator<Map<String, Object>, Void, String>) loadPreCompiledEvaluator(entry.fqn(), entry.classFilePath());
                    LOG.info("Loaded pre-compiled consequence evaluator for {}.{}", currentRuleName, capturedCounter);
                    return new DrlxLambdaConsequence(consequenceBlock, declarationTypes, evaluator);
                } catch (Exception e) {
                    LOG.warn("Failed to load pre-compiled consequence for {}.{}, falling back to compilation", currentRuleName, capturedCounter, e);
                }
            } else if (entry == null) {
                LOG.warn("No pre-built metadata for {}.{}, falling back to compilation", currentRuleName, capturedCounter);
            } else {
                LOG.warn("Expression mismatch for {}.{}: expected '{}' but found '{}', falling back to compilation",
                        currentRuleName, capturedCounter, consequenceBlock, entry.expression());
            }
        }
        if (batchMode) {
            return createBatchConsequence(consequenceBlock, declarationTypes);
        }
        return new DrlxLambdaConsequence(consequenceBlock, declarationTypes);
    }

    private static final String RETURN_NULL = "\n return null;";

    private DrlxLambdaConsequence createBatchConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        // Build CompilerParameters with unique class name (same logic as DrlxLambdaConsequence.initializeLambdaConsequence)
        @SuppressWarnings({"unchecked", "rawtypes"})
        CompilerParameters<Map<String, Object>, Void, String> evalInfo =
                (CompilerParameters) MVEL.<Object>map(org.mvel3.transpiler.context.Declaration.from(declarationTypes))
                        .<String>out(String.class)
                        .block(consequenceBlock + RETURN_NULL)
                        .imports(new HashSet<>())
                        .classManager(sharedClassManager)
                        .generatedClassName("GeneratorEvaluator__" + batchCounter++)
                        .build();
        MVELCompiler.TranspiledSource ts = new MVELCompiler().transpileToSource(evalInfo);
        String fqn = deduplicateSource(ts);
        DrlxLambdaConsequence consequence = new DrlxLambdaConsequence(consequenceBlock, declarationTypes, (Evaluator<Map<String, Object>, Void, String>) null);
        pendingLambdas.add(new PendingLambda(fqn, consequence));
        return consequence;
    }

    /**
     * Deduplicate transpiled sources: if an identical Java source was already transpiled,
     * reuse the existing fqn instead of adding a duplicate to the batch.
     * The class name is stripped from the source before comparison, since identical expressions
     * get different generated class names (e.g., GeneratorEvaluator__1 vs GeneratorEvaluator__4).
     */
    private String deduplicateSource(MVELCompiler.TranspiledSource ts) {
        String normalizedSource = normalizeSource(ts.javaSource());
        String existingFqn = sourceToFqn.get(normalizedSource);
        if (existingFqn != null) {
            LOG.debug("Dedup: reusing {} for identical source (skipped {})", existingFqn, ts.fqn());
            return existingFqn;
        }
        sourceToFqn.put(normalizedSource, ts.fqn());
        pendingSources.put(ts.fqn(), ts.javaSource());
        return ts.fqn();
    }

    /**
     * Strip the generated class name from the source so that identical method bodies
     * with different class names (e.g., GeneratorEvaluator__0 vs GeneratorEvaluator__3) compare equal.
     */
    private static String normalizeSource(String javaSource) {
        return javaSource.replaceFirst("class\\s+\\S+\\s+implements", "class __ implements");
    }

    private Object loadPreCompiledEvaluator(String fqn, String classFilePath) throws Exception {
        Class<?> clazz = loadedClassCache.get(fqn);
        if (clazz == null) {
            byte[] bytes = Files.readAllBytes(Path.of(classFilePath));
            if (preBuildClassManager == null) {
                preBuildClassManager = new ClassManager();
            }
            preBuildClassManager.define(Collections.singletonMap(fqn, bytes));
            clazz = preBuildClassManager.getClass(fqn);
            loadedClassCache.put(fqn, clazz);
        }
        return clazz.getConstructor().newInstance();
    }

    private String extractConsequence(DrlxParser.RuleConsequenceContext ctx) {
        String statementText = getText(ctx.statement());
        return trimBraces(statementText);
    }

    private String extractEntryPointFromOopath(String oopath) {
        String result = oopath;
        if (result.startsWith("/")) {
            result = result.substring(1);
        }
        int bracketIndex = result.indexOf('[');
        if (bracketIndex >= 0) {
            result = result.substring(0, bracketIndex);
        }
        return result;
    }

    private List<String> extractConditions(DrlxParser.OopathExpressionContext ctx) {
        List<DrlxParser.OopathChunkContext> oopathChunkContexts = ctx.oopathChunk();
        if (oopathChunkContexts.isEmpty()) {
            return List.of();
        }
        DrlxParser.OopathChunkContext lastOopathChunkContext = oopathChunkContexts.get(oopathChunkContexts.size() - 1);
        return lastOopathChunkContext.drlxExpression().stream()
                .map(this::getText)
                .collect(Collectors.toList());
    }

    private Map<String, Type<?>> getTypeMap(GroupElement ge) {
        Map<String, Type<?>> types = new HashMap<>();
        ge.getChildren().stream().filter(element -> element instanceof Pattern).forEach(pattern -> {
            Pattern p = (Pattern) pattern;
            Class<?> patternClass = ((ClassObjectType) p.getObjectType()).getClassType();
            Declaration declaration = p.getDeclaration();
            types.put(declaration.getIdentifier(), Type.type(patternClass));
        });
        return types;
    }

    private String trimBraces(String text) {
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
