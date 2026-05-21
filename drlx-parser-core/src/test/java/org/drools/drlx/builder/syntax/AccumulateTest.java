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

package org.drools.drlx.builder.syntax;

import java.util.ArrayList;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.extractors.ArrayElementReader;
import org.drools.base.base.extractors.SelfReferenceClassFieldReader;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.MultiAccumulate;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.SingleAccumulate;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Order;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccumulateTest extends DrlxBuilderTestSupport {

    @Test
    void singleAvgOverPersons() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule AvgRule {
                    var p : /persons,
                    var avgAge = avg(p.age),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(40.0);  // avg(20, 40, 60) = 40.0
    }

    @Test
    void multiFunctionMinMaxAvg() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var minAge = min(p.age),
                    var maxAge = max(p.age),
                    var avgAge = avg(p.age),
                    do { results.add(minAge); results.add(maxAge); results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(20, 60, 40.0);
    }

    @Test
    void countWithNoArgument() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    long n = count(),
                    do { results.add(n); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(2L);
    }

    @Test
    void sumOverIntegerField() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var total = sum(p.age),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 10));
            entryPoint.insert(new Person("B", 30));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        // Drools' SumAccumulateFunction normalises to Double regardless of input type.
        assertThat(observed).containsExactly(100.0);  // sum(10,30,60) = 100.0
    }

    @Test
    void customFunctionSumSquares() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.acc.TestAccFuncs;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var ss = TestAccFuncs.sumSquares(p.age),
                    do { results.add(ss); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 3));
            entryPoint.insert(new Person("B", 4));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(25.0);  // 3² + 4² = 9 + 16 = 25.0
    }

    @Test
    void customFunctionMultiAccumulate() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.acc.TestAccFuncs;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var ss = TestAccFuncs.sumSquares(p.age),
                    var dc = TestAccFuncs.doubleCount(p.age),
                    do { results.add(ss); results.add(dc); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 3));
            entryPoint.insert(new Person("B", 4));
            entryPoint.insert(new Person("C", 5));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(50.0, 6L);  // 9+16+25=50.0, count=3 × 2=6
    }

    @Test
    void mixedBuiltinAndCustomFunction() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.acc.TestAccFuncs;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var avgAge = avg(p.age),
                    var ss = TestAccFuncs.sumSquares(p.age),
                    do { results.add(avgAge); results.add(ss); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 3));
            entryPoint.insert(new Person("B", 4));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(3.5, 25.0);  // avg(3,4)=3.5, 9+16=25.0
    }

    @Test
    void customFunctionClassNotFound() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var ss = NoSuchClass.sumSquares(p.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot resolve accumulate function class")
                .hasMessageContaining("NoSuchClass");
    }

    @Test
    void customFunctionFieldNotFound() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.acc.TestAccFuncs;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var ss = TestAccFuncs.noSuchField(p.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("has no static AccumulateFunction field")
                .hasMessageContaining("noSuchField");
    }

    @Test
    void customFunctionFieldWrongType() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.acc.BadContainer;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var ss = BadContainer.notAFunction(p.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("is not an AccumulateFunction");
    }

    @Test
    void unknownFunctionRejected() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var x = bogus(p.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown accumulate function 'bogus'")
                .hasMessageContaining("avg, sum, min, max, count");
    }

    @Test
    void sourceBindingNotVisibleAfterAccumulate() {
        // `p` is internal to the accumulate; referencing it in the consequence
        // must fail at compile time. The exact message comes from the MVEL3
        // lambda compiler — we only assert that a RuntimeException is raised.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var avgAge = avg(p.age),
                    do { results.add(p); }
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sumOfArithmeticExpression() {
        // After #48: arbitrary MVEL3 expressions over the source binding are
        // accepted. This test was the v1-limit contract — flipped here from
        // assertion-of-rejection to assertion-of-success.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var total = sum(p.age + 1),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 10));
            entryPoint.insert(new Person("B", 30));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        // sum((10+1) + (30+1) + (60+1)) = 11 + 31 + 61 = 103
        // SumAccumulateFunction normalises to Double.
        assertThat(observed).containsExactly(103.0);
    }

    @Test
    void sumOfMethodCallExpression() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var total = sum(p.name.length()),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("AA", 10));    // length 2
            entryPoint.insert(new Person("BBB", 20));   // length 3
            entryPoint.insert(new Person("CCCC", 30));  // length 4
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(9.0);  // 2 + 3 + 4 = 9
    }

    @Test
    void sumOfMultipleBindingRefs() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var total = sum(p.age * p.age),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 2));
            entryPoint.insert(new Person("B", 3));
            entryPoint.insert(new Person("C", 4));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(29.0);  // 4 + 9 + 16 = 29
    }

    @Test
    void avgOfExpression() {
        // Confirms non-sum functions also pick up the new MVEL3 extractor path.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var avgPlus = avg(p.age + 1),
                    do { results.add(avgPlus); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(41.0);  // avg(21, 41, 61) = 41.0
    }

    @Test
    void extractorExpressionWithUnknownPropertyFailsAtBuild() {
        // An extractor expression that references a non-existent property must
        // fail at build time (MVEL3 raises during batch compile). We assert on
        // RuntimeException only — not on MVEL3's specific message, which would
        // be brittle across MVEL3 versions.
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var total = sum(p.notAField),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void inlineFromAvg() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var avgAge = avg(/persons.age),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(40.0);
    }

    @Test
    void inlineFromCount() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    long n = count(/persons),
                    do { results.add(n); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(2L);
    }

    @Test
    void inlineFromMultipleSameSource() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var minAge = min(/persons.age),
                    var maxAge = max(/persons.age),
                    var avgAge = avg(/persons.age),
                    do { results.add(minAge); results.add(maxAge); results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(20, 60, 40.0);
    }

    @Test
    void inlineFromWithSourceConstraint() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var totalSenior = sum(/persons[age >= 40].age),
                    do { results.add(totalSenior); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(100.0);
    }

    @Test
    void inlineFromComposesWithBoundPattern() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons[age >= 18],
                    long n = count(/persons),
                    do { results.add(p.getName()); results.add(n); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("Alice", 20));
            entryPoint.insert(new Person("Bob", 40));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactlyInAnyOrder("Alice", 2L, "Bob", 2L);
    }

    @Test
    void inlineFromSynthesisesSourcePattern() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var avgAge = avg(/persons.age),
                    do {}
                }
                """;
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        final Pattern wrap = accumulateResultPattern(kieBase, "org.drools.drlx.parser", "R");

        assertThat(wrap.getSource()).isInstanceOf(SingleAccumulate.class);
        SingleAccumulate single = (SingleAccumulate) wrap.getSource();
        Pattern srcPattern = (Pattern) single.getSource();
        assertThat(srcPattern.getDeclaration().getIdentifier()).startsWith("$inline");
        assertThat(wrap.getDeclarations()).hasSize(1).containsKey("avgAge");
    }

    @Test
    void inlineFromMultipleEmitsSeparateSingleAccumulates() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var minAge = min(/persons.age),
                    var maxAge = max(/persons.age),
                    do {}
                }
                """;
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        RuleImpl impl = (RuleImpl) kieBase.getKiePackage("org.drools.drlx.parser")
                .getRules().stream().filter(r -> r.getName().equals("R")).findFirst().orElseThrow();
        java.util.List<Pattern> accPatterns = impl.getLhs().getChildren().stream()
                .filter(Pattern.class::isInstance)
                .map(Pattern.class::cast)
                .filter(p -> p.getSource() instanceof SingleAccumulate)
                .toList();

        assertThat(accPatterns).hasSize(2);

        SingleAccumulate s0 = (SingleAccumulate) accPatterns.get(0).getSource();
        SingleAccumulate s1 = (SingleAccumulate) accPatterns.get(1).getSource();
        String id0 = ((Pattern) s0.getSource()).getDeclaration().getIdentifier();
        String id1 = ((Pattern) s1.getSource()).getDeclaration().getIdentifier();
        assertThat(id0).isNotEqualTo(id1);
        assertThat(id0).startsWith("$inline");
        assertThat(id1).startsWith("$inline");

        assertThat(accPatterns.get(0).getDeclarations()).containsKey("minAge");
        assertThat(accPatterns.get(1).getDeclarations()).containsKey("maxAge");
    }

    @Test
    void inlineFromCountWithFinalDotRejected() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    long n = count(/persons.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("function 'count' does not accept a final-dot extractor");
    }

    @Test
    void inlineFromBareOopathRejectedForNonZeroArg() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var avgAge = avg(/persons),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("requires exactly 1 argument, got 0");
    }

    @Test
    void inlineFromUnknownPropertyFailsAtBuild() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var avgAge = avg(/persons.nope),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class);
    }

    /** Walk the rule's LHS to find the single Pattern whose source is an Accumulate. */
    private static Pattern accumulateResultPattern(KieBase kieBase, String pkg, String ruleName) {
        RuleImpl impl = (RuleImpl) kieBase.getKiePackage(pkg).getRules().stream()
                .filter(r -> r.getName().equals(ruleName))
                .findFirst()
                .orElseThrow();
        GroupElement lhs = impl.getLhs();
        return lhs.getChildren().stream()
                .filter(Pattern.class::isInstance)
                .map(Pattern.class::cast)
                .filter(p -> p.getSource() instanceof org.drools.base.rule.Accumulate)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no accumulate result Pattern under rule " + ruleName));
    }

    @Test
    void singleFunctionEmitsSingleAccumulate() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var minAge = min(p.age),
                    do {}
                }
                """;
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        final Pattern wrap = accumulateResultPattern(kieBase, "org.drools.drlx.parser", "R");

        assertThat(wrap.getSource()).isInstanceOf(SingleAccumulate.class);
        // resultClassFor(min) returns the registry's resultType for var-bindings;
        // AccumulateFunctionRegistry maps min/max -> Comparable.class.
        assertThat(((ClassObjectType) wrap.getObjectType()).getClassType())
                .isEqualTo(Comparable.class);
        assertThat(wrap.getDeclarations()).hasSize(1).containsKey("minAge");
        final Declaration d = wrap.getDeclarations().get("minAge");
        assertThat(d.getExtractor()).isInstanceOf(SelfReferenceClassFieldReader.class);
        assertThat(d.getExtractor().getExtractToClass()).isEqualTo(Comparable.class);
    }

    @Test
    void multiFunctionEmitsOneMultiAccumulate() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var minAge = min(p.age),
                    var maxAge = max(p.age),
                    var avgAge = avg(p.age),
                    do {}
                }
                """;
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        final java.util.List<Pattern> patterns = ((RuleImpl) kieBase.getKiePackage("org.drools.drlx.parser")
                .getRules().stream().filter(r -> r.getName().equals("R")).findFirst().orElseThrow())
                .getLhs().getChildren().stream()
                .filter(Pattern.class::isInstance)
                .map(Pattern.class::cast)
                .filter(p -> p.getSource() instanceof org.drools.base.rule.Accumulate)
                .toList();
        assertThat(patterns).hasSize(1);
        final Pattern wrap = patterns.get(0);

        assertThat(wrap.getSource()).isInstanceOf(MultiAccumulate.class);
        final MultiAccumulate multi = (MultiAccumulate) wrap.getSource();
        assertThat(multi.getAccumulators()).hasSize(3);
        assertThat(((ClassObjectType) wrap.getObjectType()).getClassType())
                .isEqualTo(Object[].class);
        assertThat(wrap.getDeclarations()).hasSize(3)
                .containsKeys("minAge", "maxAge", "avgAge");

        final Declaration minDecl = wrap.getDeclarations().get("minAge");
        assertThat(minDecl.getExtractor()).isInstanceOf(ArrayElementReader.class);
        // Registry resultType for min/max is Comparable.class; avg is Double.class.
        assertThat(minDecl.getExtractor().getExtractToClass()).isEqualTo(Comparable.class);
        final Declaration maxDecl = wrap.getDeclarations().get("maxAge");
        assertThat(maxDecl.getExtractor().getExtractToClass()).isEqualTo(Comparable.class);
        final Declaration avgDecl = wrap.getDeclarations().get("avgAge");
        assertThat(avgDecl.getExtractor().getExtractToClass()).isEqualTo(Double.class);
    }

    @Test
    void multiFunctionCountAndSum() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    long n = count(),
                    var total = sum(p.age),
                    do { results.add(n); results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 10));
            entryPoint.insert(new Person("B", 20));
            entryPoint.insert(new Person("C", 30));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(3L, 60.0);
    }

    @Test
    void multiFunctionWithExpressionExtractors() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var s1 = sum(p.age + 1),
                    var s2 = sum(p.age * 2),
                    do { results.add(s1); results.add(s2); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 10));
            entryPoint.insert(new Person("B", 20));
            entryPoint.insert(new Person("C", 30));
            kieSession.fireAllRules();
        });
        // sum(age+1) = 11+21+31 = 63   |   sum(age*2) = 20+40+60 = 120
        // SumAccumulateFunction normalises to Double.
        assertThat(observed).containsExactly(63.0, 120.0);
    }

    // --- acc() keyword form tests ---

    @Test
    void accKeyword3ParamSumWithoutReverse() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        int s = 0;,
                        s = s + p.age,
                        int sum = s),
                    do { results.add(sum); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(120);
    }

    @Test
    void accKeyword2ParamSingleFunction() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        var avgAge = avg(p.age)),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(40.0);
    }

    @Test
    void accKeyword2ParamGroupedFunctions() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        (var maxAge = max(p.age),
                         var minAge = min(p.age))),
                    do { results.add(maxAge); results.add(minAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(60, 20);
    }

    @Test
    void accKeyword3ParamSumWithReverse() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        int s = 0;,
                        (s = s + p.age, s = s - p.age),
                        int sum = s),
                    do { results.add(sum); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            FactHandle h1 = entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();

            observed.clear();
            entryPoint.delete(h1);
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(100);
    }

    @Test
    void accKeyword5ParamAvg() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        { int count = 0; int total = 0; },
                        { total = total + p.age; count = count + 1; },
                        { total = total - p.age; count = count - 1; },
                        double avgAge = (double) total / count),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(40.0);
    }

    @Test
    void accKeyword5ParamWithRetraction() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    acc(var p : /persons,
                        { int count = 0; int total = 0; },
                        { total = total + p.age; count = count + 1; },
                        { total = total - p.age; count = count - 1; },
                        double avgAge = (double) total / count),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            final EntryPoint entryPoint = kieSession.getEntryPoint("persons");
            FactHandle h1 = entryPoint.insert(new Person("A", 20));
            entryPoint.insert(new Person("B", 40));
            entryPoint.insert(new Person("C", 60));
            kieSession.fireAllRules();

            observed.clear();
            entryPoint.delete(h1);
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(50.0);
    }

    // --- multi-pattern source (and()) tests ---

    @Test
    void accMultiPatternCount() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CountJoined {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        var count = count()),
                    do { results.add(count); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 1));
            kieSession.getEntryPoint("persons").insert(new Person("Bob", 2));
            kieSession.getEntryPoint("orders").insert(new Order("O1", 1, 100));
            kieSession.getEntryPoint("orders").insert(new Order("O2", 2, 200));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(2L);
    }

    @Test
    void accMultiPatternSumSingleBinding() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule SumAmount {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        var total = sum(o.amount)),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 1));
            kieSession.getEntryPoint("persons").insert(new Person("Bob", 2));
            kieSession.getEntryPoint("orders").insert(new Order("O1", 1, 100));
            kieSession.getEntryPoint("orders").insert(new Order("O2", 2, 200));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(300.0);
    }

    @Test
    void accMultiPatternSumCrossBinding() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule WeightedSum {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        var weighted = sum(p.age * o.amount)),
                    do { results.add(weighted); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 1));
            kieSession.getEntryPoint("persons").insert(new Person("Bob", 2));
            kieSession.getEntryPoint("orders").insert(new Order("O1", 1, 100));
            kieSession.getEntryPoint("orders").insert(new Order("O2", 2, 200));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(500.0);
    }

    @Test
    void accMultiPatternCustom3Param() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CustomSum {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        int s = 0;,
                        s = s + o.amount,
                        int total = s),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 1));
            kieSession.getEntryPoint("persons").insert(new Person("Bob", 2));
            kieSession.getEntryPoint("orders").insert(new Order("O1", 1, 100));
            kieSession.getEntryPoint("orders").insert(new Order("O2", 2, 200));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(300);
    }

    @Test
    void accMultiPatternCustom5ParamWithReverse() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule CustomSumReverse {
                    acc(and(var p : /persons, var o : /orders[customerId == p.age]),
                        int s = 0;,
                        { s = s + o.amount; },
                        { s = s - o.amount; },
                        int total = s),
                    do { results.add(total); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("Alice", 1));
            kieSession.getEntryPoint("persons").insert(new Person("Bob", 2));
            kieSession.getEntryPoint("orders").insert(new Order("O1", 1, 100));
            kieSession.getEntryPoint("orders").insert(new Order("O2", 2, 200));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(300);
    }

    @Test
    void accSingleChildAndBehavesLikeSingleSource() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule SingleChildAnd {
                    acc(and(var p : /persons),
                        var avgAge = avg(p.age)),
                    do { results.add(avgAge); }
                }
                """;

        final List<Object> observed = new ArrayList<>();
        withSession(rule, (kieSession, listener) -> {
            kieSession.setGlobal("results", observed);
            kieSession.getEntryPoint("persons").insert(new Person("A", 20));
            kieSession.getEntryPoint("persons").insert(new Person("B", 40));
            kieSession.getEntryPoint("persons").insert(new Person("C", 60));
            kieSession.fireAllRules();
        });
        assertThat(observed).containsExactly(40.0);
    }
}
