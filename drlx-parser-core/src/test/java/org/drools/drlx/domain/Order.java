package org.drools.drlx.domain;

public class Order {

    private final String id;
    private final int customerId;
    private final int amount;

    public Order(String id, int customerId, int amount) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public int getCustomerId() {
        return customerId;
    }

    public int getAmount() {
        return amount;
    }
}
