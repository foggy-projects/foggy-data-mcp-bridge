package com.foggyframework.fsscript.client.proxy;

import com.foggyframework.core.utils.JsonUtils;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class JsonFsscriptReturnConverter<T> implements FsscriptReturnConverter {
    Class<T> beanClazz;


    public Object convert(Object result) {
        if (result == null) {
            return null;
        }
        if (result.getClass() == beanClazz) {
            return result;
        }
        if(beanClazz==Object.class){
            return result;
        }
        return JsonUtils.fromJson(JsonUtils.toBytes(result), beanClazz);
    }
}
