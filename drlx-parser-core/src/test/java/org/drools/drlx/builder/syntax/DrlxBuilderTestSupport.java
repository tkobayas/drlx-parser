package org.drools.drlx.builder.syntax;

import java.util.function.BiConsumer;

import org.drools.core.event.TrackingAgendaEventListener;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.drools.drlx.ruleunit.CreditUnit;
import org.drools.drlx.ruleunit.DrlxRuleUnitInstance;
import org.drools.drlx.ruleunit.MyUnit;
import org.drools.ruleunits.api.RuleUnitData;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
abstract class DrlxBuilderTestSupport {

    @FunctionalInterface
    interface TriConsumer<A, B, C> { void accept(A a, B b, C c); }

    protected static void withSession(final String rule,
                                      final BiConsumer<KieSession, TrackingAgendaEventListener> test) {
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        final KieSession kieSession = kieBase.newKieSession();
        final TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
        kieSession.addEventListener(listener);
        try {
            test.accept(kieSession, listener);
        } finally {
            kieSession.dispose();
        }
    }

    protected static void withMyUnitInstance(String rule,
            TriConsumer<DrlxRuleUnitInstance<MyUnit>, MyUnit, TrackingAgendaEventListener> test) {
        KieBase kieBase = new DrlxRuleBuilder().build(rule);
        MyUnit unit = new MyUnit();
        try (DrlxRuleUnitInstance<MyUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);
            test.accept(instance, unit, listener);
        }
    }

    protected static void withCreditUnitInstance(String rule,
            TriConsumer<DrlxRuleUnitInstance<CreditUnit>, CreditUnit, TrackingAgendaEventListener> test) {
        KieBase kieBase = new DrlxRuleBuilder().build(rule);
        CreditUnit unit = new CreditUnit();
        try (DrlxRuleUnitInstance<CreditUnit> instance = DrlxRuleUnitInstance.create(kieBase, unit)) {
            TrackingAgendaEventListener listener = new TrackingAgendaEventListener();
            instance.addEventListener(listener);
            test.accept(instance, unit, listener);
        }
    }

    protected static DrlxRuleBuilder newBuilder() {
        return new DrlxRuleBuilder();
    }
}
