package org.drools.drlx.domain;

public class Car extends Vehicle {
    private final int speed;

    public Car(String vin, int speed) {
        super(vin);
        this.speed = speed;
    }

    public int getSpeed() {
        return speed;
    }
}
