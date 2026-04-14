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

import org.drools.base.base.ClassObjectType;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.Constraint;
import org.mvel3.ClassManager;
import org.mvel3.CompilerParameters;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;
import org.mvel3.MVELBatchCompiler;
import org.mvel3.Type;
import org.mvel3.lambdaextractor.LambdaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the lambda-compilation concern separated from ANTLR visiting.
 * Builds {@link DrlxLambdaConstraint}, {@link DrlxLambdaBetaConstraint},
 * and {@link DrlxLambdaConsequence} instances, with support for batch
 * compilation and pre-built metadata reuse.
 *
 * <p>When pre-built metadata is provided but an entry is missing, stale, or
 * the class file can't be loaded, behavior is controlled by
 * {@link DrlxMetadataMismatchMode} — fail-fast by default.
 */
public class DrlxLambdaCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxLambdaCompiler.class);

    private static final String RETURN_NULL = "\n return null;";

    private static final ConcurrentHashMap<Class<?>, org.mvel3.transpiler.context.Declaration<?>[]> DECLARATION_CACHE = new ConcurrentHashMap<>();

    public static org.mvel3.transpiler.context.Declaration<?>[] extractDeclarations(Class<?> patternType) {
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

    public record BoundVariable(String name, Class<?> type, Pattern pattern) {}

    public record PendingLambda(MVELBatchCompiler.LambdaHandle handle, EvaluatorSink target) {}

    protected int patternId = 0;

    protected DrlxLambdaMetadata preBuildMetadata; // null = normal build

    protected String currentRuleName;
    protected int lambdaCounter;

    protected final MVELBatchCompiler batchCompiler;
    protected final List<PendingLambda> pendingLambdas = new ArrayList<>();

    private ClassManager preBuildClassManager;
    private final Map<String, Class<?>> loadedClassCache = new HashMap<>();

    public DrlxLambdaCompiler(MVELBatchCompiler batchCompiler) {
        this.batchCompiler = batchCompiler;
    }

    public void setPreBuildMetadata(DrlxLambdaMetadata preBuildMetadata) {
        this.preBuildMetadata = preBuildMetadata;
    }

    public int nextPatternId() {
        return patternId++;
    }

    /** Reset per-rule state. Must be called at the start of each rule. */
    public void beginRule(String ruleName) {
        this.currentRuleName = ruleName;
        this.lambdaCounter = 0;
    }

    public DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        int counter = lambdaCounter++;
        @SuppressWarnings("unchecked")
        Evaluator<Object, Void, Boolean> preCompiled = (Evaluator<Object, Void, Boolean>) tryLoadPreCompiled(counter, expression, "constraint");
        if (preCompiled != null) {
            return new DrlxLambdaConstraint(expression, patternType, preCompiled);
        }
        DrlxLambdaConstraint constraint = createBatchConstraint(expression, patternType, declarations);
        onLambdaCreated(counter, expression);
        return constraint;
    }

    public Constraint createBetaLambdaConstraint(String expression, Class<?> patternType,
                                                 org.mvel3.transpiler.context.Declaration<?>[] patternDeclarations,
                                                 List<BoundVariable> referencedBindings) {
        int counter = lambdaCounter++;

        List<org.mvel3.transpiler.context.Declaration<?>> allDecls = new ArrayList<>(Arrays.asList(patternDeclarations));
        for (BoundVariable bv : referencedBindings) {
            allDecls.add(org.mvel3.transpiler.context.Declaration.of(bv.name(), bv.type()));
        }
        org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations = allDecls.toArray(new org.mvel3.transpiler.context.Declaration[0]);

        Declaration[] requiredDeclarations = referencedBindings.stream()
                .map(bv -> bv.pattern().getDeclaration())
                .toArray(Declaration[]::new);

        @SuppressWarnings("unchecked")
        Evaluator<Map<String, Object>, Void, Boolean> preCompiled =
                (Evaluator<Map<String, Object>, Void, Boolean>) tryLoadPreCompiled(counter, expression, "beta constraint");
        if (preCompiled != null) {
            return new DrlxLambdaBetaConstraint(expression, patternType, preCompiled, requiredDeclarations);
        }
        DrlxLambdaBetaConstraint constraint = createBatchBetaConstraint(expression, patternType, mvelDeclarations, requiredDeclarations);
        onLambdaCreated(counter, expression);
        return constraint;
    }

    public DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int counter = lambdaCounter++;
        @SuppressWarnings("unchecked")
        Evaluator<Map<String, Object>, Void, String> preCompiled =
                (Evaluator<Map<String, Object>, Void, String>) tryLoadPreCompiled(counter, consequenceBlock, "consequence");
        if (preCompiled != null) {
            return new DrlxLambdaConsequence(consequenceBlock, declarationTypes, preCompiled);
        }
        DrlxLambdaConsequence consequence = createBatchConsequence(consequenceBlock, declarationTypes);
        onLambdaCreated(counter, consequenceBlock);
        return consequence;
    }

    public void compileBatch(ClassLoader classLoader) {
        if (pendingLambdas.isEmpty()) {
            return;
        }
        batchCompiler.compile(classLoader);
        for (PendingLambda pl : pendingLambdas) {
            pl.target().bindEvaluator(batchCompiler.resolve(pl.handle()));
        }
        pendingLambdas.clear();
    }

    /**
     * Hook called after a new lambda has been added to {@link #pendingLambdas}
     * on the batch-compilation path. Subclasses ({@link DrlxPreBuildLambdaCompiler})
     * override this to record metadata; the default is a no-op.
     */
    protected void onLambdaCreated(int counter, String expression) {
    }

    /**
     * Try to resolve a pre-compiled evaluator from {@link #preBuildMetadata}.
     * Returns {@code null} when no metadata is attached, or when the lookup misses
     * and {@link DrlxMetadataMismatchMode#current()} is {@link DrlxMetadataMismatchMode#FALLBACK}.
     * Throws {@link IllegalStateException} on miss/mismatch/load-failure when the
     * mode is {@link DrlxMetadataMismatchMode#FAIL_FAST} (the default).
     */
    private Object tryLoadPreCompiled(int counter, String expression, String kind) {
        if (preBuildMetadata == null) {
            return null;
        }
        DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, counter);
        if (entry == null) {
            return handleMetadataMismatch(counter, kind,
                    "No pre-built metadata for " + currentRuleName + "." + counter, null);
        }
        if (!entry.expression().equals(expression)) {
            return handleMetadataMismatch(counter, kind,
                    "Expression mismatch for " + currentRuleName + "." + counter
                            + ": expected '" + expression + "' but found '" + entry.expression() + "'",
                    null);
        }
        try {
            Object evaluator = loadPreCompiledEvaluator(entry.fqn(), entry.physicalId());
            LOG.info("Loaded pre-compiled {} evaluator for {}.{}", kind, currentRuleName, counter);
            return evaluator;
        } catch (Exception e) {
            return handleMetadataMismatch(counter, kind,
                    "Failed to load pre-compiled " + kind + " for " + currentRuleName + "." + counter, e);
        }
    }

    private Object handleMetadataMismatch(int counter, String kind, String message, Exception cause) {
        switch (DrlxMetadataMismatchMode.current()) {
            case FAIL_FAST -> throw new IllegalStateException(message, cause);
            case FALLBACK -> {
                if (cause != null) {
                    LOG.warn("{}, falling back to compilation", message, cause);
                } else {
                    LOG.warn("{}, falling back to compilation", message);
                }
                return null;
            }
        }
        throw new IllegalStateException("Unhandled DrlxMetadataMismatchMode"); // unreachable
    }

    private DrlxLambdaConstraint createBatchConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        CompilerParameters<Object, Void, Boolean> evalInfo = MVEL.pojo(patternType,
                        declarations[0], Arrays.copyOfRange(declarations, 1, declarations.length))
                .<Boolean>out(Boolean.class)
                .expression(expression)
                .classManager(batchCompiler.getClassManager())
                .generatedClassName("GeneratorEvaluator__")
                .build();
        MVELBatchCompiler.LambdaHandle handle = batchCompiler.add(evalInfo);
        DrlxLambdaConstraint constraint = new DrlxLambdaConstraint(expression, patternType, (Evaluator<Object, Void, Boolean>) null);
        pendingLambdas.add(new PendingLambda(handle, constraint));
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
                        .classManager(batchCompiler.getClassManager())
                        .generatedClassName("GeneratorEvaluator__")
                        .build();
        MVELBatchCompiler.LambdaHandle handle = batchCompiler.add(evalInfo);
        DrlxLambdaBetaConstraint constraint = new DrlxLambdaBetaConstraint(expression, patternType,
                (Evaluator<Map<String, Object>, Void, Boolean>) null, requiredDeclarations);
        pendingLambdas.add(new PendingLambda(handle, constraint));
        return constraint;
    }

    private DrlxLambdaConsequence createBatchConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        CompilerParameters<Map<String, Object>, Void, String> evalInfo =
                (CompilerParameters) MVEL.<Object>map(org.mvel3.transpiler.context.Declaration.from(declarationTypes))
                        .<String>out(String.class)
                        .block(consequenceBlock + RETURN_NULL)
                        .imports(new HashSet<>())
                        .classManager(batchCompiler.getClassManager())
                        .generatedClassName("GeneratorEvaluator__")
                        .build();
        MVELBatchCompiler.LambdaHandle handle = batchCompiler.add(evalInfo);
        DrlxLambdaConsequence consequence = new DrlxLambdaConsequence(consequenceBlock, declarationTypes, (Evaluator<Map<String, Object>, Void, String>) null);
        pendingLambdas.add(new PendingLambda(handle, consequence));
        return consequence;
    }

    protected Object loadPreCompiledEvaluator(String fqn, int physicalId) throws Exception {
        Class<?> clazz = loadedClassCache.get(fqn);
        if (clazz == null) {
            Path classFilePath = LambdaRegistry.INSTANCE.getPhysicalPath(physicalId);
            if (classFilePath == null) {
                throw new IllegalStateException("No persisted class file for physicalId " + physicalId);
            }
            byte[] bytes = Files.readAllBytes(classFilePath);
            if (preBuildClassManager == null) {
                preBuildClassManager = new ClassManager();
            }
            preBuildClassManager.define(Collections.singletonMap(fqn, bytes));
            clazz = preBuildClassManager.getClass(fqn);
            loadedClassCache.put(fqn, clazz);
        }
        return clazz.getConstructor().newInstance();
    }

    /**
     * Find bound variables from previous patterns that are referenced in the expression.
     * Uses word-boundary matching to avoid false positives.
     */
    public List<BoundVariable> findReferencedBindings(String expression, Map<String, BoundVariable> boundVariables) {
        List<BoundVariable> referenced = new ArrayList<>();
        for (Map.Entry<String, BoundVariable> entry : boundVariables.entrySet()) {
            String varName = entry.getKey();
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                    "(?<![a-zA-Z0-9_])" + java.util.regex.Pattern.quote(varName) + "(?![a-zA-Z0-9_])");
            if (regex.matcher(expression).find()) {
                referenced.add(entry.getValue());
            }
        }
        return referenced;
    }

    public Map<String, Type<?>> getTypeMap(GroupElement ge) {
        Map<String, Type<?>> types = new LinkedHashMap<>();
        ge.getChildren().stream().filter(element -> element instanceof Pattern).forEach(pattern -> {
            Pattern p = (Pattern) pattern;
            Class<?> patternClass = ((ClassObjectType) p.getObjectType()).getClassType();
            Declaration declaration = p.getDeclaration();
            types.put(declaration.getIdentifier(), Type.type(patternClass));
        });
        return types;
    }
}
