/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

package org.drools.drlx.builder;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.assertj.core.groups.Tuple;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class AccumulateVisitorTest {

    @Test
    void foldsBoundOopathAndOneAccumulateIntoAccumulatePatternIR() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var p : /persons,
                    var avgAge = avg(p.age),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var item = rule.lhs().get(0);
        assertThat(item).isInstanceOf(DrlxRuleAstModel.AccumulatePatternIR.class);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) item;
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).bindName()).isEqualTo("p");
        assertThat(accPat.accumulators()).hasSize(1);
        var acc = accPat.accumulators().get(0);
        assertThat(acc.resultBindName()).isEqualTo("avgAge");
        assertThat(acc.resultTypeName()).isEqualTo("var");
        assertThat(acc.functionName()).isEqualTo("avg");
        assertThat(acc.argExpressions()).containsExactly("p.age");
        assertThat(acc.referencedBindings()).contains("p");
    }

    @Test
    void foldsThreeAccumulatorsSharingOneSource() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var p : /persons,
                    var avgAge = avg(p.age),
                    var minAge = min(p.age),
                    var maxAge = max(p.age),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(accPat.accumulators())
                .extracting(DrlxRuleAstModel.AccumulatorIR::resultBindName,
                            DrlxRuleAstModel.AccumulatorIR::functionName)
                .containsExactly(
                        Tuple.tuple("avgAge", "avg"),
                        Tuple.tuple("minAge", "min"),
                        Tuple.tuple("maxAge", "max"));
    }

    @Test
    void countWithNoArgumentHasEmptyArgList() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var p : /persons,
                    long n = count(),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        var acc = accPat.accumulators().get(0);
        assertThat(acc.functionName()).isEqualTo("count");
        assertThat(acc.argExpressions()).isEmpty();
        assertThat(acc.referencedBindings()).isEmpty();
        assertThat(acc.resultTypeName()).isEqualTo("long");
    }

    @Test
    void qualifiedFunctionNamePreservedVerbatim() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var p : /persons,
                    var avgAge = Func.avg(p.age),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(accPat.accumulators().get(0).functionName()).isEqualTo("Func.avg");
    }

    @Test
    void inlineFromBareOopathCountHasEmptyArgs() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    long n = count(/persons),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).bindName()).isEqualTo("$inline0");
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).entryPoint()).isEqualTo("persons");
        var acc = accPat.accumulators().get(0);
        assertThat(acc.functionName()).isEqualTo("count");
        assertThat(acc.argExpressions()).isEmpty();
        assertThat(acc.referencedBindings()).isEmpty();
        assertThat(acc.resultTypeName()).isEqualTo("long");
    }

    @Test
    void multipleInlineFromProduceSeparateAccumulatePatterns() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var minAge = min(/persons.age),
                    var maxAge = max(/persons.age),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(2);

        var accPat0 = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(((DrlxRuleAstModel.PatternIR) accPat0.source()).bindName()).isEqualTo("$inline0");
        assertThat(accPat0.accumulators().get(0).functionName()).isEqualTo("min");
        assertThat(accPat0.accumulators().get(0).argExpressions()).containsExactly("$inline0.age");

        var accPat1 = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(1);
        assertThat(((DrlxRuleAstModel.PatternIR) accPat1.source()).bindName()).isEqualTo("$inline1");
        assertThat(accPat1.accumulators().get(0).functionName()).isEqualTo("max");
        assertThat(accPat1.accumulators().get(0).argExpressions()).containsExactly("$inline1.age");
    }

    @Test
    void inlineFromCountWithFinalDotRejectedAtVisitor() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    long n = count(/persons.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("function 'count' does not accept a final-dot extractor");
    }

    @Test
    void inlineFromParsesToAccumulatePatternWithSyntheticSource() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    var avgAge = avg(/persons.age),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var item = rule.lhs().get(0);
        assertThat(item).isInstanceOf(DrlxRuleAstModel.AccumulatePatternIR.class);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) item;
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).bindName()).startsWith("$inline");
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).entryPoint()).isEqualTo("persons");
        assertThat(accPat.accumulators()).hasSize(1);
        var acc = accPat.accumulators().get(0);
        assertThat(acc.functionName()).isEqualTo("avg");
        assertThat(acc.argExpressions()).containsExactly("$inline0.age");
        assertThat(acc.referencedBindings()).containsExactly("$inline0");
    }

    // --- protobuf round-trip tests ---

    @Test
    void customAccumulateIRProtobufRoundTrip() {
        var original = new DrlxRuleAstModel.CustomAccumulateIR(
                new DrlxRuleAstModel.PatternIR("var", "p", "persons", List.of(), null, List.of(), false, List.of()),
                List.of(
                        new DrlxRuleAstModel.InitVarIR("int", "count", "0"),
                        new DrlxRuleAstModel.InitVarIR("int", "total", "0")),
                "total += p.age; count = count + 1;",
                "total -= p.age; count = count - 1;",
                "double",
                "avgAge",
                "(double) total / count",
                List.of("p", "total", "count"));

        var protoLhs = DrlxRuleAstParseResult.toProtoLhs(original);
        var roundTripped = DrlxRuleAstParseResult.fromProtoLhs(protoLhs, java.nio.file.Path.of("test"));

        assertThat(roundTripped).isInstanceOf(DrlxRuleAstModel.CustomAccumulateIR.class);
        var rt = (DrlxRuleAstModel.CustomAccumulateIR) roundTripped;
        assertThat(((DrlxRuleAstModel.PatternIR) rt.source()).bindName()).isEqualTo("p");
        assertThat(rt.initVars()).hasSize(2);
        assertThat(rt.initVars().get(0).name()).isEqualTo("count");
        assertThat(rt.initVars().get(1).name()).isEqualTo("total");
        assertThat(rt.actionBlock()).isEqualTo("total += p.age; count = count + 1;");
        assertThat(rt.reverseBlock()).isEqualTo("total -= p.age; count = count - 1;");
        assertThat(rt.resultTypeName()).isEqualTo("double");
        assertThat(rt.resultBindName()).isEqualTo("avgAge");
        assertThat(rt.resultExpression()).isEqualTo("(double) total / count");
        assertThat(rt.referencedBindings()).containsExactly("p", "total", "count");
    }

    @Test
    void customAccumulateIRProtobufRoundTripNullReverse() {
        var original = new DrlxRuleAstModel.CustomAccumulateIR(
                new DrlxRuleAstModel.PatternIR("var", "p", "persons", List.of(), null, List.of(), false, List.of()),
                List.of(new DrlxRuleAstModel.InitVarIR("int", "s", "0")),
                "s = s + p.age",
                null,
                "int",
                "sum",
                "s",
                List.of("p", "s"));

        var protoLhs = DrlxRuleAstParseResult.toProtoLhs(original);
        var roundTripped = DrlxRuleAstParseResult.fromProtoLhs(protoLhs, java.nio.file.Path.of("test"));

        var rt = (DrlxRuleAstModel.CustomAccumulateIR) roundTripped;
        assertThat(rt.reverseBlock()).isNull();
        assertThat(rt.actionBlock()).isEqualTo("s = s + p.age");
    }

    // --- acc() keyword form tests ---

    @Test
    void accKeyword3ParamProducesCustomAccumulateIR() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = 0;,
                        s = s + p.age,
                        int sum = s),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        assertThat(rule.lhs().get(0)).isInstanceOf(DrlxRuleAstModel.CustomAccumulateIR.class);
        var custom = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(((DrlxRuleAstModel.PatternIR) custom.source()).bindName()).isEqualTo("p");
        assertThat(custom.initVars()).hasSize(1);
        assertThat(custom.initVars().get(0).typeName()).isEqualTo("int");
        assertThat(custom.initVars().get(0).name()).isEqualTo("s");
        assertThat(custom.initVars().get(0).initializer()).isEqualTo("0");
        assertThat(custom.actionBlock()).isEqualTo("s = s + p.age");
        assertThat(custom.reverseBlock()).isNull();
        assertThat(custom.resultTypeName()).isEqualTo("int");
        assertThat(custom.resultBindName()).isEqualTo("sum");
        assertThat(custom.resultExpression()).isEqualTo("s");
    }

    @Test
    void accKeyword2ParamSingleFunctionProducesAccumulatePatternIR() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        var avgAge = avg(p.age)),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        assertThat(rule.lhs().get(0)).isInstanceOf(DrlxRuleAstModel.AccumulatePatternIR.class);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(((DrlxRuleAstModel.PatternIR) accPat.source()).bindName()).isEqualTo("p");
        assertThat(accPat.accumulators()).hasSize(1);
        assertThat(accPat.accumulators().get(0).functionName()).isEqualTo("avg");
    }

    @Test
    void accKeyword2ParamGroupedFunctions() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        (var maxAge = max(p.age),
                         var minAge = min(p.age))),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        assertThat(rule.lhs()).hasSize(1);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(accPat.accumulators()).hasSize(2);
        assertThat(accPat.accumulators().get(0).functionName()).isEqualTo("max");
        assertThat(accPat.accumulators().get(1).functionName()).isEqualTo("min");
    }

    @Test
    void accKeyword3ParamWithPairedActionReverse() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = 0;,
                        (s = s + p.age, s = s - p.age),
                        int sum = s),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var custom = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(custom.actionBlock()).isEqualTo("s = s + p.age");
        assertThat(custom.reverseBlock()).isEqualTo("s = s - p.age");
    }

    @Test
    void accKeyword5ParamWithBracedBlocks() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        { int count = 0; int total = 0; },
                        { total += p.age; count++; },
                        { total -= p.age; count--; },
                        double avgAge = total / count),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var custom = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(((DrlxRuleAstModel.PatternIR) custom.source()).bindName()).isEqualTo("p");
        assertThat(custom.initVars()).hasSize(2);
        assertThat(custom.initVars().get(0)).isEqualTo(new DrlxRuleAstModel.InitVarIR("int", "count", "0"));
        assertThat(custom.initVars().get(1)).isEqualTo(new DrlxRuleAstModel.InitVarIR("int", "total", "0"));
        assertThat(custom.reverseBlock()).isNotNull();
        assertThat(custom.resultTypeName()).isEqualTo("double");
        assertThat(custom.resultBindName()).isEqualTo("avgAge");
    }

    @Test
    void accKeywordRejectsNonAccIdentifier() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    notAcc(var p : /persons,
                        int s = 0;,
                        s = s + p.age,
                        int sum = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void accKeywordRejectsVarInInitVars() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        var s = 0;,
                        s = s + p.age,
                        int sum = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("var");
    }

    @Test
    void accKeywordRejectsInitVarNameCollision() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int p = 0;,
                        p = p + 1,
                        int sum = p),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("conflicts with source binding name");
    }

    @Test
    void accKeywordRejectsDuplicateInitVarNames() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        { int x = 0; int x = 1; },
                        x = x + p.age,
                        int sum = x),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("duplicate init var name 'x'");
    }

    @Test
    void accKeywordRejectsSourceRefInResult() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = 0;,
                        s = s + p.age,
                        int sum = p),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("result expression cannot reference source binding");
    }

    @Test
    void accKeywordRejectsPairedBlockIn5Param() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = 0;,
                        (s = s + p.age, s = s - p.age),
                        s = s - p.age,
                        int sum = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("paired (action, reverse) block is not valid in 5-param acc");
    }

    @Test
    void accKeywordRejectsNullForPrimitiveInit() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = null;,
                        s = s + p.age,
                        int sum = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot assign null to primitive type int");
    }

    @Test
    void accKeywordRejectsNarrowingLiteral() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int s = 0L;,
                        s = s + p.age,
                        int sum = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot assign long literal to int");
    }

    @Test
    void accKeywordRejectsComplexInitializer() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        String s = new String();,
                        s = p.name,
                        String result = s),
                    do {}
                }
                """;
        assertThatThrownBy(() -> parseRule(drlx))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("complex initializers are not yet supported");
    }

    @Test
    void accKeywordMultiDeclaratorSplitsIntoSeparateInitVars() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int a = 0, b = 1;,
                        a = a + p.age,
                        int sum = a),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var custom = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(custom.initVars()).hasSize(2);
        assertThat(custom.initVars().get(0).name()).isEqualTo("a");
        assertThat(custom.initVars().get(0).initializer()).isEqualTo("0");
        assertThat(custom.initVars().get(1).name()).isEqualTo("b");
        assertThat(custom.initVars().get(1).initializer()).isEqualTo("1");
    }

    @Test
    void accKeywordMissingInitializerUsesDefault() {
        String drlx = """
                package p;
                unit MyUnit;
                rule R1 {
                    acc(var p : /persons,
                        int a;,
                        a = a + p.age,
                        int sum = a),
                    do {}
                }
                """;
        var rule = parseRule(drlx);
        var custom = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(custom.initVars()).hasSize(1);
        assertThat(custom.initVars().get(0).initializer()).isEqualTo("0");
    }

    @Test
    void accKeyword2ParamWithAndSourceProducesGroupElementSource() {
        DrlxRuleAstModel.RuleIR rule = parseRule("""
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        var total = sum(o.amount)),
                    do { results.add(total); }
                }
                """);
        assertThat(rule.lhs()).hasSize(1);
        var accPat = (DrlxRuleAstModel.AccumulatePatternIR) rule.lhs().get(0);
        assertThat(accPat.source()).isInstanceOf(DrlxRuleAstModel.GroupElementIR.class);
        var group = (DrlxRuleAstModel.GroupElementIR) accPat.source();
        assertThat(group.kind()).isEqualTo(DrlxRuleAstModel.GroupElementIR.Kind.AND);
        assertThat(group.children()).hasSize(2);
        assertThat(group.children().get(0)).isInstanceOf(DrlxRuleAstModel.PatternIR.class);
        assertThat(group.children().get(1)).isInstanceOf(DrlxRuleAstModel.PatternIR.class);
        assertThat(((DrlxRuleAstModel.PatternIR) group.children().get(0)).bindName()).isEqualTo("p");
        assertThat(((DrlxRuleAstModel.PatternIR) group.children().get(1)).bindName()).isEqualTo("o");
        assertThat(accPat.accumulators()).hasSize(1);
        assertThat(accPat.accumulators().get(0).functionName()).isEqualTo("sum");
    }

    @Test
    void accKeyword3ParamWithAndSourceProducesGroupElementSource() {
        DrlxRuleAstModel.RuleIR rule = parseRule("""
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        int s = 0;,
                        s = s + o.amount,
                        int total = s),
                    do { results.add(total); }
                }
                """);
        assertThat(rule.lhs()).hasSize(1);
        var customAcc = (DrlxRuleAstModel.CustomAccumulateIR) rule.lhs().get(0);
        assertThat(customAcc.source()).isInstanceOf(DrlxRuleAstModel.GroupElementIR.class);
        var group = (DrlxRuleAstModel.GroupElementIR) customAcc.source();
        assertThat(group.kind()).isEqualTo(DrlxRuleAstModel.GroupElementIR.Kind.AND);
        assertThat(group.children()).hasSize(2);
    }

    @Test
    void accumulatePatternWithAndSourceProtobufRoundTrip() {
        var src = new DrlxRuleAstModel.GroupElementIR(
                DrlxRuleAstModel.GroupElementIR.Kind.AND,
                List.of(
                        new DrlxRuleAstModel.PatternIR("var", "p", "persons", List.of(), null, List.of(), false, List.of()),
                        new DrlxRuleAstModel.PatternIR("var", "o", "orders", List.of("customerId == p.age"), null, List.of(), false, List.of())));
        var acc = new DrlxRuleAstModel.AccumulatorIR("var", "total", "sum", List.of("o.amount"), List.of("o"));
        var original = new DrlxRuleAstModel.AccumulatePatternIR(src, List.of(acc));

        var protoLhs = DrlxRuleAstParseResult.toProtoLhs(original);
        var roundTripped = DrlxRuleAstParseResult.fromProtoLhs(protoLhs, java.nio.file.Path.of("test"));

        assertThat(roundTripped).isInstanceOf(DrlxRuleAstModel.AccumulatePatternIR.class);
        var back = (DrlxRuleAstModel.AccumulatePatternIR) roundTripped;
        assertThat(back.source()).isInstanceOf(DrlxRuleAstModel.GroupElementIR.class);
        var group = (DrlxRuleAstModel.GroupElementIR) back.source();
        assertThat(group.kind()).isEqualTo(DrlxRuleAstModel.GroupElementIR.Kind.AND);
        assertThat(group.children()).hasSize(2);
        assertThat(((DrlxRuleAstModel.PatternIR) group.children().get(0)).bindName()).isEqualTo("p");
        assertThat(((DrlxRuleAstModel.PatternIR) group.children().get(1)).bindName()).isEqualTo("o");
        assertThat(back.accumulators()).hasSize(1);
        assertThat(back.accumulators().get(0).functionName()).isEqualTo("sum");
    }

    private static DrlxRuleAstModel.RuleIR parseRule(String drlx) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(drlx));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxStart().drlxCompilationUnit();
        var unit = new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx);
        return unit.rules().get(0);
    }
}
