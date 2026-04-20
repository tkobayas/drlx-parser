package org.drools.drlx.builder.syntax;

import java.util.function.Consumer;

import org.drools.drlx.builder.DrlxRuleBuilder;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

@DisabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
abstract class DrlxBuilderTestSupport {

    protected static void withSession(final String rule, final Consumer<KieSession> test) {
        final KieBase kieBase = new DrlxRuleBuilder().build(rule);
        final KieSession kieSession = kieBase.newKieSession();
        try {
            test.accept(kieSession);
        } finally {
            kieSession.dispose();
        }
    }

    protected static DrlxRuleBuilder newBuilder() {
        return new DrlxRuleBuilder();
    }
}
