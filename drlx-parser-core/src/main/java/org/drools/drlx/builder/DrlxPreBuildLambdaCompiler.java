package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.List;

import org.mvel3.MVELBatchCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-build lambda compiler that extends {@link DrlxLambdaCompiler}.
 * Records metadata for each compiled lambda via the {@link #onLambdaCreated}
 * hook so that runtime builds can bypass MVEL compilation.
 */
public class DrlxPreBuildLambdaCompiler extends DrlxLambdaCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(DrlxPreBuildLambdaCompiler.class);

    private final DrlxLambdaMetadata metadata = new DrlxLambdaMetadata();

    private final List<PendingPreBuildInfo> pendingPreBuildInfos = new ArrayList<>();

    record PendingPreBuildInfo(String ruleName, int counterId, String expression, MVELBatchCompiler.LambdaHandle handle) {}

    public DrlxPreBuildLambdaCompiler(MVELBatchCompiler batchCompiler) {
        super(batchCompiler);
    }

    public DrlxLambdaMetadata getMetadata() {
        return metadata;
    }

    @Override
    protected void onLambdaCreated(int counter, String expression) {
        MVELBatchCompiler.LambdaHandle handle = pendingLambdas.get(pendingLambdas.size() - 1).handle();
        pendingPreBuildInfos.add(new PendingPreBuildInfo(currentRuleName, counter, expression, handle));
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
}
