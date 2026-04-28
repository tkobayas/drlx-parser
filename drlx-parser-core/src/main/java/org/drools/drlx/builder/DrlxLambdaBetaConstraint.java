package org.drools.drlx.builder;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.MutableTypeConstraint;
import org.kie.api.runtime.rule.FactHandle;
import org.mvel3.ClassManager;
import org.mvel3.CompilerParameters;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;

/**
 * Beta constraint for join conditions that reference bindings from previous patterns.
 * Uses MAP context MVEL evaluator so both current fact properties and external bindings
 * are accessible.
 */
public class DrlxLambdaBetaConstraint extends MutableTypeConstraint<ContextEntry> implements EvaluatorSink {

    private String expression;
    private Class<?> patternType;
    private Declaration[] requiredDeclarations;
    private Evaluator<Map<String, Object>, Void, Boolean> evaluator;

    // Cached property extractors for fast evaluation (avoid Introspector at eval time)
    private transient PropertyExtractor[] propertyExtractors;

    // For deferred compilation (batch mode / pre-build)
    private org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations;

    record PropertyExtractor(String name, Method getter) {}

    private static final ConcurrentHashMap<Class<?>, PropertyExtractor[]> EXTRACTOR_CACHE = new ConcurrentHashMap<>();

    public DrlxLambdaBetaConstraint() {
    }

    /**
     * Constructor for immediate compilation.
     */
    public DrlxLambdaBetaConstraint(String expression, Class<?> patternType,
                                     org.mvel3.transpiler.context.Declaration<?>[] mvelDeclarations,
                                     Declaration[] requiredDeclarations) {
        this.expression = expression;
        this.patternType = patternType;
        this.mvelDeclarations = mvelDeclarations;
        this.requiredDeclarations = requiredDeclarations;
        this.propertyExtractors = buildPropertyExtractors(patternType);
        initializeLambdaConstraint();
    }

    /**
     * Constructor with pre-compiled evaluator.
     */
    public DrlxLambdaBetaConstraint(String expression, Class<?> patternType,
                                     Evaluator<Map<String, Object>, Void, Boolean> evaluator,
                                     Declaration[] requiredDeclarations) {
        this.expression = expression;
        this.patternType = patternType;
        this.evaluator = evaluator;
        this.requiredDeclarations = requiredDeclarations;
        this.propertyExtractors = buildPropertyExtractors(patternType);
    }

    public String getExpression() {
        return expression;
    }

    @SuppressWarnings("unchecked")
    public Evaluator<?, ?, Boolean> getEvaluator() {
        return evaluator;
    }

    @SuppressWarnings("unchecked")
    public void setEvaluator(Evaluator<?, ?, Boolean> evaluator) {
        this.evaluator = (Evaluator<Map<String, Object>, Void, Boolean>) evaluator;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
        setEvaluator((Evaluator) evaluator);
    }

    private static PropertyExtractor[] buildPropertyExtractors(Class<?> patternType) {
        return EXTRACTOR_CACHE.computeIfAbsent(patternType, clz -> {
            try {
                BeanInfo beanInfo = Introspector.getBeanInfo(clz, Object.class);
                return Arrays.stream(beanInfo.getPropertyDescriptors())
                        .filter(pd -> pd.getReadMethod() != null)
                        .map(pd -> new PropertyExtractor(pd.getName(), pd.getReadMethod()))
                        .toArray(PropertyExtractor[]::new);
            } catch (IntrospectionException e) {
                throw new RuntimeException("Failed to introspect " + clz.getName(), e);
            }
        });
    }

    private void initializeLambdaConstraint() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        CompilerParameters<Map<String, Object>, Void, Boolean> evalInfo =
                (CompilerParameters) MVEL.<Object>map(mvelDeclarations)
                        .<Boolean>out(Boolean.class)
                        .expression(expression)
                        .classManager(new ClassManager())
                        .build();
        MVEL mvel = new MVEL();
        evaluator = mvel.compile(evalInfo);
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return requiredDeclarations;
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        for (int i = 0; i < requiredDeclarations.length; i++) {
            if (requiredDeclarations[i].equals(oldDecl)) {
                requiredDeclarations[i] = newDecl;
            }
        }
    }

    @Override
    public DrlxLambdaBetaConstraint clone() {
        // Drools' LogicTransformer clones constraints when expanding OR-trees into
        // sibling AND sub-rules. Re-running initializeLambdaConstraint() would NPE
        // on the deferred-compile path (mvelDeclarations is null until bindEvaluator
        // fires). Reuse the already-bound evaluator — MVEL3 evaluators are stateless.
        return new DrlxLambdaBetaConstraint(this.expression, this.patternType,
                this.evaluator, this.requiredDeclarations.clone());
    }

    @Override
    public ConstraintType getType() {
        return ConstraintType.BETA;
    }

    @Override
    public boolean isTemporal() {
        return false;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        throw new UnsupportedOperationException("Beta constraint should not be evaluated as alpha");
    }

    @Override
    public boolean isAllowedCachedLeft(ContextEntry context, FactHandle handle) {
        DrlxBetaContextEntry ctx = (DrlxBetaContextEntry) context;
        Map<String, Object> map = buildEvalMap(handle.getObject(), ctx.tuple);
        return evaluator.eval(map);
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, ContextEntry context) {
        DrlxBetaContextEntry ctx = (DrlxBetaContextEntry) context;
        Map<String, Object> map = buildEvalMap(ctx.handle.getObject(), tuple);
        return evaluator.eval(map);
    }

    private Map<String, Object> buildEvalMap(Object currentFact, BaseTuple tuple) {
        Map<String, Object> map = new HashMap<>(propertyExtractors.length + requiredDeclarations.length);

        // Extract current fact's properties using cached extractors
        for (PropertyExtractor pe : propertyExtractors) {
            try {
                map.put(pe.name, pe.getter.invoke(currentFact));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read property " + pe.name, e);
            }
        }

        // Extract bound variables from tuple
        for (Declaration decl : requiredDeclarations) {
            FactHandle fh = tuple.get(decl);
            map.put(decl.getIdentifier(), fh != null ? fh.getObject() : null);
        }

        return map;
    }

    @Override
    public ContextEntry createContext() {
        return new DrlxBetaContextEntry();
    }

    @Override
    public String toString() {
        return expression;
    }

    /**
     * Context entry that caches the left tuple and right fact handle for beta evaluation.
     */
    public static class DrlxBetaContextEntry implements ContextEntry {

        BaseTuple tuple;
        FactHandle handle;
        ContextEntry next;

        @Override
        public ContextEntry getNext() {
            return next;
        }

        @Override
        public void setNext(ContextEntry entry) {
            this.next = entry;
        }

        @Override
        public void updateFromTuple(ValueResolver valueResolver, BaseTuple tuple) {
            this.tuple = tuple;
        }

        @Override
        public void updateFromFactHandle(ValueResolver valueResolver, FactHandle handle) {
            this.handle = handle;
        }

        @Override
        public void resetTuple() {
            this.tuple = null;
        }

        @Override
        public void resetFactHandle() {
            this.handle = null;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
