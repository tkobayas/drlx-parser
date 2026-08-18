package org.drools.drlx.builder;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.DataProvider;
import org.kie.api.runtime.rule.FactHandle;
import org.mvel3.Evaluator;

public class FromExpressionDataProvider implements DataProvider, EvaluatorSink {

    private final String expression;
    private Declaration[] declarations;
    private Evaluator<Map<String, Object>, Void, Object> evaluator;

    public FromExpressionDataProvider(String expression,
                                      Evaluator<Map<String, Object>, Void, Object> evaluator,
                                      Declaration... declarations) {
        this.expression = expression;
        this.evaluator = evaluator;
        this.declarations = declarations;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return declarations;
    }

    @Override
    public Object createContext() {
        return null;
    }

    @Override
    public Iterator getResults(BaseTuple tuple, ValueResolver valueResolver,
                               Object providerContext) {
        Object result = evaluate(tuple, valueResolver);
        if (result == null) {
            return Collections.emptyIterator();
        }
        if (result instanceof Object[] arr) {
            return Arrays.asList(arr).iterator();
        }
        if (result.getClass().isArray()) {
            int len = Array.getLength(result);
            return new AbstractList<Object>() {
                @Override public Object get(int index) { return Array.get(result, index); }
                @Override public int size() { return len; }
            }.iterator();
        }
        if (result instanceof Iterator iter) {
            return iter;
        }
        if (result instanceof Iterable<?> iter) {
            return iter.iterator();
        }
        return Collections.singletonList(result).iterator();
    }

    private Object evaluate(BaseTuple tuple, ValueResolver valueResolver) {
        if (evaluator == null) {
            throw new IllegalStateException(
                    "FromExpressionDataProvider evaluator not yet bound: " + expression);
        }
        Map<String, Object> input = new HashMap<>(declarations.length * 2);
        for (Declaration d : declarations) {
            FactHandle fh = tuple.get(d);
            input.put(d.getIdentifier(), fh != null ? d.getValue(null, fh.getObject()) : null);
        }
        return evaluator.eval(input);
    }

    @Override
    public DataProvider clone() {
        Declaration[] cloned = new Declaration[declarations.length];
        for (int i = 0; i < declarations.length; i++) {
            cloned[i] = declarations[i].clone();
        }
        return new FromExpressionDataProvider(expression, evaluator, cloned);
    }

    @Override
    public void replaceDeclaration(Declaration declaration, Declaration resolved) {
        for (int i = 0; i < declarations.length; i++) {
            if (declarations[i].getIdentifier().equals(declaration.getIdentifier())) {
                declarations[i] = resolved;
            }
        }
    }

    @Override
    public boolean isReactive() {
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
        this.evaluator = (Evaluator<Map<String, Object>, Void, Object>) evaluator;
    }
}
