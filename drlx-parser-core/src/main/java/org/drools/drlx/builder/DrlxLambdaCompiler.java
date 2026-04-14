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

    public record PendingLambda(MVELBatchCompiler.LambdaHandle handle, Object target) {}

    protected int patternId = 0;

    protected DrlxLambdaMetadata preBuildMetadata; // null = normal build
    protected Path outputDir; // directory containing pre-compiled .class files

    protected String currentRuleName;
    protected int lambdaCounter;

    protected boolean batchMode = false;
    protected MVELBatchCompiler batchCompiler;
    protected final List<PendingLambda> pendingLambdas = new ArrayList<>();

    private ClassManager preBuildClassManager;
    private final Map<String, Class<?>> loadedClassCache = new HashMap<>();

    public void setPreBuildMetadata(DrlxLambdaMetadata preBuildMetadata) {
        this.preBuildMetadata = preBuildMetadata;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public void enableBatchMode(MVELBatchCompiler batchCompiler) {
        this.batchMode = true;
        this.batchCompiler = batchCompiler;
    }

    public MVELBatchCompiler getBatchCompiler() {
        return batchCompiler;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public int nextPatternId() {
        return patternId++;
    }

    /** Reset per-rule state. Must be called at the start of each rule. */
    public void beginRule(String ruleName) {
        this.currentRuleName = ruleName;
        this.lambdaCounter = 0;
    }

    public String getCurrentRuleName() {
        return currentRuleName;
    }

    public DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        int capturedCounter = lambdaCounter++;
        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(expression)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Object, Void, Boolean> evaluator = (Evaluator<Object, Void, Boolean>) loadPreCompiledEvaluator(entry.fqn(), entry.physicalId());
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

    public Constraint createBetaLambdaConstraint(String expression, Class<?> patternType,
                                                 org.mvel3.transpiler.context.Declaration<?>[] patternDeclarations,
                                                 List<BoundVariable> referencedBindings) {
        int capturedCounter = lambdaCounter++;

        List<org.mvel3.transpiler.context.Declaration<?>> allDecls = new ArrayList<>(Arrays.asList(patternDeclarations));
        for (BoundVariable bv : referencedBindings) {
            allDecls.add(org.mvel3.transpiler.context.Declaration.of(bv.name(), bv.type()));
        }
        org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations = allDecls.toArray(new org.mvel3.transpiler.context.Declaration[0]);

        Declaration[] requiredDeclarations = referencedBindings.stream()
                .map(bv -> bv.pattern().getDeclaration())
                .toArray(Declaration[]::new);

        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(expression)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Map<String, Object>, Void, Boolean> evaluator =
                            (Evaluator<Map<String, Object>, Void, Boolean>) loadPreCompiledEvaluator(entry.fqn(), entry.physicalId());
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

    public DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int capturedCounter = lambdaCounter++;
        if (preBuildMetadata != null) {
            DrlxLambdaMetadata.LambdaEntry entry = preBuildMetadata.get(currentRuleName, capturedCounter);
            if (entry != null && entry.expression().equals(consequenceBlock)) {
                try {
                    @SuppressWarnings("unchecked")
                    Evaluator<Map<String, Object>, Void, String> evaluator = (Evaluator<Map<String, Object>, Void, String>) loadPreCompiledEvaluator(entry.fqn(), entry.physicalId());
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

    public void compileBatch(ClassLoader classLoader) {
        if (pendingLambdas.isEmpty()) {
            return;
        }
        batchCompiler.compile(classLoader);
        for (PendingLambda pl : pendingLambdas) {
            if (pl.target() instanceof DrlxLambdaConstraint c) {
                c.setEvaluator(batchCompiler.resolve(pl.handle()));
            } else if (pl.target() instanceof DrlxLambdaBetaConstraint c) {
                c.setEvaluator(batchCompiler.resolve(pl.handle()));
            } else if (pl.target() instanceof DrlxLambdaConsequence c) {
                c.setEvaluator(batchCompiler.resolve(pl.handle()));
            }
        }
        pendingLambdas.clear();
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
