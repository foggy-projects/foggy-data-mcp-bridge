package com.foggyframework.dataset.client.proxy;

import com.foggyframework.dataset.client.proxy.converter.BeanReturnConverter;
import com.foggyframework.dataset.client.proxy.converter.ReturnConverter;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ReturnConverterManagerImpl implements ReturnConverterManager {

    Map<Object, ReturnConverter> class2ReturnConverter = new HashMap<>();

    public void register(Object clazz, ReturnConverter returnConverter) {
        class2ReturnConverter.put(clazz, returnConverter);
    }

    @Override
    public ReturnConverter getReturnConverter(Method method) {
        Type genericReturnType = method.getGenericReturnType();
        ReturnConverter converter = class2ReturnConverter.get(genericReturnType);
        if (converter == null) {
            converter = class2ReturnConverter.get(method.getReturnType());
        }
        if (converter == null) {
            return new BeanReturnConverter<>(method.getReturnType());
        }
        return converter;
    }
}
