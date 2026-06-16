package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Car;
import org.drools.drlx.domain.Customer;
import org.drools.drlx.domain.Product;
import org.drools.drlx.domain.Rating;
import org.drools.drlx.domain.Rates;
import org.drools.drlx.domain.Vehicle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchTest extends DrlxBuilderTestSupport {

    @Test
    void valueMatch_lowRating_firstCaseFires() {
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
                    match (c.creditRating)
                        case Rating.LOW {
                            var p : /products[ rate == Rates.HIGH ],
                            do { System.out.println("low: " + c.name + " " + p.name); }
                        }
                        case Rating.MEDIUM {
                            var p : /products[ rate == Rates.MEDIUM ],
                            do { System.out.println("medium: " + c.name); }
                        }
                        default {
                            do { System.out.println("other: " + c.name); }
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
    void valueMatch_mediumRating_secondCaseFires() {
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
                    match (c.creditRating)
                        case Rating.LOW {
                            var p : /products[ rate == Rates.HIGH ],
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            var p : /products[ rate == Rates.MEDIUM ],
                            do { System.out.println("medium"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Bob", Rating.MEDIUM));
            unit.products.add(new Product("luxury", Rates.HIGH));
            unit.products.add(new Product("standard", Rates.MEDIUM));
            unit.products.add(new Product("budget", Rates.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
        });
    }

    @Test
    void valueMatch_noMatch_defaultFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            do { System.out.println("medium"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Carol", Rating.HIGH));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$2");
        });
    }

    @Test
    void valueMatch_noDefault_noMatchDoesNotFire() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW {
                            do { System.out.println("low"); }
                        }
                        case Rating.MEDIUM {
                            do { System.out.println("medium"); }
                        }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Carol", Rating.HIGH));
            assertThat(instance.fire()).isZero();
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void typeMatch_carInstanceofFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Vehicle;
                import org.drools.drlx.domain.Car;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var o : /objects,
                    match (o)
                        case #Car {
                            do { System.out.println("car: " + o.vin); }
                        }
                        default {
                            do { System.out.println("vehicle: " + o.vin); }
                        }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.objects.add(new Car("CAR1", 100));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void typeMatch_nonCar_defaultFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Vehicle;
                import org.drools.drlx.domain.Car;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var o : /objects,
                    match (o)
                        case #Car {
                            do { System.out.println("car"); }
                        }
                        default {
                            do { System.out.println("vehicle: " + o.vin); }
                        }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.objects.add(new Vehicle("VEH1"));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
        });
    }

    @Test
    void typeMatchWithConstraints_fastCarFires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Vehicle;
                import org.drools.drlx.domain.Car;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var o : /objects,
                    match (o)
                        case #Car[speed > 80] {
                            do { System.out.println("fast car"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.objects.add(new Car("FAST1", 100));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void typeMatchWithConstraints_slowCarFallsToDefault() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Vehicle;
                import org.drools.drlx.domain.Car;
                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;
                rule R1 {
                    var o : /objects,
                    match (o)
                        case #Car[speed > 80] {
                            do { System.out.println("fast car"); }
                        }
                        default {
                            do { System.out.println("other"); }
                        }
                }
                """;
        withMyUnitInstance(rule, (instance, unit, listener) -> {
            unit.objects.add(new Car("SLOW1", 50));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$1");
        });
    }

    @Test
    void doStatementBody_fires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW do { System.out.println("low"); }
                        default do { System.out.println("other"); }
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }

    @Test
    void bareExpressionBody_fires() {
        String rule = """
                package org.drools.drlx.parser;
                import org.drools.drlx.domain.Customer;
                import org.drools.drlx.domain.Rating;
                import org.drools.drlx.ruleunit.CreditUnit;
                unit CreditUnit;
                rule R1 {
                    var c : /customers,
                    match (c.creditRating)
                        case Rating.LOW System.out.println("low")
                        default System.out.println("other")
                }
                """;
        withCreditUnitInstance(rule, (instance, unit, listener) -> {
            unit.customers.add(new Customer("Alice", Rating.LOW));
            assertThat(instance.fire()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1$0");
        });
    }
}
