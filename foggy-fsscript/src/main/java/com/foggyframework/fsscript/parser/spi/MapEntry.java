package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.fsscript.exp.IdExp;

import java.io.Serializable;
import java.util.Map;

public  abstract class MapEntry implements Serializable {

    public abstract void applyMap(Map m, ExpEvaluator ee);

public abstract String getKey();
    // public void setKey(String key) {
    // this.key = key;
    // }

    // public void setValue(Exp value) {
    // this.value = value;
    // }

}
