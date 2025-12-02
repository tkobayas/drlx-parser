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
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.drools.drlx.parser;

import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drlx.util.DrlxHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxToDescrVisitorTest {

    @Test
    void testBuildDescrForBasicRule() {
        String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                unit org.drools.drlx.ruleunit.MyUnit;

                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        PackageDescr packageDescr = DrlxHelper.parseDrlxCompilationUnitAsPackageDescr(rule);

        assertThat(packageDescr.getName()).isEqualTo("org.drools.drlx.parser");
        assertThat(packageDescr.getImports())
                .extracting(importDescr -> importDescr.getTarget())
                .containsExactly("org.drools.drlx.domain.Person");
        assertThat(packageDescr.getUnit()).isNotNull();
        assertThat(packageDescr.getUnit().getTarget()).isEqualTo("org.drools.drlx.ruleunit.MyUnit");

        assertThat(packageDescr.getRules()).hasSize(1);
        RuleDescr ruleDescr = packageDescr.getRules().get(0);
        assertThat(ruleDescr.getName()).isEqualTo("CheckAge");
        assertThat(ruleDescr.getConsequence()).isEqualTo("System.out.println(p);");

        assertThat(ruleDescr.getLhs().getDescrs()).hasSize(1);
        PatternDescr patternDescr = (PatternDescr) ruleDescr.getLhs().getDescrs().get(0);
        assertThat(patternDescr.getObjectType()).isEqualTo("Person");
        assertThat(patternDescr.getIdentifier()).isEqualTo("p");
        assertThat(patternDescr.getSource()).isInstanceOf(FromDescr.class);
        FromDescr fromDescr = (FromDescr) patternDescr.getSource();
        assertThat(fromDescr.getDataSource().getText().replaceAll("\\s+", ""))
                .isEqualTo("/persons");
        assertThat(patternDescr.getDescrs()).hasSize(1);
        ExprConstraintDescr constraintDescr = (ExprConstraintDescr) patternDescr.getDescrs().get(0);
        assertThat(constraintDescr.getExpression().replaceAll("\\s+", ""))
                .isEqualTo("age>18");
    }
}
