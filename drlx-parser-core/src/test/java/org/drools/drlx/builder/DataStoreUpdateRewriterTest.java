package org.drools.drlx.builder;

import java.util.Set;

import com.github.javaparser.JavaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataStoreUpdateRewriterTest {

    private DataStoreUpdateRewriter rewriter;

    @BeforeEach
    void setUp() {
        rewriter = new DataStoreUpdateRewriter(new JavaParser());
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

        assertThat(result).contains("alerts.update(");
        assertThat(result).contains("java.util.Objects.requireNonNull(alerts.lookup(t)");
        assertThat(result).contains("DataStore 'alerts' has no DataHandle");
        assertThat(result.replaceAll("\\s+", "")).contains(",t);");
    }

    @Test
    void updateOnFieldAccessExprArgIsRewritten() {
        String body = "alerts.update(this.t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).contains("alerts.lookup(this.t)");
        assertThat(result.replaceAll("\\s+", "")).contains(",this.t);");
    }

    @Test
    void updateWithComplexArgIsLeftUntouched() {
        String body = "alerts.update(getThing(t));";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result.replaceAll("\\s+", ""))
                .isEqualTo("alerts.update(getThing(t));");
        assertThat(result).doesNotContain("requireNonNull");
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

        long count = result.split("requireNonNull", -1).length - 1;
        assertThat(count).isEqualTo(2);
    }

    @Test
    void mixedRewritableAndNonRewritableUpdatesAreHandled() {
        String body = "alerts.update(t); alerts.update(getThing(u));";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        long count = result.split("requireNonNull", -1).length - 1;
        assertThat(count).isEqualTo(1);
        assertThat(result).contains("alerts.update(getThing(u))");
    }

    @Test
    void chainedScopeIsLeftUntouched() {
        String body = "alerts.add(t); getStore().update(t);";
        String result = rewriter.rewrite(body, Set.of("alerts"));

        assertThat(result).doesNotContain("requireNonNull");
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

        assertThat(result).contains("requireNonNull");
    }
}
