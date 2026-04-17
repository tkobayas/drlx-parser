package org.drools.drlx.domain;

import org.kie.api.definition.type.Position;

public class DuplicatePositionLocation {

    @Position(0)
    private String city;

    @Position(0)
    private String district;

    public DuplicatePositionLocation() {
    }

    public DuplicatePositionLocation(String city, String district) {
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
}
