package org.drools.drlx.builder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;

import org.drools.base.base.ValueResolver;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.accessor.DataProvider;

public class OopathFieldDataProvider implements DataProvider {

    private final Declaration sourceDeclaration;
    private final Method getter;

    public OopathFieldDataProvider(Declaration sourceDeclaration, Method getter) {
        this.sourceDeclaration = sourceDeclaration;
        this.getter = getter;
    }

    @Override
    public Declaration[] getRequiredDeclarations() {
        return new Declaration[]{ sourceDeclaration };
    }

    @Override
    public Object createContext() {
        return null;
    }

    @Override
    public Iterator getResults(BaseTuple tuple, ValueResolver valueResolver,
                               Object providerContext) {
        Object sourceObj = sourceDeclaration.getValue(valueResolver,
                tuple.getFactHandle().getObject());
        Object result;
        try {
            result = getter.invoke(sourceObj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke getter " + getter.getName()
                    + " on " + sourceObj, e);
        }
        if (result == null) {
            return Collections.emptyIterator();
        }
        if (result instanceof Iterable<?> iterable) {
            return iterable.iterator();
        }
        return Collections.singletonList(result).iterator();
    }

    @Override
    public DataProvider clone() {
        return this;
    }

    @Override
    public void replaceDeclaration(Declaration declaration, Declaration resolved) {
    }

    @Override
    public boolean isReactive() {
        return false;
    }
}
