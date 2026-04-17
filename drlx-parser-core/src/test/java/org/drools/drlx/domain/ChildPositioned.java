package org.drools.drlx.domain;

import org.kie.api.definition.type.Position;

public class ChildPositioned extends BasePositioned {

    @Position(0)
    private String bar;

    public ChildPositioned() {
    }

    public ChildPositioned(String foo, String bar) {
        super(foo);
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}
