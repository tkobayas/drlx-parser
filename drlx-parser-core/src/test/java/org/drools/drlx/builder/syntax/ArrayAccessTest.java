package org.drools.drlx.builder.syntax;

import java.util.List;
import java.util.Map;

import org.drools.drlx.domain.Container;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayAccessTest extends DrlxBuilderTestSupport {

    @Test
    void listAccessWithLiteralIndex() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Container;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule ListLiteralIndex {
                    Container c : /containers[ items[0] == "apple" ],
                    do { System.out.println(c.getName()); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.containers.add(new Container("match", List.of("apple", "banana"), Map.of()));
            unit.containers.add(new Container("noMatch", List.of("cherry", "banana"), Map.of()));
            assertThat(instance.fire()).isEqualTo(1);
        });
    }

    @Test
    void mapAccessWithStringKey() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Container;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule MapStringKey {
                    Container c : /containers[ attributes["color"] == "red" ],
                    do { System.out.println(c.getName()); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.containers.add(new Container("match", List.of(), Map.of("color", "red")));
            unit.containers.add(new Container("noMatch", List.of(), Map.of("color", "blue")));
            assertThat(instance.fire()).isEqualTo(1);
        });
    }

    @Test
    void listAccessWithExpressionIndex() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Container;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule ListExpressionIndex {
                    Container c : /containers[ items[2 - 1] == "banana" ],
                    do { System.out.println(c.getName()); }
                }
                """;

        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.containers.add(new Container("match", List.of("apple", "banana"), Map.of()));
            unit.containers.add(new Container("noMatch", List.of("apple", "cherry"), Map.of()));
            assertThat(instance.fire()).isEqualTo(1);
        });
    }
}
