package org.drools.drlx.builder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.Function;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.AccumulateContextEntry;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.RuleConditionElement;
import org.drools.base.rule.accessor.Accumulator;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.AccumulateNode.GroupByContext;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.util.index.TupleListWithContext;
import org.kie.api.runtime.rule.FactHandle;

public class DrlxGroupByAccumulate extends Accumulate {

    private Accumulate innerAccumulate;
    private Function<Object, Object> groupingFunction;
    private DrlxValueExtractor groupingFunctionMulti;
    private boolean multiSource;

    public DrlxGroupByAccumulate() {}

    public DrlxGroupByAccumulate(Accumulate innerAccumulate,
                                  Function<Object, Object> groupingFunction) {
        super(innerAccumulate.getSource(), innerAccumulate.getRequiredDeclarations());
        this.innerAccumulate = innerAccumulate;
        this.groupingFunction = groupingFunction;
        this.groupingFunctionMulti = null;
        this.multiSource = false;
    }

    public DrlxGroupByAccumulate(Accumulate innerAccumulate,
                                  DrlxValueExtractor groupingFunctionMulti) {
        super(innerAccumulate.getSource(), innerAccumulate.getRequiredDeclarations());
        this.innerAccumulate = innerAccumulate;
        this.groupingFunction = null;
        this.groupingFunctionMulti = groupingFunctionMulti;
        this.multiSource = true;
    }

    private Object getKey(BaseTuple tuple, FactHandle handle, ValueResolver valueResolver) {
        if (multiSource) {
            Declaration[] innerDecls = getInnerDeclarationCache();
            java.util.HashMap<String, Object> bindings = new java.util.HashMap<>(innerDecls.length);
            for (Declaration d : innerDecls) {
                bindings.put(d.getIdentifier(), d.getValue(valueResolver, tuple));
            }
            return groupingFunctionMulti.applyMulti(bindings);
        }
        return groupingFunction.apply(handle.getObject());
    }

    @Override
    public boolean isGroupBy() {
        return true;
    }

    @Override
    public Accumulator[] getAccumulators() {
        return innerAccumulate.getAccumulators();
    }

    @Override
    public Object createFunctionContext() {
        return innerAccumulate.createFunctionContext();
    }

    @Override
    public Object init(Object workingMemoryContext, Object accContext,
                       Object funcContext, BaseTuple leftTuple, ValueResolver valueResolver) {
        return funcContext;
    }

    @Override
    public Object accumulate(Object workingMemoryContext, Object context,
                             BaseTuple match, FactHandle handle,
                             ValueResolver valueResolver) {
        GroupByContext groupByContext = (GroupByContext) context;
        TupleImpl leftTupleMatch = (TupleImpl) match;
        TupleListWithContext<AccumulateContextEntry> tupleList =
                groupByContext.getGroup(workingMemoryContext, innerAccumulate,
                        leftTupleMatch,
                        getKey(leftTupleMatch, handle, valueResolver),
                        (ReteEvaluator) valueResolver);
        return accumulate(workingMemoryContext, match, handle, groupByContext, tupleList, valueResolver);
    }

    @Override
    public Object accumulate(Object workingMemoryContext, BaseTuple match,
                             FactHandle childHandle, Object groupByContext,
                             Object tupleList, ValueResolver valueResolver) {
        @SuppressWarnings("unchecked")
        TupleListWithContext<AccumulateContextEntry> list =
                (TupleListWithContext<AccumulateContextEntry>) tupleList;
        ((GroupByContext) groupByContext).moveToPropagateTupleList(list);
        return innerAccumulate.accumulate(workingMemoryContext, list.getContext(),
                match, childHandle, valueResolver);
    }

    @Override
    public boolean tryReverse(Object workingMemoryContext, Object context,
                              BaseTuple leftTuple, FactHandle handle,
                              BaseTuple match, ValueResolver valueResolver) {
        TupleImpl tupleMatch = (TupleImpl) match;
        @SuppressWarnings("unchecked")
        TupleListWithContext<AccumulateContextEntry> memory =
                (TupleListWithContext<AccumulateContextEntry>) tupleMatch.getMemory();
        AccumulateContextEntry entry = memory.getContext();
        boolean reversed = innerAccumulate.tryReverse(workingMemoryContext, entry,
                leftTuple, handle, match, valueResolver);
        if (reversed) {
            GroupByContext groupByContext = (GroupByContext) context;
            groupByContext.moveToPropagateTupleList(memory);
            memory.remove(tupleMatch);
            if (memory.isEmpty()) {
                groupByContext.removeGroup(entry.getKey());
                memory.getContext().setEmpty(true);
            }
        }
        return reversed;
    }

    @Override
    public Object getResult(Object workingMemoryContext, Object context,
                            BaseTuple leftTuple, ValueResolver valueResolver) {
        AccumulateContextEntry entry = (AccumulateContextEntry) context;
        return entry.isEmpty() ? null :
                innerAccumulate.getResult(workingMemoryContext, context, leftTuple, valueResolver);
    }

    @Override
    public boolean supportsReverse() {
        return innerAccumulate.supportsReverse();
    }

    @Override
    public Accumulate clone() {
        if (multiSource) {
            return new DrlxGroupByAccumulate(innerAccumulate.clone(), groupingFunctionMulti);
        }
        return new DrlxGroupByAccumulate(innerAccumulate.clone(), groupingFunction);
    }

    @Override
    public Object createWorkingMemoryContext() {
        return innerAccumulate.createWorkingMemoryContext();
    }

    @Override
    public boolean isMultiFunction() {
        return innerAccumulate.isMultiFunction();
    }

    @Override
    public void replaceAccumulatorDeclaration(Declaration declaration, Declaration resolved) {
        innerAccumulate.replaceAccumulatorDeclaration(declaration, resolved);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        this.innerAccumulate = (Accumulate) in.readObject();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(innerAccumulate);
    }
}
