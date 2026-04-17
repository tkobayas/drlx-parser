package org.drools.drlx.domain;

import org.kie.api.definition.type.Position;

public class Location {

    @Position(0)
    private String city;

    @Position(1)
    private String district;

    public Location() {
    }

    public Location(String city, String district) {
        this.city = city;
        this.district = district;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    @Override
    public String toString() {
        return "Location{" +
                "city='" + city + '\'' +
                ", district='" + district + '\'' +
                '}';
    }
}
