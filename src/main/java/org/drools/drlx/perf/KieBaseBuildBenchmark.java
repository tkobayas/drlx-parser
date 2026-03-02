package org.drools.drlx.perf;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1, jvmArgsAppend = {"-Dmvel3.compiler.lambda.resetOnTestStartup=true"})
public class KieBaseBuildBenchmark {

    @Param({"100"})
    private int ruleCount;

    private String drlSource;
    private String drlxSource;

    @Setup(Level.Trial)
    public void setup() {
        drlSource = generateDrl(ruleCount);
        drlxSource = generateDrlx(ruleCount);
    }

    @Setup(Level.Invocation)
    public void resetState() {
        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();
    }

    @Benchmark
    public KieBase buildWithExecutableModel() {
        return new KieHelper()
                .addContent(drlSource, ResourceType.DRL)
                .build(ExecutableModelProject.class);
    }

    @Benchmark
    public KieBase buildWithDrlxOneStep() throws IOException {
        DrlxCompiler compiler = new DrlxCompiler();
        // no pre-build step, so build will compile lambdas on the fly
        return compiler.build(drlxSource);
    }

    static String generateDrl(int count) {
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

    static String generateDrlx(int count) {
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

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(KieBaseBuildBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
