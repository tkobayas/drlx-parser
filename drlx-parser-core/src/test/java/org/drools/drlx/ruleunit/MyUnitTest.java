package org.drools.drlx.ruleunit;

import org.drools.ruleunits.api.RuleUnitData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyUnitTest {

    @Test
    void implementsRuleUnitData() {
        assertThat(new MyUnit()).isInstanceOf(RuleUnitData.class);
    }

    @Test
    void dataStoresAreInitialised() {
        MyUnit unit = new MyUnit();
        assertThat(unit.persons).isNotNull();
        assertThat(unit.addresses).isNotNull();
        assertThat(unit.seniors).isNotNull();
        assertThat(unit.juniors).isNotNull();
        assertThat(unit.locations).isNotNull();
        assertThat(unit.persons1).isNotNull();
        assertThat(unit.persons2).isNotNull();
        assertThat(unit.persons3).isNotNull();
        assertThat(unit.childPositionedThings).isNotNull();
        assertThat(unit.duplicateLocations).isNotNull();
        assertThat(unit.plainLocations).isNotNull();
        assertThat(unit.objects).isNotNull();
        assertThat(unit.orders).isNotNull();
        assertThat(unit.reactiveEmployees).isNotNull();
    }
}
