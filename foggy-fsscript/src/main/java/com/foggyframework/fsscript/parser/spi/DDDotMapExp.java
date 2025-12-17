package com.foggyframework.fsscript.parser.spi;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.exp.AbstractExp;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DDDotMapExp extends MapEntry {
    Exp value;

    public DDDotMapExp(Exp value) {
        this.value = value;
    }


    public Object apply2List(List ll, ExpEvaluator ee) {
        Object v = value.evalValue(ee);
        if (v instanceof Collection) {
            ll.addAll((Collection) v);
        } else {
            ll.add(v);
        }
        return v;
    }

    @Override
    public void applyMap(Map m, ExpEvaluator ee) {
        Object mm = (Object) value.evalResult(ee);
        if (mm instanceof Map) {
            m.putAll((Map) mm);
        } else if (mm != null) {
            BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(mm.getClass());

            for (BeanProperty readMethod : beanInfoHelper.getReadMethods()) {
                Object v = readMethod.getBeanValue(mm);
                if (v != null) {
                    m.put(readMethod.getName(), v);
                }
            }
        }

    }

    @Override
    public String getKey() {
        return null;
    }
}
