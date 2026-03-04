package org.drools.drlx.perf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
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
 * Benchmarks KieBase building using pre-built artifacts (excludes compilation cost).
 *
 * Setup (Level.Trial) runs the full pre-build for both paths, persisting to disk:
 * - DRLX: DrlxCompiler.preBuild() creates .class files + metadata on disk
 * - Executable-model: KieBuilder.buildAll() + InternalKieModule.getBytes() writes kjar to disk
 *
 * Benchmark methods measure only the KieBase creation from persisted artifacts:
 * - DRLX: DrlxCompiler.build() loads pre-compiled classes via metadata from disk
 * - Executable-model: loads kjar from disk, adds to KieRepository, creates KieBase
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(value = 5, jvmArgsAppend = {"-Dmvel3.compiler.lambda.resetOnTestStartup=true"})
public class KieBaseBuildUsingPreBuildArtifactsBenchmark {

    @Param({"100"})
    private int ruleCount;

    private String drlxSource;
    private Path tempDir;
    private Path kjarPath;
    private ReleaseId releaseId;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        String drlSource = KieBaseBuildNoPersistenceBenchmark.generateDrl(ruleCount);
        drlxSource = KieBaseBuildNoPersistenceBenchmark.generateDrlx(ruleCount);
        tempDir = Files.createTempDirectory("prebuild-artifacts-bench-");

        // Pre-build DRLX: creates .class files + metadata in tempDir
        DrlxCompiler compiler = new DrlxCompiler(tempDir);
        compiler.preBuild(drlxSource);

        // Pre-build executable-model: buildAll() then persist kjar to disk
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules.drl", drlSource);
        InternalKieBuilder kieBuilder = (InternalKieBuilder) ks.newKieBuilder(kfs);
        kieBuilder.buildAll(ExecutableModelProject.class);

        InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();
        releaseId = kieModule.getReleaseId();

        // Write kjar to disk
        kjarPath = tempDir.resolve("rules.kjar");
        Files.write(kjarPath, kieModule.getBytes());

        // Remove from KieRepository so the benchmark must load from disk
        ks.getRepository().removeKieModule(releaseId);
    }

    @Setup(Level.Invocation)
    public void resetState() {
        // Clear in-memory LambdaRegistry so DRLX build loads classes from disk via metadata.
        // resetAndRemoveAllPersistedFiles() only deletes files under DEFAULT_PERSISTENCE_PATH,
        // so the pre-built artifacts in tempDir survive.
        LambdaRegistry.INSTANCE.resetAndRemoveAllPersistedFiles();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Benchmark
    public KieBase buildWithDrlx() throws IOException {
        DrlxCompiler compiler = new DrlxCompiler(tempDir);
        return compiler.build(drlxSource);
    }

    @Benchmark
    public KieBase buildWithExecutableModel() {
        KieServices ks = KieServices.Factory.get();
        Resource kjarResource = ks.getResources().newFileSystemResource(kjarPath.toFile());
        KieModule kieModule = ks.getRepository().addKieModule(kjarResource);
        KieContainer kieContainer = ks.newKieContainer(kieModule.getReleaseId());
        return kieContainer.getKieBase();
    }

    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        CommandLineOptions cmdOptions = new CommandLineOptions(args);
        Options opt = new OptionsBuilder()
                .parent(cmdOptions)
                .include(KieBaseBuildUsingPreBuildArtifactsBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
