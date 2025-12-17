package com.foggyframework.core.filter;

public interface FoggyExtFilter<T,C> extends FoggyFilter<T, C> {

    T beforeDoFilter(T ctx);

    T afterDoFilter(T ctx);

}
