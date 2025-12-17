package com.foggyframework.core.filter;

public interface FoggyFilter<T, C> extends Comparable<FoggyFilter> {

    int MAX_ORDER = Integer.MAX_VALUE;

    int MIN_ORDER = Integer.MIN_VALUE;

    @Override
    default int compareTo(FoggyFilter o) {

        return Integer.compare(o.order(), this.order()
        );
    }

    /**
     * 越大越靠前！
     *
     * @return
     */
    default int order() {
        return 0;
    }

    void doFilter(T ctx, C chain);

}
