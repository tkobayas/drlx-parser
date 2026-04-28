package org.drools.drlx.domain;

public class Product {
    private final String name;
    private final Rates rate;

    public Product(String name, Rates rate) {
        this.name = name;
        this.rate = rate;
    }

    public String getName() { return name; }
    public Rates getRate() { return rate; }

    @Override public String toString() { return name; }
}
