package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.exp.IdExp;

import java.io.Serializable;
import java.util.Map;

public class KeyValueMapEntry extends MapEntry {

    /**
     *
     */
    private static final long serialVersionUID = 4464499123139780557L;

    final String key;

    final Exp value;

    @Override
    public void applyMap(Map m, ExpEvaluator ee) {
        m.put(key, value.evalResult(ee));
    }

    public KeyValueMapEntry(String i, Exp v) {
        key = i;
        this.value = v;
    }

    public KeyValueMapEntry(String v) {
        key = v;
        this.value = new IdExp(v);
    }

    public KeyValueMapEntry(IdExp v) {
        key = v.value;
        this.value = v;
    }

    public String getKey() {
        return key;
    }

    public Exp getValue() {
        return value;
    }


    // public void setKey(String key) {
    // this.key = key;
    // }

    // public void setValue(Exp value) {
    // this.value = value;
    // }

}
