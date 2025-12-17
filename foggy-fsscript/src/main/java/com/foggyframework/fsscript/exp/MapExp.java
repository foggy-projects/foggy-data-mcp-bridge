package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.KeyValueMapEntry;
import com.foggyframework.fsscript.parser.spi.MapEntry;
import lombok.Getter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class MapExp implements Exp, Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 984313084333757885L;

    final List<MapEntry> ll;

    public MapExp(List<MapEntry> ll) {
        this.ll = ll;

    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Map m = new HashMap();
        for (MapEntry e : ll) {
//            m.put(e.getKey(), e.getValue().evalResult(ee));

            e.applyMap( m,ee);
        }
        return m;
    }
    public static MapEntry toMapEntry(Object e){
        if(e instanceof MapEntry){
            return (MapEntry) e;
        }else if(e instanceof String){
            return new KeyValueMapEntry((String) e);
        }else if(e instanceof IdExp){
            return new KeyValueMapEntry((IdExp) e);
        }
        throw new UnsupportedOperationException("不支持的MapEntry类型: "+e);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Map.class;
    }

    @Override
    public String toString() {
        return "MAP";
    }
}
