package com.foggyframework.bundle;

import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.core.io.Resource;

public interface Bundle {
    String getName();

    int getMode();

    void clearCache();

    Resource[] findResources(String s) ;

    BundleResource[] findBundleResources(String path);

    /**
     * 注意，只会在当前模块中查找
     * @param name
     * @param errorIfNotFound
     * @return
     */
    BundleResource findBundleResource(String name,boolean errorIfNotFound);

    BundleDefinition getDefinition();

    SystemBundlesContext getSystemBundlesContext();

    String getPackageName();

    String getRootPath();

    Fsscript loadFsscript(String name, FsscriptLoader loader, boolean errorIfNotFound);
}
