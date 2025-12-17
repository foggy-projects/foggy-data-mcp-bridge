package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.VarDef;

public class DeleteExp extends AbstractExp<Exp> {
    public DeleteExp(Exp e) {
        super(e);
    }

    @Override
    public Object evalValue(ExpEvaluator ee) {

        if (value instanceof IdExp) {
            VarDef varDef = ee.getVarDef(((IdExp) value).getValue());
            if (varDef != null) {
                varDef.setValue(null);
            }
        } else if (value instanceof PropertyExp) {
            PropertyExp exp = (PropertyExp) value;
            Object obj = exp.exp.evalResult(ee);
            if (obj != null) {
                BeanProperty beanProperty = BeanInfoHelper.getClassHelper(obj.getClass()).getBeanProperty(exp.value, true);
                beanProperty.setBeanValue(obj, null);
            }
        }
        return null;
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return Object.class;
    }
}
