package org.drools.drlx.builder;

import java.util.ArrayList;
import java.util.List;

import org.mvel3.MVELBatchCompiler;
import org.mvel3.lambdaextractor.ArtifactRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-build lambda compiler that extends {@link DrlxLambdaCompiler}.
 * Records metadata for each compiled lambda via the {@link #onLambdaCreated}
 * hook so that runtime builds can bypass MVEL compilation.
 * <p>
 * Records {@link ArtifactRef} (fqn + classFile) per lambda; no {@code physicalId}
 * crosses the DRLX persistence boundary.
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
            ArtifactRef ref = batchCompiler.getArtifactRef(info.handle());
            metadata.put(info.ruleName(), info.counterId(), ref, info.expression());
            LOG.info("Recorded pre-build metadata: {}.{} -> {} (classFile={})",
                    info.ruleName(), info.counterId(), ref.fqn(), ref.classFile());
        }

        pendingPreBuildInfos.clear();
    }
}
