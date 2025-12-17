package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.parser.spi.VarDef;

public class SimpleVarDef implements VarDef {

    Object value;

    public SimpleVarDef() {
    }

    public SimpleVarDef( String name,Object value) {
        this.value = value;
        this.name = name;
    }

    String name;

    @Override
    public void setValue(Object x) {
        value = x;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public String getName() {
        return name;
    }
}
