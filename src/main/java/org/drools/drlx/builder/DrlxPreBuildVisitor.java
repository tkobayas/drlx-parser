package org.drools.drlx.builder;

import java.nio.file.Path;
import java.util.Map;

import org.antlr.v4.runtime.TokenStream;
import org.mvel3.Evaluator;
import org.mvel3.Type;
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

    public DrlxPreBuildVisitor(TokenStream tokens) {
        super(tokens);
    }

    public DrlxLambdaMetadata getMetadata() {
        return metadata;
    }

    @Override
    protected DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConstraint constraint = super.createLambdaConstraint(expression, patternType);

        recordMetadata(currentRuleName, capturedCounter, constraint.getEvaluator(), expression);
        return constraint;
    }

    @Override
    protected DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConsequence consequence = super.createLambdaConsequence(consequenceBlock, declarationTypes);

        recordMetadata(currentRuleName, capturedCounter, consequence.getEvaluator(), consequenceBlock);
        return consequence;
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
