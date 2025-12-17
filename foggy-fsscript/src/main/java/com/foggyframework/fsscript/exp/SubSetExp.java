package com.foggyframework.fsscript.exp;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;

public class SubSetExp extends AbstractExp<Exp> {
    public SubSetExp(Exp value) {
        super(value);
    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    SubExp exp;

    public SubSetExp(SubExp exp, Exp rightValue) {
        super(rightValue);
        this.exp = exp;
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {
        Object sub = exp.sub.evalResult(ee);
        if (sub == null) {
            return null;
        }
        Object leftValue = exp.value.evalResult(ee);
        if (leftValue == null) {
            return null;
        }
        Object rightValue = value.evalResult(ee);
        if (sub instanceof Integer) {
            // 期望是数组,或者 List
            if (leftValue.getClass().isArray()) {
                ((Object[]) leftValue)[((Integer) sub)] = rightValue;
            } else if (leftValue instanceof List) {
                ((List) leftValue).set(((Integer) sub), rightValue);
            } else {
                throw RX.throwB("不支持的类型:" + leftValue);
            }
        } else {
//            throw new UnsupportedOperationException();
            BeanInfoHelper.setObjectProperty(leftValue, (String) sub, rightValue);
        }

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
