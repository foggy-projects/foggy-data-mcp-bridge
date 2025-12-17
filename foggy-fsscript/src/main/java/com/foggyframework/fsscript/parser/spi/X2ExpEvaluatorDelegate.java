package com.foggyframework.fsscript.parser.spi;


import com.foggyframework.fsscript.DefaultExpEvaluator;
import lombok.experimental.Delegate;

import java.util.Stack;

/**
 * 未完成
 */
public class X2ExpEvaluatorDelegate extends ExpEvaluatorDelegate{
    @Override
    public ExpEvaluator clone() {
        ExpEvaluator c = delegate.clone();

        return new X2ExpEvaluatorDelegate(c);
    }

    private DefaultExpEvaluator expEvaluator;

    public X2ExpEvaluatorDelegate() {
    }

    public X2ExpEvaluatorDelegate(ExpEvaluator delegate) {
        super(delegate);
//        expEvaluator
    }


}
