package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.springframework.beans.BeanUtils;

import java.util.Map;

public class PropertySetExp extends AbstractExp<Exp> {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    PropertyExp exp;

    public PropertySetExp(PropertyExp exp, Exp value) {
        super(value);
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object l = exp.exp.evalResult(ee);
        if (l == null) {
            return null;
        }
        Object v = value.evalResult(ee);
        if (l instanceof Map) {
            ((Map<Object, Object>) l).put(exp.value, v);
        } else if (l != null) {
            BeanInfoHelper beanInfoHelper = BeanInfoHelper.getClassHelper(l.getClass());
            beanInfoHelper.getBeanProperty(exp.value,true).setBeanValue(l,v);
        }

//        BeanUtils.setObjectProperty(l, exp.value, v);
//        throw  new UnsupportedOperationException();
        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }

    @Override
    public String toString() {
        return exp.toString() + " = " + value;
    }
}
