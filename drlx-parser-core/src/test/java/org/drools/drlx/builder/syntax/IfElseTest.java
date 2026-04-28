package org.drools.drlx.builder.syntax;

import org.drools.drlx.domain.Customer;
import org.drools.drlx.domain.Product;
import org.drools.drlx.domain.Rates;
import org.drools.drlx.domain.Rating;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.rule.EntryPoint;

import static org.assertj.core.api.Assertions.assertThat;

class IfElseTest extends DrlxBuilderTestSupport {

    @Test
    void binaryIfElse_lowRating_picksHighRateProduct() {
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
                        var p : /products[ rate == Rates.HIGH ]
                    } else {
                        var p : /products[ rate == Rates.LOW ]
                    },
                    do { System.out.println(c + " " + p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Alice", Rating.LOW));
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("budget", Rates.LOW));
            assertThat(session.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }

    @Test
    void binaryIfElse_highRating_picksLowRateProduct() {
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
                        var p : /products[ rate == Rates.HIGH ]
                    } else {
                        var p : /products[ rate == Rates.LOW ]
                    },
                    do { System.out.println(c + " " + p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Bob", Rating.HIGH));
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("budget", Rates.LOW));
            assertThat(session.fireAllRules()).isEqualTo(1);
            assertThat(listener.getAfterMatchFired()).containsExactly("R1");
        });
    }
}
