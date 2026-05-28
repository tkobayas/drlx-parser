package org.drools.drlx.builder;

import java.util.List;

import org.drools.model.functions.temporal.AfterPredicate;
import org.drools.model.functions.temporal.BeforePredicate;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalPredicateFactoryTest {

    @Test
    void afterNoParams() {
        TemporalPredicate p = TemporalPredicateFactory.create("after", false, List.of());
        assertThat(p).isInstanceOf(AfterPredicate.class);
        assertThat(p.evaluate(2000, 0, 2000, 0, 0, 0)).isTrue();
        assertThat(p.isNegated()).isFalse();
    }

    @Test
    void afterWithTwoParams() {
        TemporalPredicate p = TemporalPredicateFactory.create("after", false, List.of("3s", "5s"));
        assertThat(p.evaluate(4000, 0, 4000, 0, 0, 0)).isTrue();
        assertThat(p.evaluate(1000, 0, 1000, 0, 0, 0)).isFalse();
    }

    @Test
    void beforeNoParams() {
        TemporalPredicate p = TemporalPredicateFactory.create("before", false, List.of());
        assertThat(p).isInstanceOf(BeforePredicate.class);
    }

    @Test
    void negatedAfter() {
        TemporalPredicate p = TemporalPredicateFactory.create("after", true, List.of());
        assertThat(p.isNegated()).isTrue();
    }

    @Test
    void meetsRejectsTwoParams() {
        assertThatThrownBy(() ->
            TemporalPredicateFactory.create("meets", false, List.of("1s", "2s")))
            .hasMessageContaining("accepts 0 or 1 parameters");
    }

    @Test
    void unknownOperatorThrows() {
        assertThatThrownBy(() ->
            TemporalPredicateFactory.create("bogus", false, List.of()))
            .hasMessageContaining("Unknown temporal operator");
    }
}
