package org.drools.drlx.builder;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mvel3.parser.antlr4.Antlr4MvelParser;

import static org.assertj.core.api.Assertions.assertThat;

class DataStoreUpdateRewriterTest {

    private DataStoreUpdateRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new DataStoreUpdateRewriter(new Antlr4MvelParser());
    }

    @Test
    void emptyGlobalsReturnsInputUnchanged() {
        String body = "alerts.update(t);";
        assertThat(rewriter.rewrite(body, Set.of())).isEqualTo(body);
    }

    @Test
    void noUpdateCallReturnsInputUnchanged() {
        String body = "alerts.add(t); alerts.remove(t);";
        assertThat(rewriter.rewrite(body, Set.of("alerts"))).isEqualTo(body);
    }

    @Test
    void simpleUpdateWithNameExprArgIsRewritten() {
        String body = "alerts.update(t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, t, __match__,");
        assertThat(result).doesNotContain("alerts.update(");
    }

    @Test
    void updateOnFieldAccessExprArgIsRewritten() {
        String body = "alerts.update(this.t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, this.t, __match__,");
    }

    @Test
    void updateWithComplexArgIsLeftUntouched() {
        String body = "alerts.update(getThing(t));";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result.replaceAll("\\s+", ""))
                .isEqualTo("alerts.update(getThing(t));");
        assertThat(result).doesNotContain("DataStoreSupport");
    }

    @Test
    void updateOnUnrelatedScopeIsLeftUntouched() {
        String body = "other.update(t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).isEqualTo(body);
    }

    @Test
    void multipleMatchesAreAllRewritten() {
        String body = "alerts.update(t); alerts.update(u);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        long count = result.split("DataStoreSupport\\.update", -1).length - 1;
        assertThat(count).isEqualTo(2);
    }

    @Test
    void mixedRewritableAndNonRewritableUpdatesAreHandled() {
        String body = "alerts.update(t); alerts.update(getThing(u));";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        long count = result.split("DataStoreSupport\\.update", -1).length - 1;
        assertThat(count).isEqualTo(1);
        assertThat(result).contains("alerts.update(getThing(u))");
    }

    @Test
    void chainedScopeIsLeftUntouched() {
        String body = "alerts.add(t); getStore().update(t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).doesNotContain("DataStoreSupport");
    }

    @Test
    void malformedJavaReturnsInputUnchanged() {
        String body = "alerts.update(t // missing semicolon and paren";
        assertThat(rewriter.rewrite(body, Set.of("alerts"))).isEqualTo(body);
    }

    @Test
    void shadowedGlobalIsRewrittenAnyway() {
        String body = "DataStore<Person> alerts = other; alerts.update(t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, t, __match__,");
    }

    @Test
    void compactWithPlusUpdateIsRewritten() {
        String body = "p{age = 0}; alerts.update(p);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, p, __match__,");
    }

    @Test
    void compactWithMultipleAssignmentsPlusUpdateIsRewritten() {
        String body = "p{name = \"Reset\", age = 0}; alerts.update(p);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, p, __match__,");
    }

    @Test
    void compactWithAsUpdateArgIsRewritten() {
        String body = "alerts.update(t{status = RECEIVED});";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, t, __match__,");
        assertThat(result).contains("t{status = RECEIVED}");
    }

    @Test
    void compactWithMultipleAssignmentsAsUpdateArgIsRewritten() {
        String body = "alerts.update(t{status = RECEIVED, timestamp = new Date()});";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("DataStoreSupport.update(alerts, t, __match__,");
        assertThat(result).contains("t{status = RECEIVED, timestamp = new Date()}");
    }
}
