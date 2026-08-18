package org.drools.drlx.builder;

import java.nio.file.Path;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.FromExpressionIR;
import org.drools.drlx.builder.DrlxRuleAstModel.GroupElementIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.proto.DrlxRuleAstProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxRuleAstParseResultTest {

    @Test
    void evalIrRoundTripsThroughProto() {
        EvalIR original = new EvalIR("p.age > 30", List.of("p"));
        GroupElementIR andGroup = new GroupElementIR(
                GroupElementIR.Kind.AND,
                List.of(original));

        DrlxRuleAstProto.LhsItemParseResult proto = DrlxRuleAstParseResult.toProtoLhs(andGroup);
        LhsItemIR roundTripped = DrlxRuleAstParseResult.fromProtoLhs(proto, Path.of("test.drlx"));

        assertThat(roundTripped).isInstanceOf(GroupElementIR.class);
        GroupElementIR g = (GroupElementIR) roundTripped;
        assertThat(g.children()).hasSize(1);
        assertThat(g.children().get(0)).isInstanceOf(EvalIR.class);
        EvalIR e = (EvalIR) g.children().get(0);
        assertThat(e.expression()).isEqualTo("p.age > 30");
        assertThat(e.referencedBindings()).containsExactly("p");
    }

    @Test
    void evalIrWithMultipleBindingsRoundTrips() {
        EvalIR original = new EvalIR("p.age > q.age", List.of("p", "q"));
        DrlxRuleAstProto.LhsItemParseResult proto = DrlxRuleAstParseResult.toProtoLhs(
                new GroupElementIR(GroupElementIR.Kind.AND, List.of(original)));
        GroupElementIR g = (GroupElementIR) DrlxRuleAstParseResult.fromProtoLhs(
                proto, Path.of("test.drlx"));
        EvalIR e = (EvalIR) g.children().get(0);
        assertThat(e.referencedBindings()).containsExactly("p", "q");
    }

    @Test
    void passiveFlagRoundTripsThroughProto() {
        PatternIR ir = new PatternIR(
                "Person", "p", "persons",
                List.of("age > 18"),
                List.of(),
                null,
                List.of(),
                true,
                List.of(),
                null, null, List.of());

        // Serialise.
        DrlxRuleAstProto.LhsItemParseResult lhsItem = DrlxRuleAstParseResult.toProtoLhs(ir);
        DrlxRuleAstProto.PatternParseResult proto = lhsItem.getPattern();
        assertThat(proto.getPassive()).isTrue();

        // Deserialise.
        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.passive()).isTrue();
    }

    @Test
    void missingPassiveFieldDeserialisesToFalse() {
        DrlxRuleAstProto.PatternParseResult proto =
                DrlxRuleAstProto.PatternParseResult.newBuilder()
                        .setTypeName("Person")
                        .setBindName("p")
                        .setEntryPoint("persons")
                        .build();

        DrlxRuleAstProto.LhsItemParseResult lhsItem =
                DrlxRuleAstProto.LhsItemParseResult.newBuilder()
                        .setPattern(proto)
                        .build();

        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.passive()).isFalse();
    }

    @Test
    void watchedPropertiesRoundTripThroughProto() {
        PatternIR ir = new PatternIR(
                "ReactiveEmployee", "e", "reactiveEmployees",
                List.of("salary > 0"),
                List.of(),
                null,
                List.of(),
                false,
                List.of("basePay", "!bonusPay", "*"),
                null, null, List.of());

        DrlxRuleAstProto.LhsItemParseResult lhsItem = DrlxRuleAstParseResult.toProtoLhs(ir);
        DrlxRuleAstProto.PatternParseResult proto = lhsItem.getPattern();
        assertThat(proto.getWatchedPropertiesList()).containsExactly("basePay", "!bonusPay", "*");

        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.watchedProperties()).containsExactly("basePay", "!bonusPay", "*");
    }

    @Test
    void missingWatchedPropertiesDeserialisesToEmpty() {
        DrlxRuleAstProto.PatternParseResult proto =
                DrlxRuleAstProto.PatternParseResult.newBuilder()
                        .setTypeName("Person")
                        .setBindName("p")
                        .setEntryPoint("persons")
                        .build();

        DrlxRuleAstProto.LhsItemParseResult lhsItem =
                DrlxRuleAstProto.LhsItemParseResult.newBuilder()
                        .setPattern(proto)
                        .build();

        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.watchedProperties()).isEmpty();
    }

    @Test
    void accumulatePatternIrRoundTripsThroughProto() {
        DrlxRuleAstModel.PatternIR src = new DrlxRuleAstModel.PatternIR(
                "var", "p", "persons",
                List.of(), List.of(), null, List.of(), false, List.of(), null, null, List.of());
        DrlxRuleAstModel.AccumulatorIR acc1 = new DrlxRuleAstModel.AccumulatorIR(
                "var", "avgAge", "avg", List.of("p.age"), List.of("p"));
        DrlxRuleAstModel.AccumulatorIR acc2 = new DrlxRuleAstModel.AccumulatorIR(
                "long", "n", "count", List.of(), List.of());
        DrlxRuleAstModel.AccumulatePatternIR original =
                new DrlxRuleAstModel.AccumulatePatternIR(src, List.of(acc1, acc2));

        DrlxRuleAstProto.LhsItemParseResult proto = DrlxRuleAstParseResult.toProtoLhs(original);
        LhsItemIR roundTripped = DrlxRuleAstParseResult.fromProtoLhs(proto, Path.of("test.drlx"));

        assertThat(roundTripped).isInstanceOf(DrlxRuleAstModel.AccumulatePatternIR.class);
        DrlxRuleAstModel.AccumulatePatternIR back =
                (DrlxRuleAstModel.AccumulatePatternIR) roundTripped;
        assertThat(((DrlxRuleAstModel.PatternIR) back.source()).bindName()).isEqualTo("p");
        assertThat(back.accumulators()).hasSize(2);
        assertThat(back.accumulators().get(0).functionName()).isEqualTo("avg");
        assertThat(back.accumulators().get(0).resultBindName()).isEqualTo("avgAge");
        assertThat(back.accumulators().get(0).resultTypeName()).isEqualTo("var");
        assertThat(back.accumulators().get(0).argExpressions()).containsExactly("p.age");
        assertThat(back.accumulators().get(0).referencedBindings()).containsExactly("p");
        assertThat(back.accumulators().get(1).functionName()).isEqualTo("count");
        assertThat(back.accumulators().get(1).resultTypeName()).isEqualTo("long");
        assertThat(back.accumulators().get(1).argExpressions()).isEmpty();
        assertThat(back.accumulators().get(1).referencedBindings()).isEmpty();
    }

    @Test
    void windowFieldsRoundTripThroughProto() {
        PatternIR ir = new PatternIR(
                "Withdrawal", "w", "withdrawals",
                List.of(),
                List.of(),
                null,
                List.of(),
                false,
                List.of(),
                "time", "5s", List.of());

        DrlxRuleAstProto.LhsItemParseResult lhsItem = DrlxRuleAstParseResult.toProtoLhs(ir);
        DrlxRuleAstProto.PatternParseResult proto = lhsItem.getPattern();
        assertThat(proto.getWindowType()).isEqualTo("time");
        assertThat(proto.getWindowParameter()).isEqualTo("5s");

        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.windowType()).isEqualTo("time");
        assertThat(back.windowParameter()).isEqualTo("5s");
    }

    @Test
    void missingWindowFieldsDeserialiseToNull() {
        DrlxRuleAstProto.PatternParseResult proto =
                DrlxRuleAstProto.PatternParseResult.newBuilder()
                        .setTypeName("Person")
                        .setBindName("p")
                        .setEntryPoint("persons")
                        .build();

        DrlxRuleAstProto.LhsItemParseResult lhsItem =
                DrlxRuleAstProto.LhsItemParseResult.newBuilder()
                        .setPattern(proto)
                        .build();

        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(lhsItem, Path.of("test"));
        assertThat(back.windowType()).isNull();
        assertThat(back.windowParameter()).isNull();
    }

    @Test
    void oopathSegmentsRoundTripThroughProto() {
        PatternIR ir = new PatternIR(
                "var", "addr", "persons",
                List.of("age > 18"),
                List.of(),
                null,
                List.of(),
                false,
                List.of(),
                null, null,
                List.of(
                    new DrlxRuleAstModel.OopathSegmentIR("addresses",
                            List.of("city == \"London\""), null),
                    new DrlxRuleAstModel.OopathSegmentIR("streets",
                            List.of(), "MainStreet")
                ));

        DrlxRuleAstProto.LhsItemParseResult lhsItem = DrlxRuleAstParseResult.toProtoLhs(ir);
        PatternIR back = (PatternIR) DrlxRuleAstParseResult.fromProtoLhs(
                lhsItem, Path.of("test.drlx"));

        assertThat(back.entryPoint()).isEqualTo("persons");
        assertThat(back.conditions()).containsExactly("age > 18");
        assertThat(back.oopathSegments()).hasSize(2);
        assertThat(back.oopathSegments().get(0).field()).isEqualTo("addresses");
        assertThat(back.oopathSegments().get(0).conditions())
                .containsExactly("city == \"London\"");
        assertThat(back.oopathSegments().get(0).inlineCast()).isNull();
        assertThat(back.oopathSegments().get(1).field()).isEqualTo("streets");
        assertThat(back.oopathSegments().get(1).conditions()).isEmpty();
        assertThat(back.oopathSegments().get(1).inlineCast()).isEqualTo("MainStreet");
    }

    @Test
    void fromExpressionIrRoundTripsThroughProto() {
        FromExpressionIR original = new FromExpressionIR(
                "var", "i", "new int[] {1, 2, 3}", List.of());
        DrlxRuleAstProto.LhsItemParseResult proto = DrlxRuleAstParseResult.toProtoLhs(original);
        LhsItemIR roundTripped = DrlxRuleAstParseResult.fromProtoLhs(proto, Path.of("test.drlx"));

        assertThat(roundTripped).isInstanceOf(FromExpressionIR.class);
        FromExpressionIR fe = (FromExpressionIR) roundTripped;
        assertThat(fe.typeName()).isEqualTo("var");
        assertThat(fe.bindName()).isEqualTo("i");
        assertThat(fe.fromExpression()).isEqualTo("new int[] {1, 2, 3}");
        assertThat(fe.referencedBindings()).isEmpty();
    }

    @Test
    void fromExpressionIrWithBindingsRoundTrips() {
        FromExpressionIR original = new FromExpressionIR(
                "var", "addrs", "p.getAddresses()", List.of("p"));
        DrlxRuleAstProto.LhsItemParseResult proto = DrlxRuleAstParseResult.toProtoLhs(original);
        FromExpressionIR fe = (FromExpressionIR) DrlxRuleAstParseResult.fromProtoLhs(
                proto, Path.of("test.drlx"));
        assertThat(fe.fromExpression()).isEqualTo("p.getAddresses()");
        assertThat(fe.referencedBindings()).containsExactly("p");
    }
}
