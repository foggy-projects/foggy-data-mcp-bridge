package com.foggyframework.core.filter;

public interface FoggyFilterChain<T> {
    void doFilter(T ctx) ;
}
