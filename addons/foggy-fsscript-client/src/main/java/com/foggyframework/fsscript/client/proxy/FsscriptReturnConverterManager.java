package com.foggyframework.fsscript.client.proxy;

public interface FsscriptReturnConverterManager {
    FsscriptReturnConverter getReturnConverter(Class<?> returnType);
}
