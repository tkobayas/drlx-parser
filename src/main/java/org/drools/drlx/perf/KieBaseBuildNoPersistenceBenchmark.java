package org.drools.drlx.perf;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.drools.drlx.domain.Person;
import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.mvel3.lambdaextractor.LambdaRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * KieBase build is one-time process after JVM startup in real use cases,
 * so we use SingleShotTime mode with no warmup iterations to measure cold build time.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(value = 5, jvmArgsAppend = {"-Dmvel3.compiler.lambda.persistence=false", "-Dmvel3.compiler.lambda.resetOnTestStartup=true"})
public class KieBaseBuildNoPersistenceBenchmark {

    @Param({"100"})
    private int ruleCount;

    @Param({"alpha", "join"})
    private String ruleType;

    private String drlSource;
    private String drlxSource;

    @Setup(Level.Trial)
    public void setup() {
        drlSource = generateDrl(ruleCount, ruleType);
        drlxSource = generateDrlx(ruleCount, ruleType);
    }

    @Setup(Level.Invocation)
    public void resetState() {
        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();
    }

    // This method can be used to verify that the built KieBase is valid
    private void runKieSessionDrl() throws Exception {
        KieBase kieBase = new KieHelper()
                .addContent(drlSource, ResourceType.DRL)
                .build(ExecutableModelProject.class);
        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 30));
        kieSession.fireAllRules();
        kieSession.dispose();
    }

    @Benchmark
    public KieBase buildWithExecutableModel() {
        return new KieHelper()
                .addContent(drlSource, ResourceType.DRL)
                .build(ExecutableModelProject.class);
    }

    // This method can be used to verify that the built KieBase is valid
    private void runKieSessionDrlx() throws Exception {
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        KieBase kieBase = compiler.build(drlxSource);
        KieSession kieSession = kieBase.newKieSession();
        kieSession.getEntryPoint("persons").insert(new Person("John", 30));
        kieSession.fireAllRules();
        kieSession.dispose();
    }

    @Benchmark
    public KieBase buildWithDrlxNoPersist() throws IOException {
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        return compiler.build(drlxSource);
    }

    static String generateDrl(int count, String ruleType) {
        return "join".equals(ruleType) ? generateDrlJoin(count) : generateDrlAlpha(count);
    }

    static String generateDrlx(int count, String ruleType) {
        return "join".equals(ruleType) ? generateDrlxJoin(count) : generateDrlxAlpha(count);
    }

    static String generateDrlAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p : Person( age > ").append(i).append(" ) from entry-point \"persons\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxAlpha(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p : /persons[ age > ").append(i).append(" ],\n");
            sb.append("    do { System.out.println(p); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    static String generateDrlJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule \"Rule_").append(i).append("\"\n");
            sb.append("when\n");
            sb.append("    $p1 : Person( age > ").append(i).append(" ) from entry-point \"persons1\"\n");
            sb.append("    $p2 : Person( age < $p1.age ) from entry-point \"persons2\"\n");
            sb.append("then\n");
            sb.append("    System.out.println($p2);\n");
            sb.append("end\n\n");
        }
        return sb.toString();
    }

    static String generateDrlxJoin(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.drools.drlx.perf;\n\n");
        sb.append("import org.drools.drlx.domain.Person;\n\n");
        sb.append("unit MyUnit;\n\n");
        for (int i = 0; i < count; i++) {
            sb.append("rule Rule_").append(i).append(" {\n");
            sb.append("    Person p1 : /persons1[ age > ").append(i).append(" ],\n");
            sb.append("    Person p2 : /persons2[ age < p1.age ],\n");
            sb.append("    do { System.out.println(p2); }\n");
            sb.append("}\n\n");
        }
        return sb.toString();
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(KieBaseBuildNoPersistenceBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
