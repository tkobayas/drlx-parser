/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package org.drools.drlx.builder;

import java.util.Map;

import org.drools.core.base.accumulators.AverageAccumulateFunction;
import org.drools.core.base.accumulators.CountAccumulateFunction;
import org.drools.core.base.accumulators.MaxAccumulateFunction;
import org.drools.core.base.accumulators.MinAccumulateFunction;
import org.drools.core.base.accumulators.SumAccumulateFunction;
import org.kie.api.runtime.rule.AccumulateFunction;

/**
 * v1 registry of built-in accumulate function names → {@link AccumulateFunction} classes.
 * Qualified names ({@code Func.avg}) are explicitly rejected; custom-function
 * resolution is a fast-follow under #26.
 */
public final class AccumulateFunctionRegistry {

    /** Resolved metadata for one accumulate function. */
    public record Resolution(Class<? extends AccumulateFunction> functionClass,
                             Class<?> resultType,
                             boolean acceptsZeroArgs) {
    }

    private static final Map<String, Resolution> BUILTINS = Map.of(
            "avg",   new Resolution(AverageAccumulateFunction.class, Double.class,     false),
            "sum",   new Resolution(SumAccumulateFunction.class,     Number.class,     false),
            "min",   new Resolution(MinAccumulateFunction.class,     Comparable.class, false),
            "max",   new Resolution(MaxAccumulateFunction.class,     Comparable.class, false),
            "count", new Resolution(CountAccumulateFunction.class,   Long.class,       true));

    private static final String BUILTIN_LIST = "avg, sum, min, max, count";

    private AccumulateFunctionRegistry() {
    }

    public static Resolution resolve(String functionName) {
        if (functionName.contains(".")) {
            throw new IllegalArgumentException(
                    "qualified accumulate function names ('" + functionName + "') "
                    + "are not yet supported — use unqualified built-ins ("
                    + BUILTIN_LIST + ")");
        }
        Resolution r = BUILTINS.get(functionName);
        if (r == null) {
            throw new IllegalArgumentException(
                    "unknown accumulate function '" + functionName + "' — "
                    + "built-ins are: " + BUILTIN_LIST);
        }
        return r;
    }
}
