package org.drools.drlx.domain;

import org.kie.api.definition.type.Position;

public class BasePositioned {

    @Position(0)
    private String foo;

    public BasePositioned() {
    }

    public BasePositioned(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }
}
