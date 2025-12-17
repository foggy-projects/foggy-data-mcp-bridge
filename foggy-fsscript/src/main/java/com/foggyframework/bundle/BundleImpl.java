package com.foggyframework.bundle;

import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString(exclude = {"basePath", "systemBundlesContext"})
@Slf4j
//@EqualsAndHashCode(exclude = {"basePath", "systemBundlesContext"})
public class BundleImpl implements Bundle {

    public static final int MODE_JAR = 1;
    public static final int MODE_CLASSPATH = 2;

    String name;

    int mode;
    /**
     * foggy/templates的路径
     * 注意，它不会以"/"结束
     */
    String basePath;

    String rootPath;

    SystemBundlesContext systemBundlesContext;

    BundleDefinition bundleDefinition;

    Map<String, String> name2Path = new HashMap<>();

    private final Object KEY = new Object();

    public BundleImpl(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public Fsscript loadFsscript(String name, FsscriptLoader loader, boolean errorIfNotFound) {
        String path = name2Path.get(name);
        if (path == null) {
            BundleResource bundleResource = findBundleResource(name, errorIfNotFound);
            if (bundleResource != null) {
                path = ResourceFsscriptClosureDefinitionSpace.getResourcePath(bundleResource.getResource());
                Fsscript fsscript = null;
                try {
                    fsscript = loader.findLoadFsscript(bundleResource.getResource().getURL());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (fsscript != null) {
                    synchronized (KEY) {
                        name2Path.put(name, path);
                    }
                    return fsscript;
                }
            }
        } else {
            try {
                Fsscript fsscript = loader.findLoadFsscript(path);
                return fsscript;
            } catch (Throwable t) {
                synchronized (KEY) {
                    name2Path.remove(name);
                }
                log.error(t.getMessage());
                t.printStackTrace();
                //发生了错误？移除旧的缓存，重新加载下
                return loadFsscript(name, loader, errorIfNotFound);
            }
        }

        return null;
    }

    @Override
    public void clearCache() {
        name2Path.clear();
    }


    @Override
    public Resource[] findResources(String path) {
        try {

            Resource[] ress = systemBundlesContext.getApplicationContext().getResources(basePath + "/" + path);
            if (log.isDebugEnabled()) {
                if (ress != null) {
                    for (Resource resource : ress) {
                        log.debug("找到资源(findResources): " + resource.getURL());
                    }
                } else {
                    log.debug("找到资源(findResources): 返回空？");
                }

            }


            return ress;
        } catch (FileNotFoundException e) {
//            throw RX.throwB(e);
            log.error(e.getMessage());
            return new Resource[0];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BundleResource[] findBundleResources(String path) {
        BundleResource[] ress = Arrays.stream(findResources(path))
                .map(res -> new BundleResource(BundleImpl.this, res)).toArray(BundleResource[]::new);
        if (log.isDebugEnabled()) {
            if (ress != null) {
                for (BundleResource resource : ress) {
                    try {
                        log.debug("找到资源(findBundleResources): " + resource.getResource().getURL());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                log.debug("找到资源(findBundleResources): 返回空？");
            }
        }
        return ress;
    }

    @Override
    public BundleResource findBundleResource(String name, boolean errorIfNotFound) {
        Resource[] ress = findResources("**/" + name);
        if (ress.length == 1) {
            if (log.isDebugEnabled()) {
                try {
                    log.debug("找到资源(findBundleResources): " + ress[0].getURL());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return new BundleResource(this, ress[0]);
        }
        if (ress.length == 0) {
            if (errorIfNotFound) {
                throw RX.RESOURCE_NOT_FOUND.throwErrorWithFormatArgs(name);
            }
            return null;
        }
        throw RX.throwB("找到多个" + name);

    }

    @Override
    public BundleDefinition getDefinition() {
        return bundleDefinition;
    }

    @Override
    public String getPackageName() {
        return bundleDefinition.getPackageName();
    }


}
