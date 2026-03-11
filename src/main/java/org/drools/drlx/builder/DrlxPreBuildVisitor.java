package org.drools.drlx.builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.TokenStream;
import org.mvel3.Evaluator;
import org.mvel3.MVELCompiler;
import org.mvel3.Type;
import org.mvel3.javacompiler.KieMemoryCompiler;
import org.mvel3.lambdaextractor.LambdaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-build visitor that extends DrlxToRuleImplVisitor.
 * Compiles lambdas normally (via super) and records metadata mapping
 * for later reuse in runtime builds.
 */
public class DrlxPreBuildVisitor extends DrlxToRuleImplVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxPreBuildVisitor.class);

    private final DrlxLambdaMetadata metadata = new DrlxLambdaMetadata();

    // Deferred metadata recording for batch mode
    private final List<PendingPreBuildInfo> pendingPreBuildInfos = new ArrayList<>();

    record PendingPreBuildInfo(String ruleName, int counterId, String expression, String fqn) {}

    private Path outputDir;

    public DrlxPreBuildVisitor(TokenStream tokens) {
        super(tokens);
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    public DrlxLambdaMetadata getMetadata() {
        return metadata;
    }

    @Override
    protected DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConstraint constraint = super.createLambdaConstraint(expression, patternType, declarations);

        if (batchMode) {
            // In batch mode, evaluator is null — defer metadata recording until compileBatch
            String fqn = pendingLambdas.get(pendingLambdas.size() - 1).fqn();
            pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, capturedCounter, expression, fqn));
        } else {
            recordMetadata(currentRuleName, capturedCounter, constraint.getEvaluator(), expression);
        }
        return constraint;
    }

    @Override
    protected DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConsequence consequence = super.createLambdaConsequence(consequenceBlock, declarationTypes);

        if (batchMode) {
            // In batch mode, evaluator is null — defer metadata recording until compileBatch
            String fqn = pendingLambdas.get(pendingLambdas.size() - 1).fqn();
            pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, capturedCounter, consequenceBlock, fqn));
        } else {
            recordMetadata(currentRuleName, capturedCounter, consequence.getEvaluator(), consequenceBlock);
        }
        return consequence;
    }

    @Override
    public void compileBatch(ClassLoader classLoader) {
        if (pendingSources.isEmpty()) {
            return;
        }
        LOG.info("Batch-compiling and persisting {} lambda sources", pendingSources.size());

        // Batch compile + persist all classes in one javac call
        List<Path> persistedFiles = KieMemoryCompiler.compileAndPersist(
                sharedClassManager, pendingSources, classLoader, null, outputDir);

        // Resolve evaluators (same as parent)
        for (PendingLambda pl : pendingLambdas) {
            if (pl.target() instanceof DrlxLambdaConstraint c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            } else if (pl.target() instanceof DrlxLambdaBetaConstraint c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            } else if (pl.target() instanceof DrlxLambdaConsequence c) {
                c.setEvaluator(MVELCompiler.resolveEvaluator(sharedClassManager, pl.fqn()));
            }
        }

        // Record metadata: map each FQN to its persisted file path
        Map<String, Path> fqnToPath = new java.util.HashMap<>();
        for (Path persistedFile : persistedFiles) {
            // Convert file path back to FQN: e.g., "target/.../org/mvel3/GeneratorEvaluator__0.class" -> "org.mvel3.GeneratorEvaluator__0"
            String relativePath = outputDir.relativize(persistedFile).toString();
            String fqn = relativePath.replace('/', '.').replace(".class", "");
            fqnToPath.put(fqn, persistedFile);
        }

        for (PendingPreBuildInfo info : pendingPreBuildInfos) {
            Path classFilePath = fqnToPath.get(info.fqn());
            if (classFilePath != null) {
                metadata.put(info.ruleName(), info.counterId(), info.fqn(), classFilePath.toString(), info.expression());
                LOG.info("Recorded pre-build metadata: {}.{} -> {} ({})", info.ruleName(), info.counterId(), info.fqn(), classFilePath);
            } else {
                LOG.warn("No persisted class file for FQN {}, skipping metadata for {}.{}", info.fqn(), info.ruleName(), info.counterId());
            }
        }

        pendingSources.clear();
        pendingLambdas.clear();
        pendingPreBuildInfos.clear();
    }

    private void recordMetadata(String ruleName, int counterId, Evaluator<?, ?, ?> evaluator, String expression) {
        String className = evaluator.getClass().getName();
        String fqn = className.split("/0x")[0]; // strip hidden class suffix

        // extract physicalId from FQN suffix: "GeneratorEvaluator___<physicalId>"
        // the naming convention uses "___" (3 underscores from default) + "_" (1 from persistence) = total pattern "____<id>"
        int lastUnderscore = fqn.lastIndexOf('_');
        if (lastUnderscore < 0) {
            LOG.warn("Cannot extract physicalId from FQN {}, skipping metadata for {}.{}", fqn, ruleName, counterId);
            return;
        }
        String physicalIdStr = fqn.substring(lastUnderscore + 1);
        int physicalId;
        try {
            physicalId = Integer.parseInt(physicalIdStr);
        } catch (NumberFormatException e) {
            LOG.warn("Cannot parse physicalId from FQN {}, skipping metadata for {}.{}", fqn, ruleName, counterId);
            return;
        }

        Path classFilePath = LambdaRegistry.INSTANCE.getPhysicalPath(physicalId);
        if (classFilePath == null) {
            LOG.warn("No persisted class file for physicalId {}, skipping metadata for {}.{}", physicalId, ruleName, counterId);
            return;
        }

        metadata.put(ruleName, counterId, fqn, classFilePath.toString(), expression);
        LOG.info("Recorded pre-build metadata: {}.{} -> {} ({})", ruleName, counterId, fqn, classFilePath);
    }
}
