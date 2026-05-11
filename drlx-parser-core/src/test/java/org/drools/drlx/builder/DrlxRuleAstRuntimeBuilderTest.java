package org.drools.drlx.builder;

import java.lang.reflect.ParameterizedType;
import java.util.Map;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.drools.ruleunits.api.DataStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrlxRuleAstRuntimeBuilderTest {

    public static class Fixture {
        public DataStore<Person> persons;
        public int counter;
        public static String IGNORED;
        @SuppressWarnings("unused")
        private DataStore<Address> hidden;
    }

    @Test
    void buildGlobalTypeMapIncludesPublicNonStaticFields() {
        Map<String, java.lang.reflect.Type> map =
                DrlxRuleAstRuntimeBuilder.buildGlobalTypeMap(Fixture.class);

        assertThat(map).containsOnlyKeys("persons", "counter");
    }

    @Test
    void buildGlobalTypeMapPreservesGenericTypeForDataSourceField() {
        Map<String, java.lang.reflect.Type> map =
                DrlxRuleAstRuntimeBuilder.buildGlobalTypeMap(Fixture.class);

        java.lang.reflect.Type personsType = map.get("persons");
        assertThat(personsType).isInstanceOf(ParameterizedType.class);
        ParameterizedType pt = (ParameterizedType) personsType;
        assertThat(pt.getRawType()).isEqualTo(DataStore.class);
        assertThat(pt.getActualTypeArguments()).containsExactly(Person.class);
    }

    @Test
    void buildGlobalTypeMapPreservesPrimitiveType() {
        Map<String, java.lang.reflect.Type> map =
                DrlxRuleAstRuntimeBuilder.buildGlobalTypeMap(Fixture.class);

        assertThat(map.get("counter")).isEqualTo(int.class);
    }
}
