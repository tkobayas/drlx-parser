package org.drools.drlx.ruleunit;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.ChildPositioned;
import org.drools.drlx.domain.DuplicatePositionLocation;
import org.drools.drlx.domain.Location;
import org.drools.drlx.domain.Order;
import org.drools.drlx.domain.Person;
import org.drools.drlx.domain.PlainLocation;
import org.drools.drlx.domain.ReactiveEmployee;
import org.drools.drlx.domain.Vehicle;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

public class MyUnit implements RuleUnitData {
    public DataStore<Person> persons = DataSource.createStore();
    public DataStore<Address> addresses = DataSource.createStore();
    public DataStore<Person> seniors = DataSource.createStore();
    public DataStore<Person> juniors = DataSource.createStore();
    public DataStore<Location> locations = DataSource.createStore();
    public DataStore<Person> persons1 = DataSource.createStore();
    public DataStore<Person> persons2 = DataSource.createStore();
    public DataStore<Person> persons3 = DataSource.createStore();
    public DataStore<ChildPositioned> childPositionedThings = DataSource.createStore();
    public DataStore<DuplicatePositionLocation> duplicateLocations = DataSource.createStore();
    public DataStore<PlainLocation> plainLocations = DataSource.createStore();
    public DataStore<Vehicle> objects = DataSource.createStore();
    public DataStore<Order> orders = DataSource.createStore();
    public DataStore<ReactiveEmployee> reactiveEmployees = DataSource.createStore();
}
