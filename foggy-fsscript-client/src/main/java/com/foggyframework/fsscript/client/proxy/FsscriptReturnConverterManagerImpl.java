package com.foggyframework.fsscript.client.proxy;

import java.util.HashMap;
import java.util.Map;

public class FsscriptReturnConverterManagerImpl implements FsscriptReturnConverterManager {

    Map<Class<?>, FsscriptReturnConverter> class2ReturnConverter = new HashMap<>();

//    FsscriptReturnConverter fsscriptReturnConverter;

    @Override
    public FsscriptReturnConverter getReturnConverter(Class<?> returnType) {
        FsscriptReturnConverter c = class2ReturnConverter.get(returnType);
        if (c == null) {
            return new JsonFsscriptReturnConverter<>(returnType);
        }

        return c;
    }

}
