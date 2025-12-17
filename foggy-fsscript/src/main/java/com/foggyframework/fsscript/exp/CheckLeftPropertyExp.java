package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.beanhelper.BeanInfoHelper;
import com.foggyframework.core.utils.beanhelper.BeanProperty;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.PropertyHolder;

public class CheckLeftPropertyExp extends PropertyExp {
    @Override
    protected boolean checkLeft() {
        return true;
    }
    public CheckLeftPropertyExp(Exp exp, String name) {
        super(exp, name);
    }
}