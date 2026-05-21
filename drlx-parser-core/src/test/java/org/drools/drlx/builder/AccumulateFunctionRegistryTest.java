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

import org.drools.core.base.accumulators.AverageAccumulateFunction;
import org.drools.core.base.accumulators.CountAccumulateFunction;
import org.drools.core.base.accumulators.MaxAccumulateFunction;
import org.drools.core.base.accumulators.MinAccumulateFunction;
import org.drools.core.base.accumulators.SumAccumulateFunction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccumulateFunctionRegistryTest {

    @Test
    void resolvesBuiltinAverage() {
        var resolved = AccumulateFunctionRegistry.resolve("avg");
        assertThat(resolved.functionClass()).isEqualTo(AverageAccumulateFunction.class);
        assertThat(resolved.resultType()).isEqualTo(Double.class);
        assertThat(resolved.acceptsZeroArgs()).isFalse();
    }

    @Test
    void resolvesBuiltinCountWithZeroArgs() {
        var resolved = AccumulateFunctionRegistry.resolve("count");
        assertThat(resolved.functionClass()).isEqualTo(CountAccumulateFunction.class);
        assertThat(resolved.resultType()).isEqualTo(Long.class);
        assertThat(resolved.acceptsZeroArgs()).isTrue();
    }

    @Test
    void resolvesSumMinMax() {
        assertThat(AccumulateFunctionRegistry.resolve("sum").functionClass())
                .isEqualTo(SumAccumulateFunction.class);
        assertThat(AccumulateFunctionRegistry.resolve("min").functionClass())
                .isEqualTo(MinAccumulateFunction.class);
        assertThat(AccumulateFunctionRegistry.resolve("max").functionClass())
                .isEqualTo(MaxAccumulateFunction.class);
    }

    @Test
    void rejectsUnknownFunction() {
        assertThatThrownBy(() -> AccumulateFunctionRegistry.resolve("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown accumulate function 'bogus'")
                .hasMessageContaining("avg, sum, min, max, count");
    }

    @Test
    void qualifiedNameReturnsNull() {
        var resolved = AccumulateFunctionRegistry.resolve("Func.avg");
        assertThat(resolved).isNull();
    }
}
