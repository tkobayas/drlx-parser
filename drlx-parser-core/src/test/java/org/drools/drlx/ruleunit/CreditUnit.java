package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Customer;
import org.drools.drlx.domain.Product;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class CreditUnit implements RuleUnitData {
    public DataStore<Customer> customers = DataSource.createStore();
    public DataStore<Product> products = DataSource.createStore();
}
