package com.foggyframework.fsscript.parser.spi;

public interface VarDef {
    void setValue(Object x);

    Object getValue();

    String getName();
}
