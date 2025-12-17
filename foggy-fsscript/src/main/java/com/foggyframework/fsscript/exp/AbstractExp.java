package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;

public abstract class AbstractExp<T> implements Exp {

    public T value;

    public AbstractExp(T value) {
        super();
        this.value = value;
    }

    public String getString() {
        return (String) value;
    }

    public T getValue() {
        return value;
    }

    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public String toString() {
        return value == null ? "" : value.toString();
    }
}
