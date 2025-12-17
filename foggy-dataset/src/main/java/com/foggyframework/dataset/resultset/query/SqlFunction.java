package com.foggyframework.dataset.resultset.query;


import java.util.function.Function;

public interface SqlFunction extends Function<Object[],Object> {

    enum FunType {
        COMMON, AGG
    }

    FunType getFunType();
}
