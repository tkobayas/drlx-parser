package org.drools.drlx.domain;

import org.kie.api.definition.type.Role;

@Role(Role.Type.EVENT)
public class Withdrawal {

    private final String accountId;
    private final double amount;

    public Withdrawal(String accountId, double amount) {
        this.accountId = accountId;
        this.amount = amount;
    }

    public String getAccountId() {
        return accountId;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Withdrawal[accountId=" + accountId + ", amount=" + amount + "]";
    }
}
