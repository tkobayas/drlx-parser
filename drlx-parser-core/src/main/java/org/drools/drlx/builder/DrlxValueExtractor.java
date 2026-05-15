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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.mvel3.Evaluator;

/**
 * Value-extractor lambda for an accumulate function argument. Produced by
 * {@link DrlxLambdaCompiler#createValueExtractor} and consumed by
 * {@link DrlxLambdaAccumulator}. Holds a late-bound MVEL3 evaluator and
 * threads the source fact through under its binding name.
 *
 * <p>The evaluator is {@code null} on the batch-compile path until
 * {@link DrlxLambdaCompiler#compileBatch(ClassLoader)} resolves all
 * pending handles and calls {@link #bindEvaluator}.
 */
public final class DrlxValueExtractor implements Function<Object, Object>, EvaluatorSink {

    private final String expression;
    private final String sourceBindingName;
    private Evaluator<Map<String, Object>, Void, Object> evaluator;

    public DrlxValueExtractor(String expression, String sourceBindingName,
                              Evaluator<Map<String, Object>, Void, Object> evaluator) {
        this.expression = expression;
        this.sourceBindingName = sourceBindingName;
        this.evaluator = evaluator;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void bindEvaluator(Evaluator<?, ?, ?> evaluator) {
        this.evaluator = (Evaluator<Map<String, Object>, Void, Object>) evaluator;
    }

    @Override
    public Object apply(Object fact) {
        Map<String, Object> map = new HashMap<>(1);
        map.put(sourceBindingName, fact);
        try {
            return evaluator.eval(map);
        } catch (Exception e) {
            throw new RuntimeException(
                    "value extractor '" + expression + "' failed at runtime", e);
        }
    }
}
