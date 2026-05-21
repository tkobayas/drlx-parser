package org.drools.drlx.domain.acc;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.kie.api.runtime.rule.AccumulateFunction;

public class DoubleCountAccumulateFunction implements AccumulateFunction<DoubleCountAccumulateFunction.DblCountData> {

    public static class DblCountData implements Serializable {
        private long count;
    }

    @Override public DblCountData createContext() { return new DblCountData(); }

    @Override public void init(DblCountData ctx) { ctx.count = 0; }

    @Override public void accumulate(DblCountData ctx, Object value) { ctx.count++; }

    @Override public void reverse(DblCountData ctx, Object value) { ctx.count--; }

    @Override public boolean supportsReverse() { return true; }

    @Override public Object getResult(DblCountData ctx) { return ctx.count * 2L; }

    @Override public Class<?> getResultType() { return Long.class; }

    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
    @Override public void writeExternal(ObjectOutput out) throws IOException {}
}
