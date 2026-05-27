package org.drools.drlx.domain;

import org.kie.api.definition.type.Role;

@Role(Role.Type.EVENT)
public class Withdrawal {

    private final String accountId;
    private final double amount;
    private final String customer;

    public Withdrawal(String accountId, double amount, String customer) {
        this.accountId = accountId;
        this.amount = amount;
        this.customer = customer;
    }

    public Withdrawal(String accountId, double amount) {
        this(accountId, amount, null);
    }

    public String getAccountId() {
        return accountId;
    }

    public double getAmount() {
        return amount;
    }

    public String getCustomer() {
        return customer;
    }

    @Override
    public String toString() {
        return "Withdrawal[accountId=" + accountId + ", amount=" + amount
                + ", customer=" + customer + "]";
    }
}
