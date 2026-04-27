package org.drools.drlx.builder.syntax;

import java.nio.file.Path;
import java.util.List;

import org.drools.base.RuleBase;
import org.drools.core.event.TrackingAgendaEventListener;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.drlx.builder.DrlxLambdaCompiler;
import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.ConsequenceIR;
import org.drools.drlx.builder.DrlxRuleAstModel.EvalIR;
import org.drools.drlx.builder.DrlxRuleAstModel.LhsItemIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstRuntimeBuilder;
import org.drools.drlx.domain.Person;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.mvel3.ClassManager;
import org.mvel3.MVELBatchCompiler;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Programmatic E2E test for the EvalIR → EvalCondition runtime mapping.
 * Constructs the IR by hand (no grammar) and verifies an end-to-end fire.
 */
@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class EvalIRBuilderTest {

    @Test
    void evalIrFiltersPattern() {
        // IR equivalent of (without grammar):
        //   rule R { var p : /persons, test p.age > 30, do { ... } }
        PatternIR pattern = new PatternIR(
                "Person", "p", "persons",
                List.of(),
                null, List.of(), false, List.of());
        EvalIR eval = new EvalIR("p.age > 30", List.of("p"));
        ConsequenceIR rhs = new ConsequenceIR("System.out.println(p);");

        RuleIR rule = new RuleIR(
                "R",
                List.of(),
                List.of((LhsItemIR) pattern, (LhsItemIR) eval),
                rhs);

        CompilationUnitIR unit = new CompilationUnitIR(
                "org.drools.drlx.parser",
                "MyUnit",
                List.of("org.drools.drlx.domain.Person", "org.drools.drlx.ruleunit.MyUnit"),
                List.of(rule));

        KieBase kieBase = buildFromIr(unit);
        KieSession session = kieBase.newKieSession();
        TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
        session.addEventListener(listener);
        try {
            EntryPoint persons = session.getEntryPoint("persons");
            persons.insert(new Person("Alice", 40));
            persons.insert(new Person("Bob", 25));
            int fired = session.fireAllRules();
            assertThat(fired).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R");
        } finally {
            session.dispose();
        }
    }

    private static KieBase buildFromIr(CompilationUnitIR ast) {
        Path persistDir = LambdaRegistry.PERSISTENCE_ENABLED ? LambdaRegistry.DEFAULT_PERSISTENCE_PATH : null;
        MVELBatchCompiler batchCompiler = new MVELBatchCompiler(new ClassManager(), persistDir);
        DrlxLambdaCompiler lambdaCompiler = new DrlxLambdaCompiler(batchCompiler);
        DrlxRuleAstRuntimeBuilder builder = new DrlxRuleAstRuntimeBuilder(lambdaCompiler);
        List<KiePackage> packages = builder.build(ast);
        lambdaCompiler.compileBatch(Thread.currentThread().getContextClassLoader());

        RuleBase ruleBase = RuleBaseFactory.newRuleBase("evalIrTestKBase",
                RuleBaseFactory.newKnowledgeBaseConfiguration());
        ruleBase.addPackages(packages);
        return KnowledgeBaseFactory.newKnowledgeBase(ruleBase);
    }
}
