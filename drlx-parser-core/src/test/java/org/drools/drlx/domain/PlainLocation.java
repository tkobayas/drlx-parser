package org.drools.drlx.domain;

public class PlainLocation {

    private String city;
    private String district;

    public PlainLocation() {
    }

    public PlainLocation(String city, String district) {
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
        return "PlainLocation{city='" + city + "', district='" + district + "'}";
    }
}
