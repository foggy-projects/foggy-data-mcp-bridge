package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public interface FsscriptFunction extends Function<Object[], Object>, Consumer {
    Object threadSafeAccept(Object t);

    Object executeFunction(ExpEvaluator evaluator, Object... args);

    List<Exp> getArgDefs();

    Object autoApply(ExpEvaluator ee);

    /**
     * 非线程安全，若需要线程安全，请使用threadSafeAccept
     * @param t
     */
    default void accept(Object t) {
        apply(new Object[]{t});
    }
}
