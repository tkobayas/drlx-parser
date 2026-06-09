package org.drools.drlx.builder;

import java.util.Objects;

import org.drools.core.rule.consequence.InternalMatch;
import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.impl.InternalStoreCallback;
import org.drools.util.bitmask.AllSetBitMask;

/**
 * Runtime helper called from rewritten DRLX consequences. The
 * {@code lookup(Object)} method is defined on
 * {@link InternalStoreCallback} (impl package), not on the public
 * {@link DataStore} interface, so consequences can't call it through
 * the static type. This static facade gives them a resolvable method
 * to call.
 */
public final class DataStoreSupport {

    private DataStoreSupport() {
    }

    public static DataHandle lookup(DataStore<?> store, Object fact) {
        return ((InternalStoreCallback) store).lookup(fact);
    }

    public static void update(DataStore<?> store, Object fact, InternalMatch match, String storeName) {
        InternalStoreCallback callback = (InternalStoreCallback) store;
        DataHandle handle = Objects.requireNonNull(callback.lookup(fact),
                "DataStore '" + storeName + "' has no DataHandle for the given fact");
        callback.update(handle, fact, AllSetBitMask.get(), fact.getClass(), match);
    }
}
