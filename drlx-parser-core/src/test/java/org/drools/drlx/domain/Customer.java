package org.drools.drlx.domain;

public class Customer {
    private final String name;
    private Rating creditRating;

    public Customer(String name, Rating creditRating) {
        this.name = name;
        this.creditRating = creditRating;
    }

    public String getName() { return name; }
    public Rating getCreditRating() { return creditRating; }
    public void setCreditRating(Rating creditRating) { this.creditRating = creditRating; }

    @Override public String toString() { return name; }
}
