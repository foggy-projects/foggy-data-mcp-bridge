package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.SubHolder;

import java.util.List;
import java.util.Map;

public class SubExp extends AbstractExp<Exp> {
    /**
     *
     */
    private static final long serialVersionUID = 5192572191074376898L;
    Exp sub;

    public SubExp(final Exp value, Exp sub) {
        super(value);
        this.sub = sub;
    }

    @Override
    public Object evalValue(final ExpEvaluator evaluator)
            {
        Object v = value.evalResult(evaluator);
        if (v == null) {
            return null;
        }
        Object s = sub.evalResult(evaluator);
        if (s == null) {
            return null;
        }

        if (v.getClass().isArray() && s instanceof Number) {
            return ((Object[]) v)[((Number) s).intValue()];
        }
        if (v instanceof List && s instanceof Number) {
            return ((List<?>) v).get(((Number) s).intValue());
        }
        if (v instanceof SubHolder) {
            if (s instanceof Number) {
                return ((SubHolder) v).getSubObject(((Number) s).intValue());
            } else {
                return ((SubHolder) v).getSubObject(s.toString());
            }

        }
        if (v instanceof Map) {
            return ((Map<?, ?>) v).get(s);
        }

        return BeanInfoHelper.getProperty(v, s.toString());
        // throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        // TODO Auto-generated method stub
        // return null;
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return value.toString() + "[" + sub.toString() + "]";
    }

}