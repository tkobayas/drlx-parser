package org.drools.drlx.builder;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.ContextEntry;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.MutableTypeConstraint;
import org.kie.api.runtime.rule.FactHandle;
import org.mvel3.ClassManager;
import org.mvel3.CompilerParameters;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;

public class DrlxLambdaConstraint extends MutableTypeConstraint<ContextEntry[]> {

    private String expression;

    private Class<?> patternType;

    private Evaluator<Object, Void, Boolean> evaluator;

    public DrlxLambdaConstraint() {
    }

    public DrlxLambdaConstraint(String expression, Class<?> patternType) {
        this.expression = expression;
        this.patternType = patternType;

        initializeLambdaConstraint();
    }

    private void initializeLambdaConstraint() {
        // TODO: manage source code, hash, and implement cache
        // TODO: extract declarations from the expression
        CompilerParameters<Object, Void, Boolean> evalInfo = MVEL.pojo(patternType,
                                                                       org.mvel3.transpiler.context.Declaration.of("age", int.class)
                )
                .<Boolean>out(Boolean.class)
                .expression(expression)
                .classManager(new ClassManager())
                .build();
        MVEL mvel = new MVEL();
        evaluator = mvel.compilePojoEvaluator(evalInfo);
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
        return new DrlxLambdaConstraint(this.expression, this.patternType);
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
        return evaluator.eval(handle.getObject());
    }

    @Override
    public boolean isAllowedCachedLeft(ContextEntry[] context, FactHandle handle) {
        return false; // TBD
    }

    @Override
    public boolean isAllowedCachedRight(BaseTuple tuple, ContextEntry[] context) {
        return false; // TBD
    }

    @Override
    public ContextEntry[] createContext() {
        return null;
    }
}
