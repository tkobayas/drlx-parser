package org.drools.drlx.ruleunit;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.InternalRuleBase;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.impl.EntryPointDataProcessor;
import org.drools.ruleunits.impl.sessions.RuleUnitExecutorImpl;
import org.kie.api.KieBase;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.time.SessionClock;

/**
 * Runs a DRLX-built {@link KieBase} against a populated {@link RuleUnitData},
 * bridging DRLX (which compiles rules from a string at runtime) and the
 * {@link RuleUnitInstance} surface from {@code drools-ruleunits-api}.
 *
 * <p>The upstream {@code RuleUnitProvider.createRuleUnitInstance(unit)} path
 * is not usable for DRLX: it requires a generated {@code RuleUnit<T>} class
 * registered via {@link java.util.ServiceLoader}, produced by scanning {@code
 * .drl} files on the classpath. DRLX has no equivalent codegen, so this
 * wrapper performs the bind step directly against the {@link KieBase}.
 *
 * <p>For each public, non-static {@link DataSource} field declared on {@code
 * T}, the constructor (a) subscribes an
 * {@link EntryPointDataProcessor} to the same-named entry point on the
 * underlying {@link ReteEvaluator}, and (b) sets the DataSource as a global
 * of the same name. Globals that the rule unit did not declare are silently
 * skipped — same convention as the upstream
 * {@code AbstractRuleUnitInstance.bind}.
 *
 * <p>{@link #unit()} returns {@code null}: there is no upstream
 * {@code RuleUnit<T>} for a DRLX-built KieBase. Tests that need a
 * {@code RuleUnit} reference cannot use this wrapper.
 */
public final class DrlxRuleUnitInstance<T extends RuleUnitData> implements RuleUnitInstance<T> {

    private final T unitData;
    private final ReteEvaluator reteEvaluator;

    public static <T extends RuleUnitData> DrlxRuleUnitInstance<T> create(KieBase kieBase, T unitData) {
        return new DrlxRuleUnitInstance<>(kieBase, unitData);
    }

    private DrlxRuleUnitInstance(KieBase kieBase, T unitData) {
        this.unitData = unitData;
        this.reteEvaluator = new RuleUnitExecutorImpl((InternalRuleBase) kieBase);
        bind();
    }

    private void bind() {
        for (Field field : unitData.getClass().getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) {
                continue;
            }
            Object value;
            try {
                value = field.get(unitData);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read field " + field.getName(), e);
            }
            if (value == null) {
                continue;
            }
            String name = field.getName();
            if (value instanceof DataSource<?> ds) {
                ds.subscribe(new EntryPointDataProcessor(reteEvaluator.getEntryPoint(name)));
            }
            try {
                reteEvaluator.setGlobal(name, value);
            } catch (RuntimeException ignored) {
                // Global not declared in this rule unit — same convention as upstream bind.
            }
        }
    }

    @Override
    public RuleUnit<T> unit() {
        return null;
    }

    @Override
    public T ruleUnitData() {
        return unitData;
    }

    @Override
    public int fire() {
        return reteEvaluator.fireAllRules();
    }

    @Override
    public int fire(AgendaFilter agendaFilter) {
        return reteEvaluator.fireAllRules(agendaFilter);
    }

    @Override
    public QueryResults executeQuery(String query, Object... arguments) {
        fire();
        return reteEvaluator.getQueryResults(query, arguments);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends SessionClock> C getClock() {
        return (C) reteEvaluator.getSessionClock();
    }

    @Override
    public void close() {
        reteEvaluator.dispose();
    }
}
