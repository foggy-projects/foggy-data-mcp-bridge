package com.foggyframework.fsscript.loadder;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.support.FsscriptImpl;
import lombok.Getter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URL;

@Getter
public abstract class AbstractFileFsscriptLoader extends FsscriptLoader {

    private ApplicationContext appCtx;

    private static final Object KEY = new Object();

    private FsscriptFileChangeHandler changeHandler;

    public AbstractFileFsscriptLoader(ApplicationContext appCtx, FsscriptLoader parent, FsscriptFileChangeHandler changeHandler) {
        super(parent);
        this.appCtx = appCtx;
        this.changeHandler = changeHandler;
    }

    @Override
    public Fsscript findLoadFsscript(String fscriptPath) {
        Resource res = appCtx.getResource(fscriptPath);
        if (!res.exists()) {
            throw RX.throwB(String.format("Resource[%s]不存在", res));
        }
        return findLoadFsscript(res);
    }

    @Override
    public Fsscript findLoadFsscript(URL fscriptPath) {
        Resource res = appCtx.getResource(fscriptPath.toString());

        return findLoadFsscript(res);
    }

    public Fsscript findLoadFsscript(Resource resource) {
        Bundle bundle = appCtx.getBean(SystemBundlesContext.class).getBundleByResource(resource);
        return findLoadFsscript(new BundleResource(bundle, resource), true);
    }
    public Fsscript findLoadFsscript(Resource resource, ExpFactory expFactory) {
        Bundle bundle = appCtx.getBean(SystemBundlesContext.class).getBundleByResource(resource);
        return findLoadFsscript(new BundleResource(bundle, resource), expFactory,true);
    }
//    ,ee.getExpFactory(
    public Fsscript findLoadFsscript(Resource resource, ExpFactory expFactory, boolean errorIfNull) {
        Bundle bundle = appCtx.getBean(SystemBundlesContext.class).getBundleByResource(resource);
        return findLoadFsscript(new BundleResource(bundle, resource),expFactory, errorIfNull);
    }
    public Fsscript findLoadFsscript(Resource resource,  boolean errorIfNull) {
        Bundle bundle = appCtx.getBean(SystemBundlesContext.class).getBundleByResource(resource);
        return findLoadFsscript(new BundleResource(bundle, resource),null, errorIfNull);
    }
    protected abstract Exp compile(FsscriptClosureDefinition d, String str,ExpFactory expFactory);

    public Fsscript findLoadFsscript(BundleResource fscriptResource) {
        return findLoadFsscript(fscriptResource, true);
    }

    public Fsscript findLoadFsscript(BundleResource fscriptResource, ExpFactory expFactor) {
        return findLoadFsscript(fscriptResource, expFactor,true);
    }

    public Fsscript findLoadFsscript(BundleResource fscriptResource, boolean errorIfNull) {
        return findLoadFsscript(fscriptResource,null,errorIfNull);
    }
    public Fsscript findLoadFsscript(BundleResource fscriptResource , ExpFactory expFactory, boolean errorIfNull) {
        //生成path
        String path = ResourceFsscriptClosureDefinitionSpace.getResourcePath(fscriptResource.getResource());
        Fsscript fScript = parentLoader.findLoadFsscript(path);
        if (fScript != null) {
            //已经有缓存了
            return fScript;
        }

        //开始加载
        //创建
        synchronized (KEY) {
            ResourceFsscriptClosureDefinitionSpace space = new ResourceFsscriptClosureDefinitionSpace(fscriptResource);
            FsscriptClosureDefinition d = space.newFsscriptClosureDefinition();
            String str = FileUtils.toString(fscriptResource.getInputStream());

            Exp exp = compile(d, str,expFactory);
            if (errorIfNull) {
                Assert.notNull(exp, "编辑" + fscriptResource + "返回空？？");
            }
            FsscriptImpl fScriptImpl = new FsscriptImpl(d, exp);
            parentLoader.setFsscript(path, fScriptImpl);

            if (changeHandler != null) {
                //加入文件变化跟踪，在变化，或删除时，清空缓存
                if (fscriptResource.isFile()) {
                    changeHandler.addFile(fscriptResource.getFile());
                }
            }
            return fScriptImpl;
        }


    }


}
