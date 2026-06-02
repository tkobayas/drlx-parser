package org.drools.drlx.runtime;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.drools.base.base.ValueResolver;
import org.drools.core.common.ReteEvaluator;

public final class QueryResultRow extends AbstractMap<String, Object> implements Iterable<Object> {

    private final Object[] values;
    private final Map<String, Integer> nameToIndex;
    private final ValueResolver valueResolver;

    public QueryResultRow(Object[] values, Map<String, Integer> nameToIndex, ValueResolver valueResolver) {
        this.values = values;
        this.nameToIndex = nameToIndex;
        this.valueResolver = valueResolver;
    }

    @Override
    public Object get(Object key) {
        if (key instanceof String s) {
            Integer idx = nameToIndex.get(s);
            return idx != null ? values[idx] : null;
        } else if (key instanceof Integer i) {
            return values[i];
        }
        return null;
    }

    public Object[] objects() {
        return values;
    }

    public QueryResultHandleRow handles() {
        if (valueResolver == null) {
            throw new UnsupportedOperationException(
                    "Handle access requires a ValueResolver — not available in this context");
        }
        return new QueryResultHandleRow(values, nameToIndex, (ReteEvaluator) valueResolver);
    }

    @Override
    public int size() {
        return nameToIndex.size();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> entries = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> e : nameToIndex.entrySet()) {
            entries.add(new SimpleImmutableEntry<>(e.getKey(), values[e.getValue()]));
        }
        return entries;
    }

    @Override
    public Iterator<Object> iterator() {
        return Arrays.asList(values).iterator();
    }
}
