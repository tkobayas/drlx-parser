package org.drools.drlx.domain.acc;

import org.kie.api.runtime.rule.AccumulateFunction;

public class TestAccFuncs {
    public static final AccumulateFunction sumSquares = new SumSquaresAccumulateFunction();
    public static final AccumulateFunction doubleCount = new DoubleCountAccumulateFunction();
}
