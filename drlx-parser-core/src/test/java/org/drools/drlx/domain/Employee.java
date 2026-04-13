package org.drools.drlx.domain;

public class Employee extends Person {

    private String department;

    public Employee() {
    }

    public Employee(String name, int age, String department) {
        super(name, age);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    @Override
    public String toString() {
        return "Employee{" +
                "name='" + getName() + '\'' +
                ", age=" + getAge() +
                ", department='" + department + '\'' +
                '}';
    }
}
