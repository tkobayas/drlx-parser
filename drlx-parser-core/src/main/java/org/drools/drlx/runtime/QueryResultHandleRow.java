package org.drools.drlx.runtime;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.kie.api.runtime.rule.EntryPoint;

public final class QueryResultHandleRow extends AbstractMap<String, InternalFactHandle> {

    private final Object[] values;
    private final Map<String, Integer> nameToIndex;
    private final ReteEvaluator reteEvaluator;

    QueryResultHandleRow(Object[] values, Map<String, Integer> nameToIndex, ReteEvaluator reteEvaluator) {
        this.values = values;
        this.nameToIndex = nameToIndex;
        this.reteEvaluator = reteEvaluator;
    }

    @Override
    public InternalFactHandle get(Object key) {
        Object value;
        if (key instanceof String s) {
            Integer idx = nameToIndex.get(s);
            value = idx != null ? values[idx] : null;
        } else if (key instanceof Integer i) {
            value = (i >= 0 && i < values.length) ? values[i] : null;
        } else {
            return null;
        }
        return value != null ? findFactHandle(value) : null;
    }

    // Searches all entry points because objects may live in named entry points.
    // Could be improved by threading the entry point name through.
    private InternalFactHandle findFactHandle(Object object) {
        for (EntryPoint ep : reteEvaluator.getEntryPoints()) {
            InternalFactHandle fh = (InternalFactHandle) ep.getFactHandle(object);
            if (fh != null) {
                return fh;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return nameToIndex.size();
    }

    @Override
    public Set<Entry<String, InternalFactHandle>> entrySet() {
        Set<Entry<String, InternalFactHandle>> entries = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : nameToIndex.entrySet()) {
            Object value = values[e.getValue()];
            entries.add(new SimpleImmutableEntry<>(e.getKey(), value != null ? findFactHandle(value) : null));
        }
        return entries;
    }
}
