package org.drools.drlx.perf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.modelcompiler.CanonicalKieModule;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.internal.builder.InternalKieBuilder;
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
 * Benchmarks the pre-build phase: compile rule source and persist compiled artifacts to disk.
 * This is the one-time, build-time step that a user would run before deploying.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(value = 5, jvmArgsAppend = {"-Dmvel3.compiler.lambda.resetOnTestStartup=true"})
public class KieBasePreBuildPersistenceBenchmark {

    @Param({"100"})
    private int ruleCount;

    @Param({"alpha", "join"})
    private String ruleType;

    private String drlSource;
    private String drlxSource;
    private Path tempDir;

    @Setup(Level.Trial)
    public void setup() {
        drlSource = KieBaseBuildNoPersistenceBenchmark.generateDrl(ruleCount, ruleType);
        drlxSource = KieBaseBuildNoPersistenceBenchmark.generateDrlx(ruleCount, ruleType);
    }

    @Setup(Level.Invocation)
    public void setupInvocation() throws IOException {
        tempDir = Files.createTempDirectory("prebuild-bench-");
        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();
    }

    @TearDown(Level.Invocation)
    public void tearDownInvocation() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Benchmark
    public void preBuildWithDrlx() throws IOException {
        DrlxCompiler compiler = new DrlxCompiler(tempDir);
        compiler.preBuild(drlxSource);
    }

    @Benchmark
    public void preBuildWithExecutableModel() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules.drl", drlSource);
        InternalKieBuilder kieBuilder = (InternalKieBuilder) ks.newKieBuilder(kfs);
        kieBuilder.buildAll(ExecutableModelProject.class);

        InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();
        MemoryFileSystem mfs;
        if (kieModule instanceof CanonicalKieModule) {
            mfs = ((MemoryKieModule) ((CanonicalKieModule) kieModule).getInternalKieModule())
                    .getMemoryFileSystem();
        } else {
            mfs = ((MemoryKieModule) kieModule).getMemoryFileSystem();
        }

        mfs.writeAsFs(tempDir.toFile());
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(KieBasePreBuildPersistenceBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
