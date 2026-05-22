package org.drools.drlx.builder.syntax;

import org.drools.drlx.builder.DrlxRuleAstModel.CompilationUnitIR;
import org.drools.drlx.builder.DrlxRuleAstModel.PatternIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleIR;
import org.drools.drlx.builder.DrlxRuleAstModel.RuleParameterIR;
import org.drools.drlx.builder.DrlxRuleBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryParseTest {

    @Test
    void queryParametersParsed() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule FindPerson(String queryName, Person result) {
                    Person result : /persons[name == queryName],
                }
                """;

        CompilationUnitIR ast = DrlxRuleBuilder.parseToAst(source);
        RuleIR rule = ast.rules().get(0);
        assertThat(rule.name()).isEqualTo("FindPerson");
        assertThat(rule.parameters()).hasSize(2);
        assertThat(rule.parameters().get(0)).isEqualTo(new RuleParameterIR("String", "queryName"));
        assertThat(rule.parameters().get(1)).isEqualTo(new RuleParameterIR("Person", "result"));
    }

    @Test
    void regularRuleHasEmptyParameters() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R1 {
                    Person p : /persons[age > 10],
                    do { System.out.println(p); }
                }
                """;

        CompilationUnitIR ast = DrlxRuleBuilder.parseToAst(source);
        RuleIR rule = ast.rules().get(0);
        assertThat(rule.parameters()).isEmpty();
    }

    @Test
    void varOutputArgParsed() {
        String source = """
                package org.drools.drlx.parser;

                import org.drools.drlx.domain.Person;

                import org.drools.drlx.ruleunit.MyUnit;
                unit MyUnit;

                rule R1 {
                    /personsByAge(25, var p),
                    do { System.out.println("ok"); }
                }
                """;

        CompilationUnitIR ast = DrlxRuleBuilder.parseToAst(source);
        RuleIR rule = ast.rules().get(0);
        PatternIR pattern = (PatternIR) rule.lhs().get(0);
        assertThat(pattern.positionalArgs()).containsExactly("25", "var p");
    }
}
