package org.drools.drlx.builder;

import java.nio.file.Path;
import java.util.List;

import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.proto.DrlxRuleAstProto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxRuleAstParseResultTest {

    @Test
    void passiveFlagRoundTripsThroughProto() {
        PatternIR ir = new PatternIR(
                "Person", "p", "persons",
                List.of("age > 18"),
                null,
                List.of(),
                true,
                List.of());

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
                null,
                List.of(),
                false,
                List.of("basePay", "!bonusPay", "*"));

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
}
