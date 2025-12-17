package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

public abstract class FsscriptLoader {
    FsscriptLoader parentLoader;

    public FsscriptLoader(FsscriptLoader parentLoader) {
        this.parentLoader = parentLoader;
    }

    public abstract Fsscript findLoadFsscript(String path);

    public abstract ApplicationContext getAppCtx();

    public Fsscript setFsscript(String path, Fsscript fscript) {
        return parentLoader.setFsscript(path, fscript);
    }

    public Object evalResult(ExpEvaluator ee, String path, Map<String, Object> args) throws IOException {
        if(ee.getCurrentFsscriptClosure()==null||ee.getCurrentFsscriptClosure().getBeanDefinitionSpace()==null){
            return findLoadFsscript(path).evalResult(ee.getApplicationContext(), args);
        }
        Resource res= ee.getCurrentFsscriptClosure().getBeanDefinitionSpace().getResource(ee,path);

        return findLoadFsscript(res.getURL()).evalResult(ee.getApplicationContext(),args);
    }


    public abstract Fsscript findLoadFsscript(URL fscriptPath);
}
