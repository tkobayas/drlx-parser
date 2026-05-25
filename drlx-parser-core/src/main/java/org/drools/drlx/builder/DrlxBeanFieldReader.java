package org.drools.drlx.builder;

import java.lang.reflect.Method;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.base.rule.accessor.ReadAccessor;

/**
 * Minimal {@link ReadAccessor} that extracts a single bean property via its
 * getter method. Used for output bindings (e.g. {@code var z}) in positional
 * pattern matches within self-referencing query base cases.
 */
class DrlxBeanFieldReader implements ReadAccessor {

    private final Method getter;
    private final Class<?> fieldType;

    DrlxBeanFieldReader(Method getter, Class<?> fieldType) {
        this.getter = getter;
        this.fieldType = fieldType;
    }

    @Override
    public Object getValue(Object object) {
        try {
            return object != null ? getter.invoke(object) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read property via " + getter.getName(), e);
        }
    }

    @Override
    public Object getValue(ValueResolver valueResolver, Object object) {
        return getValue(object);
    }

    @Override
    public Class<?> getExtractToClass() {
        return fieldType;
    }

    @Override
    public String getExtractToClassName() {
        return fieldType.getName();
    }

    @Override
    public ValueType getValueType() {
        return ValueType.OBJECT_TYPE;
    }

    @Override
    public boolean isSelfReference() {
        return false;
    }

    @Override
    public int getIndex() {
        return -1;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    public boolean getBooleanValue(ValueResolver vr, Object o) {
        return (boolean) getValue(vr, o);
    }

    @Override
    public byte getByteValue(ValueResolver vr, Object o) {
        return (byte) getValue(vr, o);
    }

    @Override
    public char getCharValue(ValueResolver vr, Object o) {
        return (char) getValue(vr, o);
    }

    @Override
    public double getDoubleValue(ValueResolver vr, Object o) {
        return (double) getValue(vr, o);
    }

    @Override
    public float getFloatValue(ValueResolver vr, Object o) {
        return (float) getValue(vr, o);
    }

    @Override
    public int getIntValue(ValueResolver vr, Object o) {
        return (int) getValue(vr, o);
    }

    @Override
    public long getLongValue(ValueResolver vr, Object o) {
        return (long) getValue(vr, o);
    }

    @Override
    public short getShortValue(ValueResolver vr, Object o) {
        return (short) getValue(vr, o);
    }

    @Override
    public int getHashCode(Object object) {
        Object val = getValue(object);
        return val != null ? val.hashCode() : 0;
    }

    @Override
    public int getHashCode(ValueResolver vr, Object object) {
        return getHashCode(object);
    }

    @Override
    public boolean isNullValue(ValueResolver vr, Object object) {
        return getValue(object) == null;
    }

    @Override
    public Method getNativeReadMethod() {
        return getter;
    }

    @Override
    public String getNativeReadMethodName() {
        return getter.getName();
    }
}
