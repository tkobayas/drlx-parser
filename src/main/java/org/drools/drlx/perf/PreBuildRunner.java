package org.drools.drlx.perf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.drlx.tools.DrlxCompiler;
import org.drools.model.codegen.ExecutableModelProject;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.internal.builder.InternalKieBuilder;

/**
 * Pre-builds artifacts for both DRLX and executable-model to a specified directory.
 * Run this in a separate JVM before running KieBaseBuildUsingPreBuildArtifactsBenchmark.
 *
 * <pre>
 * # Step 1: Pre-build artifacts
 * java -cp target/drlx-benchmarks.jar org.drools.drlx.perf.PreBuildRunner /tmp/prebuild-dir 100
 *
 * # Step 2: Run benchmark with pre-built artifacts (separate JVM)
 * java -jar target/drlx-benchmarks.jar \
 *   -jvmArgs "-Xms4g -Xmx4g -Dbenchmark.prebuild.dir=/tmp/prebuild-dir" \
 *   -f 5 -wi 0 -i 1 -bm ss \
 *   org.drools.drlx.perf.KieBaseBuildUsingPreBuildArtifactsBenchmark
 * </pre>
 */
public class PreBuildRunner {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: PreBuildRunner <output-dir> [ruleCount]");
            System.err.println("  output-dir : directory to write pre-built artifacts");
            System.err.println("  ruleCount  : number of rules to generate (default: 100)");
            System.exit(1);
        }

        Path outputDir = Path.of(args[0]);
        int ruleCount = args.length >= 2 ? Integer.parseInt(args[1]) : 100;

        // Clean up stale artifacts from previous runs
        if (Files.exists(outputDir)) {
            Files.walk(outputDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
        Files.createDirectories(outputDir);
        System.out.println("Pre-building " + ruleCount + " rules to: " + outputDir);

        // Pre-build DRLX
        String drlxSource = KieBaseBuildNoPersistenceBenchmark.generateDrlx(ruleCount);
        DrlxCompiler compiler = new DrlxCompiler(outputDir);
        compiler.preBuild(drlxSource);
        System.out.println("DRLX pre-build complete.");

        // Pre-build executable-model kjar
        String drlSource = KieBaseBuildNoPersistenceBenchmark.generateDrl(ruleCount);
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules.drl", drlSource);
        InternalKieBuilder kieBuilder = (InternalKieBuilder) ks.newKieBuilder(kfs);
        kieBuilder.buildAll(ExecutableModelProject.class);

        InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();
        Path kjarPath = outputDir.resolve("rules.kjar");
        Files.write(kjarPath, kieModule.getBytes());
        System.out.println("Executable-model pre-build complete. kjar: " + kjarPath);

        System.out.println("Done. Run benchmark with: -Dbenchmark.prebuild.dir=" + outputDir.toAbsolutePath());
    }
}
