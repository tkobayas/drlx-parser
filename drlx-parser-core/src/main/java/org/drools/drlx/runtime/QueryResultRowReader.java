package org.drools.drlx.runtime;

import java.lang.reflect.Method;
import java.util.Map;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.ReadAccessor;

public final class QueryResultRowReader implements ReadAccessor {

    private final Map<String, Integer> nameToIndex;

    public QueryResultRowReader(Map<String, Integer> nameToIndex) {
        this.nameToIndex = nameToIndex;
    }

    @Override
    public Object getValue(Object object) {
        return new QueryResultRow((Object[]) object, nameToIndex, null);
    }

    @Override
    public Object getValue(ValueResolver valueResolver, Object object) {
        return new QueryResultRow((Object[]) object, nameToIndex, valueResolver);
    }

    @Override public int getIndex() { return -1; }
    @Override public Class<?> getExtractToClass() { return QueryResultRow.class; }
    @Override public String getExtractToClassName() { return QueryResultRow.class.getName(); }
    @Override public ValueType getValueType() { return ValueType.OBJECT_TYPE; }
    @Override public boolean isSelfReference() { return false; }
    @Override public boolean isGlobal() { return false; }
    @Override public Method getNativeReadMethod() { return null; }
    @Override public String getNativeReadMethodName() { return "getValue"; }
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
