package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.KeyValueMapEntry;
import com.foggyframework.fsscript.parser.spi.MapEntry;

public class MapVarDefExp implements Exp {
    MapExp mapExp;
    Exp valueExp;

    public MapVarDefExp(MapExp mapExp, Exp valueExp) {
        this.mapExp = mapExp;
        this.valueExp = valueExp;

    }

    @Override
    public Object evalValue(ExpEvaluator ee) {

        Object value = valueExp.evalResult(ee);
        if(value == null){
            for (MapEntry mapEntry : mapExp.ll) {
                if(mapEntry instanceof KeyValueMapEntry){
                    ee.setVar(((KeyValueMapEntry)mapEntry).getKey(),null);
                }else{
                    throw new UnsupportedOperationException();
                }


            }
        }else{
            BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(value.getClass());
            String key;
            Object keyValue;
            for (MapEntry mapEntry : mapExp.ll) {

                if(mapEntry instanceof KeyValueMapEntry){

                    key = ((KeyValueMapEntry)mapEntry).getKey();
                    keyValue = beanInfoHelper.getBeanProperty(key).getBeanValue(value);
                    ee.setVar(key,keyValue);
                }else{
                    throw new UnsupportedOperationException();
                }
            }
        }

        return value;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}
