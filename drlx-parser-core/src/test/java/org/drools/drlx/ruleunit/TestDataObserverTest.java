package org.drools.drlx.ruleunit;

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataObserverTest {

    @Test
    void capturesInserts() {
        DataStore<String> store = DataSource.createStore();
        TestDataObserver<String> obs = TestDataObserver.subscribeTo(store);

        store.add("a");
        store.add("b");

        assertThat(obs.inserted()).containsExactly("a", "b");
        assertThat(obs.updated()).isEmpty();
        assertThat(obs.removed()).isEmpty();
    }

    @Test
    void replaysExistingItemsOnSubscribe() {
        DataStore<String> store = DataSource.createStore();
        store.add("pre-existing");

        TestDataObserver<String> obs = TestDataObserver.subscribeTo(store);

        assertThat(obs.inserted()).containsExactly("pre-existing");
    }

    @Test
    void capturesUpdate() {
        DataStore<String> store = DataSource.createStore();
        DataHandle handle = store.add("v1");

        TestDataObserver<String> obs = TestDataObserver.subscribeTo(store);
        store.update(handle, "v2");

        assertThat(obs.inserted()).containsExactly("v1");
        assertThat(obs.updated()).containsExactly("v2");
    }

    @Test
    void capturesRemove() {
        DataStore<String> store = DataSource.createStore();
        DataHandle handle = store.add("doomed");

        TestDataObserver<String> obs = TestDataObserver.subscribeTo(store);
        store.remove(handle);

        assertThat(obs.removed()).containsExactly(handle);
    }
}
