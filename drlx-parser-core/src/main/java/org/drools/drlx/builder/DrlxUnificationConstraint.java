package org.drools.drlx.builder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.MutableTypeConstraint;
import org.drools.core.base.DroolsQueryImpl;
import org.kie.api.runtime.rule.FactHandle;

/**
 * A unification constraint for DRLX self-referencing query base cases.
 * Wraps an inner beta constraint and skips evaluation when the query parameter
 * is unbound (marked with Variable.v), mirroring old DRL's unification semantics.
 *
 * <p>When a query parameter is unbound (output direction), the constraint
 * always passes, allowing the Pattern to match any fact. When bound (input
 * direction), it delegates to the inner constraint for actual filtering.
 */
class DrlxUnificationConstraint extends MutableTypeConstraint<ContextEntry> {

    @SuppressWarnings("rawtypes")
    private final MutableTypeConstraint innerConstraint;
    private final int queryParamIndex;

    /**
     * @param innerConstraint the beta constraint to delegate to when the param is bound
     * @param queryParamIndex the positional index of the query parameter in the
     *                        DroolsQuery arguments array
     */
    @SuppressWarnings("rawtypes")
    DrlxUnificationConstraint(MutableTypeConstraint innerConstraint, int queryParamIndex) {
        this.innerConstraint = innerConstraint;
        this.queryParamIndex = queryParamIndex;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return innerConstraint.getRequiredDeclarations();
    }

    @Override
    public void replaceDeclaration(Declaration oldDecl, Declaration newDecl) {
        innerConstraint.replaceDeclaration(oldDecl, newDecl);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public DrlxUnificationConstraint clone() {
        return new DrlxUnificationConstraint((MutableTypeConstraint) innerConstraint.clone(), queryParamIndex);
    }

    @Override
    public ConstraintType getType() {
        return innerConstraint.getType();
    }

    @Override
    public boolean isTemporal() {
        return false;
    }

    @Override
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        return true; // Alpha evaluation shouldn't happen for unification constraints
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isAllowedCachedLeft(ContextEntry context, FactHandle handle) {
        if (isParamUnbound(context)) {
            return true;
        }
        return innerConstraint.isAllowedCachedLeft(context, handle);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, ContextEntry context) {
        if (isParamUnboundFromTuple(tuple)) {
            return true;
        }
        return innerConstraint.isAllowedCachedRight(tuple, context);
    }

    /**
     * Checks whether the query parameter at {@link #queryParamIndex} is unbound.
     * Mirrors {@code DroolsQueryImpl.getVariables()[index] != null} from
     * old DRL's unification.
     */
    private boolean isParamUnbound(ContextEntry context) {
        if (context instanceof DrlxLambdaBetaConstraint.DrlxBetaContextEntry betaCtx) {
            BaseTuple tuple = betaCtx.tuple;
            return isParamUnboundFromTuple(tuple);
        }
        return false;
    }

    private boolean isParamUnboundFromTuple(BaseTuple tuple) {
        if (tuple == null) return false;
        Object root = tuple.getObject(0);
        if (root instanceof DroolsQueryImpl query) {
            Object[] variables = query.getVariables();
            if (variables != null && queryParamIndex < variables.length) {
                return variables[queryParamIndex] != null;
            }
        }
        return false;
    }

    @Override
    public ContextEntry createContext() {
        return (ContextEntry) innerConstraint.createContext();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "unify(" + innerConstraint + ", paramIdx=" + queryParamIndex + ")";
    }
}
