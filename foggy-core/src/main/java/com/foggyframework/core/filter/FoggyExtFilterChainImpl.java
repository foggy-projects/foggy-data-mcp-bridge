package com.foggyframework.core.filter;

import lombok.Data;
import lombok.experimental.Delegate;

import java.util.List;

public class FoggyExtFilterChainImpl<T, C> implements FoggyExtFilterChain<T> {

    FoggyFilterChainImpl foggyFilterChain;

    public FoggyExtFilterChainImpl(List<FoggyFilter<T, C>> filters) {
        foggyFilterChain = new FoggyFilterChainImpl(filters);
    }

    @Override
    public void doFilter(T ctx) {

        foggyFilterChain.doFilter(ctx);

    }

    @Override
    public T beforeDoFilter(T ctx) {
        T result = ctx;
        for (Object obj : foggyFilterChain.getFilters()) {
            if (obj instanceof FoggyExtFilter) {
                FoggyExtFilter<T, C> filter = (FoggyExtFilter<T, C>) obj;
                result = filter.beforeDoFilter(ctx);
            }
        }
        return result;
    }

    @Override
    public T afterDoFilter(T ctx) {
        T result = ctx;
        for (Object obj : foggyFilterChain.getFilters()) {
            if (obj instanceof FoggyExtFilter) {
                FoggyExtFilter<T, C> filter = (FoggyExtFilter<T, C>) obj;
                result = filter.afterDoFilter(ctx);
            }
        }
        return result;
    }
}
