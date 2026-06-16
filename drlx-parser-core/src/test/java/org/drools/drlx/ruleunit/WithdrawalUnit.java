package org.drools.drlx.ruleunit;

import java.util.ArrayList;
import java.util.List;

import org.drools.drlx.domain.Withdrawal;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStream;
import org.drools.ruleunits.api.RuleUnitData;

public class WithdrawalUnit implements RuleUnitData {
    public DataStream<Withdrawal> withdrawals = DataSource.createStream();
    public List<Object> results = new ArrayList<>();
}
