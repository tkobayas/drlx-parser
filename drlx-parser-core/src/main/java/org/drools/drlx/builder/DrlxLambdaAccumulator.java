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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.Accumulator;
import org.kie.api.runtime.rule.AccumulateFunction;
import org.kie.api.runtime.rule.FactHandle;

public final class DrlxLambdaAccumulator implements Accumulator {

    private final AccumulateFunction<Serializable> accFunction;
    private final Function<Object, Object> extractor;
    private final DrlxValueExtractor multiExtractor;
    private final boolean multiSource;

    public DrlxLambdaAccumulator(AccumulateFunction<Serializable> accFunction,
                                 Function<Object, Object> extractor) {
        this.accFunction = accFunction;
        this.extractor = extractor;
        this.multiExtractor = null;
        this.multiSource = false;
    }

    public DrlxLambdaAccumulator(AccumulateFunction<Serializable> accFunction,
                                 DrlxValueExtractor multiExtractor,
                                 boolean multiSource) {
        this.accFunction = accFunction;
        this.extractor = null;
        this.multiExtractor = multiExtractor;
        this.multiSource = multiSource;
    }

    @Override public Object createWorkingMemoryContext() { return null; }

    @Override public Object createContext() { return accFunction.createContext(); }

    @Override
    public Object init(Object wmContext, Object context, BaseTuple tuple,
                       Declaration[] decls, ValueResolver vr) {
        try {
            accFunction.init((Serializable) context);
        } catch (Exception e) {
            throw new RuntimeException("init failed for " + accFunction.getClass().getSimpleName(), e);
        }
        return context;
    }

    @Override
    public Object accumulate(Object wmContext, Object context, BaseTuple tuple,
                             FactHandle handle, Declaration[] decls,
                             Declaration[] innerDecls, ValueResolver vr) {
        Object value;
        if (multiSource) {
            Map<String, Object> bindings = new HashMap<>(innerDecls.length);
            for (Declaration d : innerDecls) {
                bindings.put(d.getIdentifier(), d.getValue(vr, tuple));
            }
            value = (multiExtractor == null) ? bindings : multiExtractor.applyMulti(bindings);
        } else {
            value = (extractor == null) ? handle.getObject() : extractor.apply(handle.getObject());
        }
        try {
            accFunction.accumulate((Serializable) context, value);
        } catch (Exception e) {
            throw new RuntimeException("accumulate failed for " + accFunction.getClass().getSimpleName(), e);
        }
        return value;
    }

    @Override public boolean supportsReverse() { return accFunction.supportsReverse(); }

    @Override
    public boolean tryReverse(Object wmContext, Object context, BaseTuple tuple,
                              FactHandle handle, Object value,
                              Declaration[] decls, Declaration[] innerDecls,
                              ValueResolver vr) {
        if (!accFunction.supportsReverse()) {
            return false;
        }
        try {
            accFunction.reverse((Serializable) context, value);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("reverse failed for " + accFunction.getClass().getSimpleName(), e);
        }
    }

    @Override
    public Object getResult(Object wmContext, Object context, BaseTuple tuple,
                            Declaration[] decls, ValueResolver vr) {
        try {
            return accFunction.getResult((Serializable) context);
        } catch (Exception e) {
            throw new RuntimeException("getResult failed for " + accFunction.getClass().getSimpleName(), e);
        }
    }
}
