package com.foggyframework.core.filter;

public interface FoggyExtFilterChain<T> extends FoggyFilterChain<T>{

    T beforeDoFilter(T ctx);

    T afterDoFilter(T ctx);

}
