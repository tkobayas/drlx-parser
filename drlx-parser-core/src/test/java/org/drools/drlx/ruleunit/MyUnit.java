package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.drools.ruleunits.api.DataStore;

public class MyUnit {
    public DataStore<Person> persons;
    public DataStore<Address> addresses;
    public DataStore<Person> seniors;
    public DataStore<Person> juniors;
    public DataStore<Location> locations;
}
