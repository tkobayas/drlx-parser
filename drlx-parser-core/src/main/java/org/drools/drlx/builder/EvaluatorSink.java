package org.drools.drlx.builder;

import org.mvel3.Evaluator;

/**
 * Implemented by lambda-carrying classes ({@link DrlxLambdaConstraint},
 * {@link DrlxLambdaBetaConstraint}, {@link DrlxLambdaConsequence}) so that
 * post-batch compilation can bind the compiled evaluator uniformly.
 */
interface EvaluatorSink {
    void bindEvaluator(Evaluator<?, ?, ?> evaluator);
}
