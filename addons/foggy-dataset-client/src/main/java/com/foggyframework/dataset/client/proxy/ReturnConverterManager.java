package com.foggyframework.dataset.client.proxy;

import com.foggyframework.dataset.client.proxy.converter.ReturnConverter;

import java.lang.reflect.Method;

public interface ReturnConverterManager {
    ReturnConverter getReturnConverter(Method clazz);
}
