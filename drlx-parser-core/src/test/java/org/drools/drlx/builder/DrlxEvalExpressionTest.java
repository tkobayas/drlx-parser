package org.drools.drlx.builder;

import org.drools.base.rule.accessor.EvalExpression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrlxEvalExpressionTest {

    @Test
    void implementsEvalExpressionAndEvaluatorSink() {
        DrlxEvalExpression expr = new DrlxEvalExpression("p.age > 30", null);
        assertThat(expr).isInstanceOf(EvalExpression.class);
        assertThat(expr).isInstanceOf(EvaluatorSink.class);
        assertThat(expr.getExpression()).isEqualTo("p.age > 30");
    }

    @Test
    void cloneReturnsNonNullInstance() {
        DrlxEvalExpression expr = new DrlxEvalExpression("true", null);
        DrlxEvalExpression clone = expr.clone();
        assertThat(clone).isNotNull();
        assertThat(clone.getExpression()).isEqualTo("true");
    }

    @Test
    void evaluateBeforeBindingThrows() {
        DrlxEvalExpression expr = new DrlxEvalExpression("p.age > 30", null);
        assertThatThrownBy(() -> expr.evaluate(null, new org.drools.base.rule.Declaration[0], null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet bound");
    }
}
