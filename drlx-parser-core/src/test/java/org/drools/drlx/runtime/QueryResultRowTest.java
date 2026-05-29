package org.drools.drlx.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryResultRowTest {

    private static QueryResultRow createRow() {
        Object[] values = {"Alice", 30};
        Map<String, Integer> nameToIndex = new LinkedHashMap<>();
        nameToIndex.put("name", 0);
        nameToIndex.put("age", 1);
        return new QueryResultRow(values, nameToIndex);
    }

    @Test
    void namedAccess() {
        QueryResultRow row = createRow();
        assertThat(row.get("name")).isEqualTo("Alice");
        assertThat(row.get("age")).isEqualTo(30);
        assertThat(row.get("missing")).isNull();
    }

    @Test
    void indexedAccess() {
        QueryResultRow row = createRow();
        assertThat(row.get(0)).isEqualTo("Alice");
        assertThat(row.get(1)).isEqualTo(30);
    }

    @Test
    void objectsMethod() {
        QueryResultRow row = createRow();
        assertThat(row.objects()).containsExactly("Alice", 30);
    }

    @Test
    void handlesThrowsUnsupported() {
        QueryResultRow row = createRow();
        assertThatThrownBy(row::handles)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void iterableOverValues() {
        QueryResultRow row = createRow();
        List<Object> collected = new ArrayList<>();
        for (Object v : row) {
            collected.add(v);
        }
        assertThat(collected).containsExactly("Alice", 30);
    }

    @Test
    void mapEntrySet() {
        QueryResultRow row = createRow();
        assertThat((Map<String, Object>) row).containsEntry("name", "Alice").containsEntry("age", 30);
        assertThat((Map<String, Object>) row).hasSize(2);
    }
}
