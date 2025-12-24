package com.foggyframework.fsscript.client.proxy;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.FoggyBeanUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.fsscript.client.annotates.FsscriptClientMethod;
import com.foggyframework.fsscript.client.annotates.FsscriptClientType;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileTxtFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FsscriptClient 代理类
 * 通过 JDK 动态代理实现接口方法的动态拦截和脚本执行
 */
@Getter
public class FsscriptClientProxy implements InvocationHandler {

    FsscriptReturnConverterManager fsscriptReturnConverterManager;

    SystemBundlesContext systemBundlesContext;


    FileTxtFsscriptLoader fileTxtFsscriptLoader;

    FileFsscriptLoader fileFsscriptLoader;

    Class type;

    FsscriptClientProxy() {

    }

    public FsscriptClientProxy(SystemBundlesContext systemBundlesContext, Class type) {
        Assert.notNull(systemBundlesContext, "systemBundlesContext不得为空！");
        Assert.notNull(type, "type不得为空！");

        this.systemBundlesContext = systemBundlesContext;
        this.type = type;

        fileTxtFsscriptLoader = systemBundlesContext.getApplicationContext().getBean(FileTxtFsscriptLoader.class);
        fileFsscriptLoader = systemBundlesContext.getApplicationContext().getBean(FileFsscriptLoader.class);
        fsscriptReturnConverterManager = systemBundlesContext.getApplicationContext().getBean(FsscriptReturnConverterManager.class);
    }

    private static final int JDBC = 1;
    private static final int MONGO = 2;
    private static final int KPI = 3;

    private final Map<String, FsscriptFunction> cached = new ConcurrentHashMap();


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 Object 类的基本方法
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String name = null;
        String functionName = null;
        FsscriptClientMethod fsscriptClient = method.getAnnotation(FsscriptClientMethod.class);
        boolean cacheScript = false;

        FsscriptClientType fileType = FsscriptClientType.AUTO_TYPE;
        if (fsscriptClient != null) {
            name = fsscriptClient.name();
            fileType = fsscriptClient.fsscriptType();
            functionName = fsscriptClient.functionName();
            cacheScript = fsscriptClient.cacheScript();
        }

        if (StringUtils.isEmpty(name)) {
            name = method.getName();
        }

        if (FsscriptClientType.AUTO_TYPE == fileType) {
            if (name.endsWith(".fsscript")) {
                fileType = FsscriptClientType.EL_TYPE;
            } else if (name.endsWith(".ftxt")) {
                fileType = FsscriptClientType.FTXT_TYPE;
            } else {
                fileType = FsscriptClientType.EL_TYPE;
            }
        }

        if (name.indexOf(".") < 0) {
            switch (fileType) {
                case EL_TYPE:
                    name = name + ".fsscript";
                    break;
                case FTXT_TYPE:
                    name = name + ".ftxt";
                    break;
            }
        }

        //呃，事实上，应当根据每个方法的定义（返回值，DataSetQuery注释，方法参数）创建一个方案
        FsscriptReturnConverter returnCover = fsscriptReturnConverterManager.getReturnConverter(method.getReturnType());

        Bundle bundle = systemBundlesContext.getBundleByClassName(type.getName(), true);

        BundleResource bundleResource = bundle.findBundleResource(name, true);

        Fsscript fsscript = null;
        FsscriptFunction fsscriptFunction = null;
        switch (fileType) {
            case EL_TYPE:
                fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource);
                break;
            case FTXT_TYPE:
                fsscript = fileTxtFsscriptLoader.findLoadFsscript(bundleResource);
                break;
        }
        ExpEvaluator expEvaluator = fsscript.newInstance(systemBundlesContext.getApplicationContext());

        String[] methodArgs = FoggyBeanUtils.getParameterNames(method);
        for (int i = 0; i < methodArgs.length; i++) {
            expEvaluator.setVar(methodArgs[i], args[i]);
        }

        if (cacheScript) {
            if (StringUtils.isEmpty(functionName)) {
                throw RX.throwB("开启cacheScript时，需要指定functionName");
            }
            if (cached.containsKey(fsscript.getPath())) {
                fsscriptFunction = cached.get(fsscript.getPath());
            } else {
                synchronized (cached) {
                    if (cached.containsKey(fsscript.getPath())) {
                        fsscriptFunction = cached.get(fsscript.getPath());
                    }else{
                        //当用来缓存时，我们需要新的ExpEvaluator
                        ExpEvaluator cachExpEvaluator = fsscript.newInstance(systemBundlesContext.getApplicationContext());
                        for (int i = 0; i < methodArgs.length; i++) {
                            cachExpEvaluator.setVar(methodArgs[i], args[i]);
                        }

                        fsscript.evalResult(cachExpEvaluator);
                        fsscriptFunction = getFsscriptFunction(cachExpEvaluator, name, functionName);
                        cached.put(fsscript.getPath(), fsscriptFunction);

                    }
                }
            }
        }

        Object result = null;
        if (StringUtils.isNotEmpty(functionName)) {

            if (fsscriptFunction == null) {
                fsscript.evalResult(expEvaluator);
                fsscriptFunction = getFsscriptFunction(expEvaluator, name, functionName);
            }
            expEvaluator.setVar(ExpEvaluator._argumentsKey, args);

//            synchronized (fsscriptFunction) {
            result = fsscriptFunction.autoApply(expEvaluator);
//            }

        } else {
            result = fsscript.evalResult(expEvaluator);
        }

        return returnCover.convert(result);
    }

    private FsscriptFunction getFsscriptFunction(ExpEvaluator expEvaluator, String name, String functionName) {
        Object f = expEvaluator.getExportObject(functionName);
        if (f instanceof FsscriptFunction) {

            return (FsscriptFunction) f;
        } else {
            throw RX.throwB("期望在【" + name + "】中找到导出的函数【" + functionName + "】,但没有");
        }
    }

}
