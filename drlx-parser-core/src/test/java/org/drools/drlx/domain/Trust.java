package org.drools.drlx.domain;

import org.kie.api.definition.type.Position;

public class Trust {

    @Position(0)
    private String a;

    @Position(1)
    private String b;

    public Trust() {
    }

    public Trust(String a, String b) {
        this.a = a;
        this.b = b;
    }

    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }
}
