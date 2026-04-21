package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.ChildPositioned;
import org.drools.drlx.domain.DuplicatePositionLocation;
import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Person;
import org.drools.drlx.domain.PlainLocation;
import org.drools.drlx.domain.Vehicle;
import org.drools.ruleunits.api.DataStore;

public class MyUnit {
    public DataStore<Person> persons;
    public DataStore<Address> addresses;
    public DataStore<Person> seniors;
    public DataStore<Person> juniors;
    public DataStore<Location> locations;
    public DataStore<Person> persons1;
    public DataStore<Person> persons2;
    public DataStore<Person> persons3;
    public DataStore<ChildPositioned> childPositionedThings;
    public DataStore<DuplicatePositionLocation> duplicateLocations;
    public DataStore<PlainLocation> plainLocations;
    public DataStore<Vehicle> objects;
}
