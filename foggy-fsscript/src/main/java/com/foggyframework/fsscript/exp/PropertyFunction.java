package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

public interface PropertyFunction {
    Object invoke(ExpEvaluator evaluator, String methodName, Object[] args);
}
