package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;
import lombok.Getter;
import org.springframework.beans.BeanUtils;

@Getter
public class PropertyExp extends AbstractExp<String> {

    Exp exp;

    public PropertyExp(Exp exp, String name) {
        super(name);
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {

        Object obj = exp.evalResult(evaluator);
        return getPropertyValue(obj);
    }

    protected boolean checkLeft() {
        return true;
    }

    public Object getPropertyValue(Object obj) {

        if (obj == null) {
            if (checkLeft()) {
                throw RX.throwBUserTip("左值为空: " + exp + "." + value, "系统异常");
            }
            return null;
        }
        // if(obj.getClass().getName().indexOf("EditResultSetResultSetProcessor")>=0){
        // // Systemx.out.println(1209878);
        // }
        if (obj instanceof PropertyHolder) {
            Object x = ((PropertyHolder) obj).getProperty(value);
            if (x != PropertyHolder.NO_MATCH) {
                return x;
            }
        }
        return BeanInfoHelper.getClassHelper(obj.getClass()).getBeanProperty(value, true).getBeanValue(obj);

    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        Class<?> clz = exp.getReturnType(evaluator);
        // if (ctxObj instanceof Map<?, ?>) {
        // return ((Map<?, ?>) ctxObj).get(name);
        // } else {
        BeanProperty bp = BeanInfoHelper.getClassHelper(clz).getBeanProperty(value);
        return bp == null ? Object.class : bp.getType();
        // }
    }

    @Override
    public String toString() {
        return exp.toString() + "." + value;
    }

    public void setPropertyValue(Object bean, Object v) {
        BeanInfoHelper.getClassHelper(bean.getClass()).getBeanProperty(value).setBeanValue(bean, v, true);

    }
}