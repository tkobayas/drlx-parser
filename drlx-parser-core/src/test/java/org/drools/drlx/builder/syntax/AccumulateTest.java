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

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

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
    void qualifiedFunctionNameRejected() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    var p : /persons,
                    var avgAge = Func.avg(p.age),
                    do {}
                }
                """;
        assertThatThrownBy(() -> new DrlxRuleBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("qualified accumulate function names")
                .hasMessageContaining("Func.avg");
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
}
