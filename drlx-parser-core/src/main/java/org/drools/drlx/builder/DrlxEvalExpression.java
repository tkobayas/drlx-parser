package org.drools.drlx.builder;

import java.util.HashMap;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.EvalExpression;
import org.kie.api.runtime.rule.FactHandle;
import org.mvel3.Evaluator;

/**
 * Bridges an MVEL3-compiled boolean evaluator to drools-base's EvalExpression.
 * Used by EvalIR runtime mapping to produce the EvalCondition that backs the
 * DRLXXXX 'test' construct.
 *
 * <p>Implements {@link EvaluatorSink} so the deferred batch-compilation flow
 * in {@link DrlxLambdaCompiler#compileBatch(ClassLoader)} can plug in the
 * resolved evaluator after batch compile.
 */
public class DrlxEvalExpression implements EvalExpression, EvaluatorSink {

    private final String expression;
    private Evaluator<Map<String, Object>, Void, Boolean> evaluator;

    public DrlxEvalExpression(String expression,
                              Evaluator<Map<String, Object>, Void, Boolean> evaluator) {
        this.expression = expression;
        this.evaluator = evaluator;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public Object createContext() {
        return null;
    }

    @Override
    public boolean evaluate(BaseTuple tuple,
                            Declaration[] requiredDeclarations,
                            ValueResolver valueResolver,
                            Object context) throws Exception {
        if (evaluator == null) {
            throw new IllegalStateException(
                    "DrlxEvalExpression evaluator not yet bound: " + expression);
        }
        Map<String, Object> input = new HashMap<>(requiredDeclarations.length * 2);
        for (Declaration d : requiredDeclarations) {
            FactHandle fh = tuple.get(d);
            input.put(d.getIdentifier(), fh != null ? fh.getObject() : null);
        }
        return Boolean.TRUE.equals(evaluator.eval(input));
    }

    @Override
    public void replaceDeclaration(Declaration declaration, Declaration resolved) {
        // No-op — DrlxEvalExpression looks up declarations by name at evaluate time.
        // EvalCondition's own replaceDeclaration updates its requiredDeclarations[]
        // array, which evaluate() iterates, so name-based lookup remains correct.
    }

    @Override
    public DrlxEvalExpression clone() {
        // Stateless wrt evaluator (compiled once, shared) — share the reference.
        return new DrlxEvalExpression(expression, evaluator);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
        this.evaluator = (Evaluator<Map<String, Object>, Void, Boolean>) evaluator;
    }
}
