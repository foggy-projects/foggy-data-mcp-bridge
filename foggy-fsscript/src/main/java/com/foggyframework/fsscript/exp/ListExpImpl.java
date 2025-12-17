package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ListExp;

import java.util.ArrayList;
import java.util.List;

public class ListExpImpl extends AbstractExp<List<Object>> implements  Exp {
//    private static final XX xx = new XX();

    public ListExpImpl() {
        super(new ArrayList<Object>());
    }

    public void add(Object exp) {

        if (value.contains(exp)) {
            throw new RuntimeException("出现重复对象 : " + exp);
        }
        value.add(exp);
    }

    @Override
    public List<?> evalValue(ExpEvaluator evaluator)
            {
        List<Object> xx = new ArrayList<Object>(value.size());
        for (Object e : value) {
            if (e instanceof Exp) {
                Object o = ((Exp) e).evalValue(evaluator);
                if (o == null) {
                } else {
                    xx.add(o);
                }

            } else {
                xx.add(e);
            }
        }
        return xx;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return List.class;
    }

    public void remove(Exp exp) {
        value.remove(exp);
    }

//    public void sort() {
//        Collections.sort(value, xx);
//    }
//
//    public void sort(Comparator c) {
//        Collections.sort(value, c);
//    }
}
