package org.drools.drlx.builder.syntax;

import java.util.function.BiConsumer;

import org.drools.core.event.TrackingAgendaEventListener;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
abstract class DrlxBuilderTestSupport {

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

    protected static DrlxRuleBuilder newBuilder() {
        return new DrlxRuleBuilder();
    }
}
