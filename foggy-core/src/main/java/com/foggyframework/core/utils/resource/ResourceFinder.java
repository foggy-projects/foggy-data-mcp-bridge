package com.foggyframework.core.utils.resource;

import com.foggyframework.core.ex.ExDefined;
import com.foggyframework.core.ex.ExDefinedSupport;
import org.springframework.core.io.Resource;

import jakarta.annotation.Nullable;

/**
 * @author fengjianguang
 */
public interface ResourceFinder {
    ExDefinedSupport MULTI_FIND_ERROR = new ExDefinedSupport(1200, ExDefined.SRC_TYPE_BUSINESS, "期望只有一个{0}，但找到多个: {1}");

    /**
     * 注意，它不会返回空，所以需要自调用exist方法判断资源是否存在！
     *
     * @param resource
     * @param s
     * @return
     */
    Resource findByResource(Resource resource, String s);

    @Nullable
    Resource findOne(String name);
}
