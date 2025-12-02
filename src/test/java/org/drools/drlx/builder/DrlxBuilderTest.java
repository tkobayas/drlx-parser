package org.drools.drlx.builder;

import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drlx.domain.Person;
import org.drools.drlx.util.DrlxHelper;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxBuilderTest {

    @Test
    void testBuildBasicRule() {
        // very basic rule, not inside a class
        String rule = """
                package org.drools.drlx.parser;
                
                import org.drools.drlx.domain.Person;
                
                unit MyUnit;
                
                rule CheckAge {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """;

        PackageDescr packageDescr = DrlxHelper.parseDrlxCompilationUnitAsPackageDescr(rule);
        DrlxRuleBuilder drlxRuleBuilder = new DrlxRuleBuilder();
        KieBase kieBase = drlxRuleBuilder.createKieBase(packageDescr);

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("persons");
        Person person = new Person("John", 25);
        entryPoint.insert(person);
        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }
}
