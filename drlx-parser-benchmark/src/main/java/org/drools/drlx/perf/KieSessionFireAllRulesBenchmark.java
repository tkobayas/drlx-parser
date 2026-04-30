package org.drools.drlx.perf;

import java.io.IOException;
import java.io.PrintStream;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark measuring KieSession execution time (fireAllRules) comparing
 * KieBases built from DRLX-compiled sources vs executable-model.
 * This isolates runtime performance from compilation overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 3, jvmArgsAppend = {"-Dmvel3.compiler.lambda.persistence=false", "-Dmvel3.compiler.lambda.resetOnTestStartup=true"})
public class KieSessionFireAllRulesBenchmark {

    @Param({"100"})
    private int ruleCount;

    @Param({"alpha", "multiAlpha", "join", "multiJoin"})
    private String ruleType;

    private KieBase execModelKieBase;
    private KieBase drlxKieBase;
    private PrintStream originalOut;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String drlSource = DrlxSourceGenerator.generateDrl(ruleCount, ruleType);
        String drlxSource = DrlxSourceGenerator.generateDrlx(ruleCount, ruleType);

        // Build exec-model KieBase
        execModelKieBase = new KieHelper()
                .addContent(drlSource, ResourceType.DRL)
                .build(ExecutableModelProject.class);

        // Build DRLX KieBase (reset LambdaRegistry first)
        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();
        DrlxCompiler compiler = DrlxCompiler.noPersist();
        drlxKieBase = compiler.build(drlxSource);

        // Redirect System.out to avoid println noise from rule consequences
        originalOut = System.out;
        System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream()));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }

    @Benchmark
    public int fireWithExecutableModel() {
        KieSession ks = execModelKieBase.newKieSession();
        insertFacts(ks);
        int fired = ks.fireAllRules();
        ks.dispose();
        return fired;
    }

    @Benchmark
    public int fireWithDrlx() {
        KieSession ks = drlxKieBase.newKieSession();
        insertFacts(ks);
        int fired = ks.fireAllRules();
        ks.dispose();
        return fired;
    }

    private void insertFacts(KieSession ks) {
        switch (ruleType) {
            case "alpha":
                ks.getEntryPoint("persons").insert(new Person("John", 50));
                break;
            case "multiAlpha":
                Person p = new Person("John", 50);
                p.setValue1("A");
                p.setValue2("B");
                p.setValue3("C");
                ks.getEntryPoint("persons").insert(p);
                break;
            case "join":
                ks.getEntryPoint("persons1").insert(new Person("John", 50));
                ks.getEntryPoint("persons2").insert(new Person("Paul", 20));
                break;
            case "multiJoin":
                ks.getEntryPoint("persons1").insert(new Person("John", 50));
                ks.getEntryPoint("persons2").insert(new Person("Paul", 20));
                ks.getEntryPoint("persons3").insert(new Person("George", 40));
                break;
        }
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(KieSessionFireAllRulesBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
