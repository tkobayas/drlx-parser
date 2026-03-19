package org.drools.drlx.perf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.drools.drlx.tools.DrlxCompiler;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
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
 * Pre-build is automatically performed in a SEPARATE JVM process via {@link PreBuildRunner}
 * during {@code @Setup(Level.Trial)}. This ensures that:
 * <ul>
 *   <li>Compilation warm-up does not leak into load-time measurement</li>
 *   <li>Each forked JVM starts cold — matching real production deployment</li>
 *   <li>Multiple {@code ruleCount} params work automatically</li>
 * </ul>
 *
 * <pre>
 * java -jar target/drlx-benchmarks.jar \
 *   -jvmArgs "-Xms4g -Xmx4g" \
 *   -f 5 -wi 0 -i 1 -bm ss -p ruleCount=100 \
 *   org.drools.drlx.perf.KieBaseBuildUsingPreBuildArtifactsBenchmark
 * </pre>
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(value = 5)
public class KieBaseBuildUsingPreBuildArtifactsBenchmark {

    // Use the well-known default path directly to avoid triggering LambdaRegistry class init
    // before pre-build artifacts exist. LambdaRegistry must initialize AFTER @Setup completes.
    private static final Path DEFAULT_OUTPUT_DIR = Path.of(
            System.getProperty("mvel3.compiler.lambda.persistence.path", "target/generated-classes/mvel"));

    @Param({"100"})
    private int ruleCount;

    @Param({"alpha", "multiAlpha", "join", "multiJoin"})
    private String ruleType;

    private String drlxSource;
    private Path kjarPath;
    private Path kjarDir;

    @Setup(Level.Trial)
    public void setup() throws IOException, InterruptedException {
        // Clean up the default output directory once at the beginning
        if (Files.exists(DEFAULT_OUTPUT_DIR)) {
            Files.walk(DEFAULT_OUTPUT_DIR)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }

        drlxSource = KieBaseBuildNoPersistenceBenchmark.generateDrlx(ruleCount, ruleType);
        kjarDir = Files.createTempDirectory("prebuild-kjar-");
        kjarPath = kjarDir.resolve("rules.kjar");

        // Launch PreBuildRunner in a separate JVM process
        // DRLX artifacts go to DEFAULT_OUTPUT_DIR, kjar goes to kjarDir
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath,
                PreBuildRunner.class.getName(),
                DEFAULT_OUTPUT_DIR.toAbsolutePath().toString(),
                kjarDir.toAbsolutePath().toString(),
                String.valueOf(ruleCount),
                ruleType);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("PreBuildRunner failed with exit code " + exitCode);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        if (Files.exists(DEFAULT_OUTPUT_DIR)) {
            Files.walk(DEFAULT_OUTPUT_DIR)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        if (kjarDir != null && Files.exists(kjarDir)) {
            Files.walk(kjarDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Benchmark
    public KieBase buildWithDrlx() throws IOException {
        DrlxCompiler compiler = new DrlxCompiler();
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
                .build();
        new Runner(opt).run();
    }
}
