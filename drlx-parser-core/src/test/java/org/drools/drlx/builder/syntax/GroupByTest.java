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

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.extractors.ArrayElementReader;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.drlx.builder.DrlxGroupByAccumulate;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.domain.Person;
import org.drools.drlx.domain.Order;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;

class GroupByTest extends DrlxBuilderTestSupport {

    @Test
    void singleFunctionBoundKey() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            var g = p.name,
                            var avgAge = avg(p.age)),
                    do { results.add(g); results.add(avgAge); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 20));
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 30));
            instance.fire();
            assertThat(unit.results).hasSize(4);
            assertThat(unit.results).contains("Alice", "Bob", 30.0);
        });
    }

    @Test
    void singleFunctionUnboundKey() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            p.name,
                            var avgAge = avg(p.age)),
                    do { results.add(avgAge); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 20));
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 30));
            instance.fire();
            assertThat(unit.results).hasSize(2);
            assertThat(unit.results).containsExactlyInAnyOrder(30.0, 30.0);
        });
    }

    @Test
    void multiFunctionGroupBy() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            var g = p.name,
                            (var minAge = min(p.age),
                             var maxAge = max(p.age))),
                    do { results.add(g); results.add(minAge); results.add(maxAge); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 20));
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 30));
            instance.fire();
            assertThat(unit.results).hasSize(6);
            assertThat(unit.results).contains("Alice", 20, 40, "Bob", 30, 30);
        });
    }

    @Test
    void multiPatternSource() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Order;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(and(var p : /persons, var o : /orders[customerId == p.age]),
                            p.name,
                            var total = sum(o.amount)),
                    do { results.add(total); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 1));
            unit.persons.add(new Person("Bob", 2));
            unit.orders.add(new Order("O1", 1, 100));
            unit.orders.add(new Order("O2", 1, 200));
            unit.orders.add(new Order("O3", 2, 50));
            instance.fire();
            assertThat(unit.results).hasSize(2);
            assertThat(unit.results).containsExactlyInAnyOrder(300.0, 50.0);
        });
    }

    @Test
    void customAccumulator3Param() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            var g = p.name,
                            int s = 0;,
                            s = s + p.age,
                            int total = s),
                    do { results.add(g); results.add(total); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.persons.add(new Person("Alice", 20));
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 30));
            instance.fire();
            assertThat(unit.results).hasSize(4);
            assertThat(unit.results).contains("Alice", 60, "Bob", 30);
        });
    }

    @Test
    void customAccumulator5ParamWithRetraction() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            var g = p.name,
                            int s = 0;,
                            (s = s + p.age, s = s - p.age),
                            int total = s),
                    do { results.add(g); results.add(total); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            org.drools.ruleunits.api.DataHandle h1 = unit.persons.add(new Person("Alice", 20));
            unit.persons.add(new Person("Alice", 40));
            unit.persons.add(new Person("Bob", 30));
            instance.fire();

            unit.results.clear();
            unit.persons.remove(h1);
            instance.fire();
            // Only Alice's group changed — Bob's group doesn't re-fire
            assertThat(unit.results).hasSize(2);
            assertThat(unit.results).containsExactly("Alice", 40);
        });
    }

    @Test
    void singleFunctionEmitsGroupByAccumulate() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R {
                    groupBy(var p : /persons,
                            var g = p.name,
                            var avgAge = avg(p.age)),
                    do {}
                }
                """;
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        RuleImpl impl = (RuleImpl) kieBase
                .getKiePackage("org.drools.drlx.parser")
                .getRules().stream()
                .filter(r -> r.getName().equals("R"))
                .findFirst().orElseThrow();

        Pattern wrap = impl.getLhs().getChildren().stream()
                .filter(Pattern.class::isInstance)
                .map(Pattern.class::cast)
                .filter(p -> p.getSource() instanceof Accumulate)
                .findFirst().orElseThrow();

        assertThat(wrap.getSource()).isInstanceOf(DrlxGroupByAccumulate.class);
        assertThat(((Accumulate) wrap.getSource()).isGroupBy()).isTrue();
        assertThat(((ClassObjectType) wrap.getObjectType()).getClassType())
                .isEqualTo(Object[].class);
        assertThat(wrap.getDeclarations()).containsKeys("avgAge", "g");

        Declaration avgDecl = wrap.getDeclarations().get("avgAge");
        assertThat(avgDecl.getExtractor()).isInstanceOf(ArrayElementReader.class);

        Declaration gDecl = wrap.getDeclarations().get("g");
        assertThat(gDecl.getExtractor()).isInstanceOf(ArrayElementReader.class);
    }
}
