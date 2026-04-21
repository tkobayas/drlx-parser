package org.drools.drlx.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.drools.drlx.domain.Address;
import org.drools.drlx.domain.Person;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.mvel3.lambdaextractor.LambdaRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DrlxCompiler#noPersist()} mode.
 *
 * <p>This test class must be run in a JVM with
 * {@code -Dmvel3.compiler.lambda.persistence=false} so that
 * {@code LambdaRegistry.PERSISTENCE_ENABLED} is {@code false}.
 * The Maven surefire plugin is configured to run this class in a separate execution
 * with the required system property.
 */
@EnabledIfSystemProperty(named = "mvel3.compiler.lambda.persistence", matches = "false")
class DrlxCompilerNoPersistTest {

    private static final String SINGLE_RULE = """
            package org.drools.drlx.parser;

            import org.drools.drlx.domain.Person;

            import org.drools.drlx.ruleunit.MyUnit;
            unit MyUnit;

            rule CheckAge {
                Person p : /persons[ age > 18 ],
                do { System.out.println(p); }
            }
            """;

    private static final String MULTI_RULE = """
            package org.drools.drlx.parser;

            import org.drools.drlx.domain.Person;
            import org.drools.drlx.domain.Address;

            import org.drools.drlx.ruleunit.MyUnit;
            unit MyUnit;

            rule CheckAge {
                Person p : /persons[ age > 18 ],
                do { System.out.println(p); }
            }

            rule CheckCity {
                Address a : /addresses[ city == "Tokyo" ],
                do { System.out.println(a); }
            }
            """;

    @Test
    void testNoPersistBuild() throws IOException {
        DrlxCompiler compiler = DrlxCompiler.noPersist();

        KieBase kieBase = compiler.build(SINGLE_RULE);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 25));
        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(1);
        kieSession.dispose();
    }

    @Test
    void testNoPersistMultiRuleBuild() throws IOException {
        DrlxCompiler compiler = DrlxCompiler.noPersist();

        KieBase kieBase = compiler.build(MULTI_RULE);

        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 25));
        kieSession.getEntryPoint("addresses").insert(new Address("Tokyo"));
        int fired = kieSession.fireAllRules();
        assertThat(fired).isEqualTo(2);
        kieSession.dispose();
    }

    @Test
    void testNoPersistDoesNotWriteFiles() throws IOException {
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        compiler.build(SINGLE_RULE);

        Path defaultPath = LambdaRegistry.DEFAULT_PERSISTENCE_PATH;
        if (Files.exists(defaultPath)) {
            try (Stream<Path> files = Files.walk(defaultPath)) {
                long classFileCount = files
                        .filter(p -> p.toString().endsWith(".class"))
                        .count();
                assertThat(classFileCount).isZero();
            }
        }
    }

    @Test
    void testPreBuildThrowsInNoPersistMode() {
        DrlxCompiler compiler = DrlxCompiler.noPersist();

        assertThatThrownBy(() -> compiler.preBuild(SINGLE_RULE))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("preBuild is not supported in no-persist mode");
    }

    @Test
    void testIsPersistReturnsFalse() {
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        assertThat(compiler.isPersist()).isFalse();
    }
}
