package org.drools.drlx.builder;

import java.lang.reflect.Method;

import org.drools.base.base.DroolsQuery;
import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.ReadAccessor;

public final class DroolsQueryElementsReader implements ReadAccessor {

    public static final DroolsQueryElementsReader INSTANCE = new DroolsQueryElementsReader();

    private DroolsQueryElementsReader() {}

    @Override
    public Object getValue(Object object) {
        return ((DroolsQuery) object).getElements();
    }

    @Override
    public Object getValue(ValueResolver valueResolver, Object object) {
        return ((DroolsQuery) object).getElements();
    }

    @Override public int getIndex() { return -1; }
    @Override public Class<?> getExtractToClass() { return Object[].class; }
    @Override public String getExtractToClassName() { return "java.lang.Object[]"; }
    @Override public ValueType getValueType() { return ValueType.OBJECT_TYPE; }
    @Override public boolean isSelfReference() { return false; }
    @Override public boolean isGlobal() { return false; }
    @Override public Method getNativeReadMethod() { return null; }
    @Override public String getNativeReadMethodName() { return "getElements"; }
    @Override public boolean isNullValue(ValueResolver vr, Object o) { return getValue(vr, o) == null; }

    @Override public int getHashCode(Object o) {
        Object v = getValue(o);
        return v != null ? v.hashCode() : 0;
    }

    @Override public int getHashCode(ValueResolver vr, Object o) {
        Object v = getValue(vr, o);
        return v != null ? v.hashCode() : 0;
    }

    @Override public boolean getBooleanValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public byte getByteValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public char getCharValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public double getDoubleValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public float getFloatValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public int getIntValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public long getLongValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
    @Override public short getShortValue(ValueResolver vr, Object o) { throw new UnsupportedOperationException(); }
}
