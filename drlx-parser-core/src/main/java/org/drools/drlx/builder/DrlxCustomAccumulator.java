package org.drools.drlx.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.Accumulator;
import org.drools.drlx.builder.DrlxRuleAstModel.InitVarIR;
import org.mvel3.Evaluator;
import org.kie.api.runtime.rule.FactHandle;

public final class DrlxCustomAccumulator implements Accumulator {

    private final List<InitVarIR> initVars;
    private final String srcBindingName;
    private final Map<String, Object> initDefaults;

    private Evaluator<Map<String, Object>, Void, ?> actionEval;
    private Evaluator<Map<String, Object>, Void, ?> reverseEval;
    private Evaluator<Map<String, Object>, Void, Object> resultEval;

    public DrlxCustomAccumulator(List<InitVarIR> initVars, String srcBindingName) {
        this.initVars = initVars;
        this.srcBindingName = srcBindingName;
        this.initDefaults = buildDefaults(initVars);
    }

    void setActionEval(Evaluator<Map<String, Object>, Void, ?> eval) { this.actionEval = eval; }
    void setReverseEval(Evaluator<Map<String, Object>, Void, ?> eval) { this.reverseEval = eval; }
    void setResultEval(Evaluator<Map<String, Object>, Void, Object> eval) { this.resultEval = eval; }

    @Override public Object createWorkingMemoryContext() { return null; }

    @Override
    public Object createContext() {
        return new HashMap<String, Object>(initDefaults.size() + 1);
    }

    @Override
    public Object init(Object wmContext, Object context, BaseTuple tuple,
                       Declaration[] decls, ValueResolver vr) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) context;
        map.putAll(initDefaults);
        return context;
    }

    @Override
    public Object accumulate(Object wmContext, Object context, BaseTuple tuple,
                             FactHandle handle, Declaration[] decls,
                             Declaration[] innerDecls, ValueResolver vr) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) context;
        Object srcFact = handle.getObject();
        map.put(srcBindingName, srcFact);
        try {
            actionEval.eval(map);
        } finally {
            map.remove(srcBindingName);
        }
        return srcFact;
    }

    @Override
    public boolean supportsReverse() { return reverseEval != null; }

    @Override
    public boolean tryReverse(Object wmContext, Object context, BaseTuple tuple,
                              FactHandle handle, Object value,
                              Declaration[] decls, Declaration[] innerDecls,
                              ValueResolver vr) {
        if (reverseEval == null) return false;
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) context;
        map.put(srcBindingName, value);
        try {
            reverseEval.eval(map);
        } finally {
            map.remove(srcBindingName);
        }
        return true;
    }

    @Override
    public Object getResult(Object wmContext, Object context, BaseTuple tuple,
                            Declaration[] decls, ValueResolver vr) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) context;
        return resultEval.eval(map);
    }

    static final class ActionSink implements EvaluatorSink {
        private final DrlxCustomAccumulator parent;
        ActionSink(DrlxCustomAccumulator parent) { this.parent = parent; }
        @SuppressWarnings("unchecked")
        @Override public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
            parent.setActionEval((Evaluator<Map<String, Object>, Void, ?>) evaluator);
        }
    }

    static final class ReverseSink implements EvaluatorSink {
        private final DrlxCustomAccumulator parent;
        ReverseSink(DrlxCustomAccumulator parent) { this.parent = parent; }
        @SuppressWarnings("unchecked")
        @Override public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
            parent.setReverseEval((Evaluator<Map<String, Object>, Void, ?>) evaluator);
        }
    }

    static final class ResultSink implements EvaluatorSink {
        private final DrlxCustomAccumulator parent;
        ResultSink(DrlxCustomAccumulator parent) { this.parent = parent; }
        @SuppressWarnings("unchecked")
        @Override public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
            parent.setResultEval((Evaluator<Map<String, Object>, Void, Object>) evaluator);
        }
    }

    private static Map<String, Object> buildDefaults(List<InitVarIR> initVars) {
        Map<String, Object> defaults = new HashMap<>(initVars.size());
        for (InitVarIR iv : initVars) {
            defaults.put(iv.name(), parseLiteralValue(iv.initializer(), iv.typeName()));
        }
        return defaults;
    }

    static Object parseLiteralValue(String initializer, String typeName) {
        if (initializer == null || initializer.equals("null")) return null;
        if (initializer.equals("true")) return Boolean.TRUE;
        if (initializer.equals("false")) return Boolean.FALSE;
        if (initializer.startsWith("\"") && initializer.endsWith("\"")) {
            return initializer.substring(1, initializer.length() - 1);
        }
        if (initializer.startsWith("'") && initializer.endsWith("'") && initializer.length() == 3) {
            return initializer.charAt(1);
        }
        return parseNumericValue(initializer, typeName);
    }

    private static Object parseNumericValue(String initializer, String typeName) {
        String lower = initializer.toLowerCase();
        String numStr = lower;
        if (numStr.endsWith("l") || numStr.endsWith("f") || numStr.endsWith("d")) {
            numStr = numStr.substring(0, numStr.length() - 1);
        }
        return switch (typeName) {
            case "int", "Integer" -> Integer.parseInt(numStr);
            case "long", "Long" -> Long.parseLong(numStr);
            case "double", "Double" -> Double.parseDouble(numStr);
            case "float", "Float" -> Float.parseFloat(numStr);
            case "short", "Short" -> Short.parseShort(numStr);
            case "byte", "Byte" -> Byte.parseByte(numStr);
            default -> {
                if (lower.contains(".") || lower.endsWith("d")) yield Double.parseDouble(numStr);
                if (lower.endsWith("f")) yield Float.parseFloat(numStr);
                if (lower.endsWith("l")) yield Long.parseLong(numStr);
                yield Integer.parseInt(numStr);
            }
        };
    }
}
