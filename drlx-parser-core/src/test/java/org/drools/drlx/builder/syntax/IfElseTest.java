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
    void sameNameBindingFlowsToBranchSpecificProduct_lowOnlyHigh() {
        // LOW customer + only HIGH product → matches the LOW branch only.
        String rule = sameNameBindingRule();
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Alice", Rating.LOW));
            products.insert(new Product("luxury", Rates.HIGH));
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void sameNameBindingFlowsToBranchSpecificProduct_lowOnlyLow_doesNotFire() {
        // LOW customer + only LOW product → LOW branch needs HIGH product, no match.
        String rule = sameNameBindingRule();
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Alice", Rating.LOW));
            products.insert(new Product("budget", Rates.LOW));
            assertThat(session.fireAllRules()).isZero();
        });
    }

    private static String sameNameBindingRule() {
        return """
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
    }

    @Test
    void multiElementBranch_implicitAnd() {
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
                        var p1 : /products[ rate == Rates.HIGH ],
                        var p2 : /products[ rate == Rates.MEDIUM ]
                    } else {
                        var p1 : /products[ rate == Rates.LOW ],
                        var p2 : /products[ rate == Rates.LOW ]
                    },
                    do { System.out.println(c + " " + p1 + " " + p2); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Alice", Rating.LOW));
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("standard", Rates.MEDIUM));
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void groupElementInsideBranch_not() {
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
                        not /products[ rate == Rates.HIGH ],
                        var p : /products[ rate == Rates.MEDIUM ]
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
            products.insert(new Product("standard", Rates.MEDIUM));
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void nestedIfElseInsideBranch() {
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
                        }
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
            products.insert(new Product("standard", Rates.MEDIUM));
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void ifElse_refiresOnOuterScopePropertyUpdate() {
        // DRLX uses PropertySpecificOption.ALWAYS (DrlxRuleAstRuntimeBuilder
        // line: TypeDeclaration.createTypeDeclarationForBean(cls, ALWAYS)), so
        // every property is watched by default. An if-guard like
        // `c.creditRating == Rating.LOW` re-evaluates naturally on creditRating
        // updates — no explicit watch list required (mirrors #23's
        // TestElementTest.test_refiresOnPropertyUpdate).
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
            Customer alice = new Customer("Alice", Rating.HIGH);   // initial: else branch
            org.kie.api.runtime.rule.FactHandle handle = customers.insert(alice);
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("budget", Rates.LOW));
            assertThat(session.fireAllRules()).isEqualTo(1);
            listener.getAfterMatchFired().clear();

            alice.setCreditRating(Rating.LOW);
            customers.update(handle, alice);
            // ALWAYS-mode property reactivity — re-evaluates without explicit watch list.
            assertThat(session.fireAllRules()).isEqualTo(1);
        });
    }

    @Test
    void noFinalElse_doesNotFireWhenNoBranchMatches() {
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
                    } else if (c.creditRating == Rating.MEDIUM) {
                        var p : /products[ rate == Rates.MEDIUM ]
                    },
                    do { System.out.println(c + " " + p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Carol", Rating.HIGH));    // matches no branch
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("standard", Rates.MEDIUM));
            assertThat(session.fireAllRules()).isZero();
            assertThat(listener.getAfterMatchFired()).isEmpty();
        });
    }

    @Test
    void elseIfChain_threeRatings() {
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
                    } else if (c.creditRating == Rating.MEDIUM) {
                        var p : /products[ rate == Rates.MEDIUM ]
                    } else {
                        var p : /products[ rate == Rates.LOW ]
                    },
                    do { System.out.println(c + " " + p); }
                }
                """;
        withSession(rule, (session, listener) -> {
            EntryPoint customers = session.getEntryPoint("customers");
            EntryPoint products = session.getEntryPoint("products");
            customers.insert(new Customer("Alice", Rating.MEDIUM));
            products.insert(new Product("luxury", Rates.HIGH));
            products.insert(new Product("standard", Rates.MEDIUM));
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
