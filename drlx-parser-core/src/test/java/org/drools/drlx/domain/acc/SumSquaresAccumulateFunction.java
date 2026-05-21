package org.drools.drlx.domain.acc;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import org.kie.api.runtime.rule.AccumulateFunction;

public class SumSquaresAccumulateFunction implements AccumulateFunction<SumSquaresAccumulateFunction.SumSqData> {

    public static class SumSqData implements Serializable {
        private double total;
    }

    @Override public SumSqData createContext() { return new SumSqData(); }

    @Override public void init(SumSqData ctx) { ctx.total = 0; }

    @Override public void accumulate(SumSqData ctx, Object value) {
        double v = ((Number) value).doubleValue();
        ctx.total += v * v;
    }

    @Override public void reverse(SumSqData ctx, Object value) {
        double v = ((Number) value).doubleValue();
        ctx.total -= v * v;
    }

    @Override public boolean supportsReverse() { return true; }

    @Override public Object getResult(SumSqData ctx) { return ctx.total; }

    @Override public Class<?> getResultType() { return Double.class; }

    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
    @Override public void writeExternal(ObjectOutput out) throws IOException {}
}
