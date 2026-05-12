package org.drools.drlx.builder;

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.impl.InternalStoreCallback;

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
}
