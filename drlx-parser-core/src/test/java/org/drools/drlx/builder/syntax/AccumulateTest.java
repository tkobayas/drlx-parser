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

import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

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
}
