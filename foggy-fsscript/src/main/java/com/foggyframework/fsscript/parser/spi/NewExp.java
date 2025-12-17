package com.foggyframework.fsscript.parser.spi;

public interface NewExp extends Exp {
    /**
     * 创建实例
     * @param evaluator
     * @param retType
     * @param <T>
     * @return
     */
     <T> T newInstance(ExpEvaluator evaluator, Class<T> retType);
}
