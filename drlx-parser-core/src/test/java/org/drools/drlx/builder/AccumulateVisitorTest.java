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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.assertj.core.groups.Tuple;
import org.drools.drlx.parser.DrlxLexer;
import org.drools.drlx.parser.DrlxParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(accPat.source().bindName()).isEqualTo("p");
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

    private static DrlxRuleAstModel.RuleIR parseRule(String drlx) {
        DrlxLexer lexer = new DrlxLexer(CharStreams.fromString(drlx));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlxParser parser = new DrlxParser(tokens);
        DrlxParser.DrlxCompilationUnitContext ctx = parser.drlxStart().drlxCompilationUnit();
        var unit = new DrlxToRuleAstVisitor(tokens).visitDrlxCompilationUnit(ctx);
        return unit.rules().get(0);
    }
}
