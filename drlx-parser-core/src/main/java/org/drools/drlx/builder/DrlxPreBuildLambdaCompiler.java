package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.drools.base.rule.constraint.Constraint;
import org.mvel3.Evaluator;
import org.mvel3.MVELBatchCompiler;
import org.mvel3.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-build lambda compiler that extends {@link DrlxLambdaCompiler}.
 * Compiles lambdas normally (via super) and records metadata mapping
 * for later reuse in runtime builds.
 */
public class DrlxPreBuildLambdaCompiler extends DrlxLambdaCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxPreBuildLambdaCompiler.class);

    private final DrlxLambdaMetadata metadata = new DrlxLambdaMetadata();

    private final List<PendingPreBuildInfo> pendingPreBuildInfos = new ArrayList<>();

    record PendingPreBuildInfo(String ruleName, int counterId, String expression, MVELBatchCompiler.LambdaHandle handle) {}

    public DrlxLambdaMetadata getMetadata() {
        return metadata;
    }

    @Override
    public DrlxLambdaConstraint createLambdaConstraint(String expression, Class<?> patternType, org.mvel3.transpiler.context.Declaration<?>[] declarations) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConstraint constraint = super.createLambdaConstraint(expression, patternType, declarations);

        if (batchMode) {
            MVELBatchCompiler.LambdaHandle handle = pendingLambdas.get(pendingLambdas.size() - 1).handle();
            pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, capturedCounter, expression, handle));
        } else {
            recordMetadata(currentRuleName, capturedCounter, constraint.getEvaluator(), expression);
        }
        return constraint;
    }

    @Override
    public Constraint createBetaLambdaConstraint(String expression, Class<?> patternType,
                                                 org.mvel3.transpiler.context.Declaration<?>[] patternDeclarations,
                                                 List<BoundVariable> referencedBindings) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        Constraint constraint = super.createBetaLambdaConstraint(expression, patternType, patternDeclarations, referencedBindings);

        if (batchMode) {
            MVELBatchCompiler.LambdaHandle handle = pendingLambdas.get(pendingLambdas.size() - 1).handle();
            pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, capturedCounter, expression, handle));
        } else {
            DrlxLambdaBetaConstraint betaConstraint = (DrlxLambdaBetaConstraint) constraint;
            recordMetadata(currentRuleName, capturedCounter, betaConstraint.getEvaluator(), expression);
        }
        return constraint;
    }

    @Override
    public DrlxLambdaConsequence createLambdaConsequence(String consequenceBlock, Map<String, Type<?>> declarationTypes) {
        int capturedCounter = lambdaCounter; // capture before super increments it
        DrlxLambdaConsequence consequence = super.createLambdaConsequence(consequenceBlock, declarationTypes);

        if (batchMode) {
            MVELBatchCompiler.LambdaHandle handle = pendingLambdas.get(pendingLambdas.size() - 1).handle();
            pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, capturedCounter, consequenceBlock, handle));
        } else {
            recordMetadata(currentRuleName, capturedCounter, consequence.getEvaluator(), consequenceBlock);
        }
        return consequence;
    }

    @Override
    public void compileBatch(ClassLoader classLoader) {
        if (pendingLambdas.isEmpty()) {
            return;
        }

        super.compileBatch(classLoader);

        for (PendingPreBuildInfo info : pendingPreBuildInfos) {
            MVELBatchCompiler.LambdaHandle handle = info.handle();
            String fqn = batchCompiler.getFqn(handle);
            int physicalId = batchCompiler.getPhysicalId(handle);
            metadata.put(info.ruleName(), info.counterId(), fqn, physicalId, info.expression());
            LOG.info("Recorded pre-build metadata: {}.{} -> {} (physicalId={})", info.ruleName(), info.counterId(), fqn, physicalId);
        }

        pendingPreBuildInfos.clear();
    }

    private void recordMetadata(String ruleName, int counterId, Evaluator<?, ?, ?> evaluator, String expression) {
        String className = evaluator.getClass().getName();
        String fqn = className.split("/0x")[0]; // strip hidden class suffix

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

        metadata.put(ruleName, counterId, fqn, physicalId, expression);
        LOG.info("Recorded pre-build metadata: {}.{} -> {} (physicalId={})", ruleName, counterId, fqn, physicalId);
    }
}
