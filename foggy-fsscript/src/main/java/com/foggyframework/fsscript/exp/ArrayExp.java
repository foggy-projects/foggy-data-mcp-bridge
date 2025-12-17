package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ListExp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ArrayExp implements Exp, Serializable {
    /**
     *
     */
    private static final long serialVersionUID = -4959453353511013593L;
    final ListExp listExp;

    public ArrayExp(ListExp listExp) {
        this.listExp = listExp;
    }

    /**
     * 2016-10-27，我们需要一个类似于js的数组。。。
     */
    @Override
    public Object evalValue(ExpEvaluator ee) {
        return listExp.applyList(ee);
    }

    @Override
    public Object apply2List(List ll, ExpEvaluator ee) {
        return listExp.applyList(ll, ee);
    }

    @Override
    public Class getReturnType(ExpEvaluator ee) {
        return ArrayList.class;
    }

    @Override
    public String toString() {
        return "ARRAY";
    }
}
