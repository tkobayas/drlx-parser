package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxRuleAstModelTest {

    @Test
    void evalIrIsLhsItem() {
        EvalIR eval = new EvalIR("p.age > 30", List.of("p"));
        LhsItemIR item = eval;   // sealed-interface assignment must compile
        assertThat(item).isInstanceOf(EvalIR.class);
        assertThat(eval.expression()).isEqualTo("p.age > 30");
        assertThat(eval.referencedBindings()).containsExactly("p");
    }

    @Test
    void evalIrReferencedBindingsListIsImmutable() {
        ArrayList<String> mutable = new ArrayList<>(List.of("p"));
        EvalIR eval = new EvalIR("p.age > 30", mutable);
        mutable.add("q");
        assertThat(eval.referencedBindings()).containsExactly("p");   // unaffected by mutation
    }
}
