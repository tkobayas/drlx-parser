package org.drools.drlx.builder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.IntervalProviderConstraint;
import org.drools.base.rule.MutableTypeConstraint;
import org.drools.base.time.Interval;
import org.drools.model.functions.temporal.TemporalPredicate;
import org.kie.api.runtime.rule.EventHandle;
import org.kie.api.runtime.rule.FactHandle;

public class DrlxTemporalConstraint
        extends MutableTypeConstraint<ContextEntry>
        implements IntervalProviderConstraint {

    private final TemporalPredicate temporalPredicate;
    private final Declaration[] requiredDeclarations;
    private final Interval interval;

    public DrlxTemporalConstraint(TemporalPredicate predicate, Declaration[] decls) {
        this.temporalPredicate = predicate;
        this.requiredDeclarations = decls;
        var modelInterval = predicate.getInterval();
        this.interval = new Interval(
                modelInterval.getLowerBound(), modelInterval.getUpperBound());
    }

    @Override
    public boolean isTemporal() {
        return true;
    }

    @Override
    public ConstraintType getType() {
        return ConstraintType.BETA;
    }

    @Override
    public Interval getInterval() {
        return interval;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return requiredDeclarations;
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        for (int i = 0; i < requiredDeclarations.length; i++) {
            if (requiredDeclarations[i].equals(oldDecl)) {
                requiredDeclarations[i] = newDecl;
            }
        }
    }

    @Override
    public DrlxTemporalConstraint clone() {
        return new DrlxTemporalConstraint(temporalPredicate, requiredDeclarations.clone());
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        throw new UnsupportedOperationException(
                "Temporal constraint should not be evaluated as alpha");
    }

    @Override
    public boolean isAllowedCachedLeft(ContextEntry context, FactHandle handle) {
        DrlxLambdaBetaConstraint.DrlxBetaContextEntry ctx =
                (DrlxLambdaBetaConstraint.DrlxBetaContextEntry) context;
        EventHandle thisEvent = (EventHandle) handle;
        EventHandle otherEvent = (EventHandle) ctx.tuple.get(requiredDeclarations[0]);
        return evaluateTemporal(thisEvent, otherEvent);
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, ContextEntry context) {
        DrlxLambdaBetaConstraint.DrlxBetaContextEntry ctx =
                (DrlxLambdaBetaConstraint.DrlxBetaContextEntry) context;
        EventHandle thisEvent = (EventHandle) ctx.handle;
        EventHandle otherEvent = (EventHandle) tuple.get(requiredDeclarations[0]);
        return evaluateTemporal(thisEvent, otherEvent);
    }

    private boolean evaluateTemporal(EventHandle thisEvent, EventHandle otherEvent) {
        long start1 = thisEvent.getStartTimestamp();
        long dur1   = thisEvent.getDuration();
        long end1   = thisEvent.getEndTimestamp();
        long start2 = otherEvent.getStartTimestamp();
        long dur2   = otherEvent.getDuration();
        long end2   = otherEvent.getEndTimestamp();
        if (temporalPredicate.isThisOnRight()) {
            return temporalPredicate.evaluate(start2, dur2, end2, start1, dur1, end1);
        }
        return temporalPredicate.evaluate(start1, dur1, end1, start2, dur2, end2);
    }

    @Override
    public ContextEntry createContext() {
        return new DrlxLambdaBetaConstraint.DrlxBetaContextEntry();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toString() {
        return "temporal:" + temporalPredicate;
    }
}
