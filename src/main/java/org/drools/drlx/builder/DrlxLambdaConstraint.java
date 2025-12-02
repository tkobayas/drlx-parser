package org.drools.drlx.builder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.MutableTypeConstraint;
import org.kie.api.runtime.rule.FactHandle;

public class DrlxLambdaConstraint extends MutableTypeConstraint {

    private String expression;

    public DrlxLambdaConstraint() {
    }

    public DrlxLambdaConstraint(String expression) {
        this.expression = expression;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return new Declaration[0];
    }

    @Override
    public void replaceDeclaration(Declaration declaration, Declaration declaration1) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DrlxLambdaConstraint clone() {
        return new DrlxLambdaConstraint(this.expression);
    }

    @Override
    public ConstraintType getType() {
        return ConstraintType.ALPHA;
    }

    @Override
    public boolean isTemporal() {
        return false;
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
    public boolean isAllowed(FactHandle handle, ValueResolver valueResolver) {
        return true; // TBD
    }

    @Override
    public boolean isAllowedCachedLeft(Object context, FactHandle handle) {
        return false; // TBD
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, Object context) {
        return false; // TBD
    }

    @Override
    public Object createContext() {
        return null;
    }
}
