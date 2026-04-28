package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Customer;
import org.drools.drlx.domain.Product;
import org.drools.ruleunits.api.DataStore;

public class CreditUnit {
    public DataStore<Customer> customers;
    public DataStore<Product> products;
}
