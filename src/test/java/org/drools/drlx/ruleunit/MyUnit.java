package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Person;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class MyUnit implements RuleUnitData {

    private final DataStore<Person> persons;

    public MyUnit() {
        this(DataSource.createStore());
    }

    public MyUnit(DataStore<Person> persons) {
        this.persons = persons;
    }

    public DataStore<Person> getPersons() {
        return persons;
    }
}
