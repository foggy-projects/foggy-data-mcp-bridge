package com.foggyframework.core.filter;

import lombok.Getter;
import org.springframework.util.Assert;

import java.util.List;

@Getter
public class FoggyFilterChainImpl<T, C> implements FoggyFilterChain<T> {
    List<FoggyFilter<T, C>> filters;
    int idx = 0;
    FoggyFilter<T, C> last;



    public FoggyFilterChainImpl(List<FoggyFilter<T, C>> filters) {
        this(filters, null);
    }

    public FoggyFilterChainImpl(List<FoggyFilter<T, C>> filters, FoggyFilter<T, C> last) {
        Assert.notNull(filters, "filters不得为空!");
        this.filters = filters;
        this.last = last;
    }

    @Override
    public void doFilter(T ctx) {
        C xx = (C) this;
        if (filters.size() <= idx) {
            //过滤器已经全部执行完毕
            if (last != null) {
                if (idx == -1) {
                    return;
                } else {
                    idx = -1;
                    last.doFilter(ctx, xx);
                }
            }
            return;
        }

        FoggyFilter<T, C> filter = filters.get(idx);
        idx++;


        filter.doFilter(ctx, xx);

    }
}
