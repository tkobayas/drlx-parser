package org.drools.drlx.domain;

import java.util.List;
import java.util.Map;

public class Container {

    private String name;
    private List<String> items;
    private Map<String, String> attributes;

    public Container() {
    }

    public Container(String name, List<String> items, Map<String, String> attributes) {
        this.name = name;
        this.items = items;
        this.attributes = attributes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
