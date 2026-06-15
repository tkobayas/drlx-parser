package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Customer;
import org.drools.drlx.domain.Product;
import org.drools.drlx.domain.Rating;
import org.drools.drlx.domain.Rates;
import org.drools.ruleunits.api.DataHandle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfElseFormBTest extends DrlxBuilderTestSupport {

    @Test
    void binaryFormB_lowRating_ifBranchFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println(c + " " + p); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println(c + " " + p); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void binaryFormB_highRating_elseBranchFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println(c + " " + p); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println(c + " " + p); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Bob", Rating.HIGH));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
        });
    }

    @Test
    void elseIfChain_middleBranchFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println("low"); }
                    } else if (c.creditRating == Rating.MEDIUM) {
                        var p : /products[ rate == Rates.MEDIUM ],
                        do { System.out.println("medium"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("other"); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.MEDIUM));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("standard", Rates.MEDIUM));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
        });
    }

    @Test
    void noFinalElse_noMatchDoesNotFire() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println("low"); }
                    } else if (c.creditRating == Rating.MEDIUM) {
                        var p : /products[ rate == Rates.MEDIUM ],
                        do { System.out.println("medium"); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Carol", Rating.HIGH));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("standard", Rates.MEDIUM));
            assertThat(instance.fire()).isZero();
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void branchSpecificBindings_accessibleInConsequence() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println(c.name + " gets " + p.name); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println(c.name + " gets " + p.name); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void multipleConsequences_allExecute() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println("first: " + c.name); },
                        do { System.out.println("second: " + p.name); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("else"); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void bareExpressionConsequence_firesCorrectly() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        System.out.println(c + " " + p)
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        System.out.println(c + " " + p)
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void propertyReactivity_guardReEvaluatesOnUpdate() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println("low"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("other"); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            Customer alice = new Customer("Alice", Rating.HIGH);
            DataHandle handle = unit.customers.add(alice);
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
            listener.getAfterMatchFired().clear();

            alice.setCreditRating(Rating.LOW);
            unit.customers.update(handle, alice);
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void nestedFormAInsideFormB_works() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        if (c.name == "Alice") {
                            var p : /products[ rate == Rates.HIGH ]
                        } else {
                            var p : /products[ rate == Rates.MEDIUM ]
                        },
                        do { System.out.println(c + " " + p); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println(c + " " + p); }
                    }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("standard", Rates.MEDIUM));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void ruleAttributes_salienceHonoredOnSyntheticRules() {
        String ruleHigh = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                import org.drools.drlx.annotations.Salience;
                unit CreditUnit;
                @Salience(10)
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        var p : /products[ rate == Rates.HIGH ],
                        do { System.out.println("R1 low"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("R1 other"); }
                    }
                }
                rule R2 {
                    var c : /customers,
                    var p : /products,
                    do { System.out.println("R2"); }
                }
                """;
        withCreditUnitInstance(ruleHigh, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            unit.products.add(new Product("luxury", Rates.HIGH));
            assertThat(instance.fire()).isEqualTo(2);
            assertThat(listener.getAfterMatchFired().get(0)).isEqualTo("R1$0");
        });
    }

    @Test
    void nestedFormBInsideFormB_compilationError() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Product;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.domain.Rates;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    if (c.creditRating == Rating.LOW) {
                        if (c.name == "Alice") {
                            var p : /products[ rate == Rates.HIGH ],
                            do { System.out.println("nested"); }
                        } else {
                            var p : /products[ rate == Rates.MEDIUM ],
                            do { System.out.println("nested else"); }
                        },
                        do { System.out.println("outer"); }
                    } else {
                        var p : /products[ rate == Rates.LOW ],
                        do { System.out.println("else"); }
                    }
                }
                """;
        assertThatThrownBy(() -> withCreditUnitInstance(rule, (instance, unit, listener) -> {}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Nested per-branch consequences are not supported");
    }
}
