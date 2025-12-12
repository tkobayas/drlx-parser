package org.drools.drlx.builder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.consequence.Consequence;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.ReteDumper;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.drools.drlx.util.DrlxHelper;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.rule.Rule;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mvel3.lambdaextractor.LambdaRegistry.DEFAULT_PERSISTENCE_PATH;

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
        KieBase kieBase = drlxRuleBuilder.createKieBase(packageDescr); // TBD: test RuleUnitInstance

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint entryPoint = kieSession.getEntryPoint("persons");
        Person person = new Person("John", 25);
        entryPoint.insert(person);
        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(1);

        kieSession.dispose();
    }

    @Test
    void testLambdaSharing() {
        String rule = """
                package org.drools.drlx.parser;
                
                import org.drools.drlx.domain.Person;
                import org.drools.drlx.domain.Address;
                
                unit MyUnit;
                
                rule CheckAge1 {
                    Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                
                rule CheckAge2 {
                    Address s : /addresses[ city == "Tokyo" ],
                        Person p : /persons[ age > 18 ],
                    do { System.out.println(p); }
                }
                """; // placed /persons after /addresses to avoid node sharing, because this test is about lambda sharing

        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();

        PackageDescr packageDescr = DrlxHelper.parseDrlxCompilationUnitAsPackageDescr(rule);
        DrlxRuleBuilder drlxRuleBuilder = new DrlxRuleBuilder();
        KieBase kieBase = drlxRuleBuilder.createKieBase(packageDescr);

        List<Path> classFiles = listClassFiles();
        assertThat(classFiles).hasSize(3);

        // GeneratorEvaluaor___0 : `age > 18` is shared between both rules
        // GeneratorEvaluaor___1 : `System.out.println(p);` is shared between both rules
        // GeneratorEvaluaor___2 : `city == "Tokyo"` is unique to CheckAge2

        List<DrlxLambdaConstraint> constraints = collectConstraints(kieBase, "age > 18");

        System.out.println(stripHiddenClassNameSuffix(constraints.get(0).getEvaluator().getClass().getName()));

        assertThat(constraints).hasSize(2);
        // class names are the same (e.g. GeneratorEvaluaor___0), but Class objects are different, because ClassManager is not share at the moment
        assertThat(stripHiddenClassNameSuffix(constraints.get(0).getEvaluator().getClass().getName()))
                .isEqualTo(stripHiddenClassNameSuffix(constraints.get(1).getEvaluator().getClass().getName()));

        List<DrlxLambdaConsequence> consequences = collectConsequences(kieBase);
        assertThat(consequences).hasSize(2);
        assertThat(stripHiddenClassNameSuffix(consequences.get(0).getEvaluator().getClass().getName()))
                .isEqualTo(stripHiddenClassNameSuffix(consequences.get(1).getEvaluator().getClass().getName()));

        KieSession kieSession = kieBase.newKieSession();

        EntryPoint personsEntryPoint = kieSession.getEntryPoint("persons");
        Person person = new Person("John", 25);
        personsEntryPoint.insert(person);
        EntryPoint addressesEntryPoint = kieSession.getEntryPoint("addresses");
        Address address = new Address("Tokyo");
        addressesEntryPoint.insert(address);

        int fired = kieSession.fireAllRules();

        assertThat(fired).isEqualTo(2);

        kieSession.dispose();
    }

    private String stripHiddenClassNameSuffix(String className) {
        return className.split("/0x")[0];
    }

    private static List<DrlxLambdaConsequence> collectConsequences(KieBase kieBase) {
        return kieBase.getKiePackage("org.drools.drlx.parser").getRules().stream()
                .map(RuleImpl.class::cast)
                .map(RuleImpl::getConsequence)
                .map(DrlxLambdaConsequence.class::cast)
                .toList();
    }

    private static List<DrlxLambdaConstraint> collectConstraints(KieBase kieBase, String expression) {
        Set<AlphaNode> alphaNodes = ReteDumper.collectRete(kieBase).stream()
                .filter(n -> n.getType() == NodeTypeEnums.AlphaNode)
                .map(AlphaNode.class::cast)
                .collect(Collectors.toSet());
        return alphaNodes.stream()
                .map(AlphaNode::getConstraint)
                .map(DrlxLambdaConstraint.class::cast)
                .filter(constraint -> constraint.getExpression().equals(expression))
                .toList();
    }

    private List<Path> listClassFiles() {
        try (Stream<Path> walk = Files.walk(DEFAULT_PERSISTENCE_PATH)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
