package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Withdrawal;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class WithdrawalUnit implements RuleUnitData {
    public DataStore<Withdrawal> withdrawals = DataSource.createStore();
}
