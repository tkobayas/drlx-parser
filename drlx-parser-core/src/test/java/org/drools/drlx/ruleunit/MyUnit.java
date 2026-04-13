package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class MyUnit implements RuleUnitData {

    private final DataStore<Person> persons;

    private final DataStore<Address> addresses;

    public MyUnit() {
        this(DataSource.createStore(), DataSource.createStore());
    }

    public MyUnit(DataStore<Person> persons, DataStore<Address> addresses) {
        this.persons = persons;
        this.addresses = addresses;
    }

    public DataStore<Person> getPersons() {
        return persons;
    }

    public DataStore<Address> getStrings() {
        return addresses;
    }
}
