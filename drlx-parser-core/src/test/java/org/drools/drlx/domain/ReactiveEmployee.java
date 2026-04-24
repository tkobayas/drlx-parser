package org.drools.drlx.domain;

import org.kie.api.definition.type.PropertyReactive;

@PropertyReactive
public class ReactiveEmployee {

    private int salary;
    private int basePay;
    private int bonusPay;

    public ReactiveEmployee() {
    }

    public ReactiveEmployee(int salary, int basePay, int bonusPay) {
        this.salary = salary;
        this.basePay = basePay;
        this.bonusPay = bonusPay;
    }

    public int getSalary() {
        return salary;
    }

    public void setSalary(int salary) {
        this.salary = salary;
    }

    public int getBasePay() {
        return basePay;
    }

    public void setBasePay(int basePay) {
        this.basePay = basePay;
    }

    public int getBonusPay() {
        return bonusPay;
    }

    public void setBonusPay(int bonusPay) {
        this.bonusPay = bonusPay;
    }
}
