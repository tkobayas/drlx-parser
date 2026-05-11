package org.drools.drlx.ruleunit;

import java.util.ArrayList;
import java.util.List;

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataProcessor;
import org.drools.ruleunits.api.DataSource;
import org.kie.api.runtime.rule.FactHandle;

/**
 * Test-only {@link DataProcessor} that records every insert / update / remove
 * notification it receives from a {@link DataSource}. Use {@link #subscribeTo}
 * to attach a fresh observer to a DataSource before firing rules, then read
 * back {@link #inserted()} / {@link #updated()} / {@link #removed()} to verify
 * what the rule consequence did.
 *
 * <p>Subscribing to a non-empty DataSource will replay its current contents
 * through {@link #insert(DataHandle, Object)} — that is the upstream
 * {@code ListDataStore} contract, not behaviour added here.
 */
public final class TestDataObserver<T> implements DataProcessor<T> {

    private final List<T> inserted = new ArrayList<>();
    private final List<T> updated = new ArrayList<>();
    private final List<DataHandle> removed = new ArrayList<>();

    public static <T> TestDataObserver<T> subscribeTo(DataSource<T> source) {
        TestDataObserver<T> observer = new TestDataObserver<>();
        source.subscribe(observer);
        return observer;
    }

    @Override
    public FactHandle insert(DataHandle handle, T object) {
        inserted.add(object);
        return null;
    }

    @Override
    public void update(DataHandle handle, T object) {
        updated.add(object);
    }

    @Override
    public void delete(DataHandle handle) {
        removed.add(handle);
    }

    public List<T> inserted() {
        return List.copyOf(inserted);
    }

    public List<T> updated() {
        return List.copyOf(updated);
    }

    public List<DataHandle> removed() {
        return List.copyOf(removed);
    }
}
