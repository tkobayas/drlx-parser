package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.ReactiveEmployee;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// MVP scope: watch list fully restricts modification re-evaluation only when
// the pattern has no alpha constraints. With conditions present, modifications
// still propagate through the alpha because DrlxLambdaConstraint does not yet
// override getListenedPropertyMask. See follow-up issue on DrlxLambdaConstraint
// property-mask reporting.
class PropertyReactiveWatchListTest extends DrlxBuilderTestSupport {

    @Test
    void plainWatchList_firesOnWatchedProperty_notOnUnwatched() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][basePay, bonusPay],
                    do { System.out.println("fired"); }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
            listener.getAfterMatchFired().clear();

            // salary NOT in watch list → no re-fire.
            emp.setSalary(7000);
            ep.update(fh, emp, "salary");
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
            assertThat(listener.getAfterMatchFired()).isEmpty();

            // basePay in watch list → re-fire.
            emp.setBasePay(5000);
            ep.update(fh, emp, "basePay");
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void wildcardAll_firesOnAnyPropertyChange() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][*],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(1000, 2000, 3000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            emp.setBonusPay(4000);
            ep.update(fh, emp, "bonusPay");
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void wildcardNone_suppressesReFire() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][!*],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(1000, 2000, 3000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            emp.setBasePay(9999);
            ep.update(fh, emp, "basePay");
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
        });
    }

    @Test
    void negativeExclusion_firesOnOthersButNotExcluded() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][*, !bonusPay],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(1000, 2000, 3000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // bonusPay excluded → no re-fire.
            emp.setBonusPay(4000);
            ep.update(fh, emp, "bonusPay");
            assertThat(kieSession.fireAllRules()).isEqualTo(0);

            // basePay not excluded → re-fire.
            emp.setBasePay(5000);
            ep.update(fh, emp, "basePay");
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void unknownProperty_throwsAtBuild() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][basePya],
                    do { }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown property 'basePya'");
    }

    @Test
    void duplicateProperty_throwsAtBuild() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][basePay, basePay],
                    do { }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate property 'basePay'");
    }

    @Test
    void conditionPlusWatchList_firesOnConstraintProp() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[salary > 0][basePay],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // salary read by constraint → re-fire required.
            emp.setSalary(7000);
            ep.update(fh, emp, "salary");
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void conditionPlusWatchList_firesOnWatchProp() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[salary > 0][basePay],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // basePay in watch list → re-fire required.
            emp.setBasePay(5000);
            ep.update(fh, emp, "basePay");
            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void conditionPlusWatchList_doesNotFireOnUnrelated() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[salary > 0][basePay],
                    do { }
                }
                """;

        withSession(rule, (kieSession, listener) -> {
            EntryPoint ep = kieSession.getEntryPoint("reactiveEmployees");
            ReactiveEmployee emp = new ReactiveEmployee(6000, 4000, 1000);
            FactHandle fh = ep.insert(emp);

            assertThat(kieSession.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            // bonusPay neither in watch list nor read by constraint → must NOT re-fire.
            emp.setBonusPay(2000);
            ep.update(fh, emp, "bonusPay");
            assertThat(kieSession.fireAllRules()).isEqualTo(0);
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void duplicateWildcard_throwsAtBuild() {
        final String rule = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.ReactiveEmployee;
                import org.drools.drlx.ruleunit.MyUnit;

                unit MyUnit;

                rule R1 {
                    var e : /reactiveEmployees[][*, *],
                    do { }
                }
                """;

        assertThatThrownBy(() -> newBuilder().build(rule))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate usage of wildcard *");
    }
}
