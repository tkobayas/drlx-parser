package org.drools.drlx.builder;

import org.drools.ruleunits.api.RuleUnit;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.conf.RuleConfig;

// temporary implementation
public class DrlxRuleUnit implements RuleUnit {

    public DrlxRuleUnit() {
    }

    @Override
    public RuleUnitInstance createInstance(RuleUnitData data) {
        return null;
    }

    @Override
    public RuleUnitInstance createInstance(RuleUnitData data, RuleConfig ruleConfig) {
        // TDB
        return null;
    }
}
