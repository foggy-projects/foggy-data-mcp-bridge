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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    Map<String, String> name2Path = new ConcurrentHashMap<>();

    public BundleImpl(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public Fsscript loadFsscript(String name, FsscriptLoader loader, boolean errorIfNotFound) {
        String path = name2Path.get(name);
        if (path != null) {
            Fsscript cachedFsscript = findCachedFsscript(name, path, loader);
            if (cachedFsscript != null) {
                return cachedFsscript;
            }
        }

        return loadFromBundleResource(name, loader, errorIfNotFound);
    }

    private Fsscript findCachedFsscript(String scriptName, String path, FsscriptLoader loader) {
        try {
            Fsscript fsscript = loader.findLoadFsscript(path);
            if (fsscript != null) {
                return fsscript;
            }
            log.debug("已缓存FSScript路径未命中加载器，准备重新查找资源: bundle={}, script={}, path={}",
                    name, scriptName, path);
        } catch (RuntimeException e) {
            log.warn("加载已缓存FSScript路径失败，准备清理路径缓存并重新查找资源: bundle={}, script={}, path={}",
                    name, scriptName, path, e);
        }
        name2Path.remove(scriptName);
        return null;
    }

    private Fsscript loadFromBundleResource(String scriptName, FsscriptLoader loader, boolean errorIfNotFound) {
        BundleResource bundleResource = findBundleResource(scriptName, errorIfNotFound);
        if (bundleResource != null) {
            String path = ResourceFsscriptClosureDefinitionSpace.getResourcePath(bundleResource.getResource());
            Fsscript fsscript;
            try {
                fsscript = loader.findLoadFsscript(bundleResource.getResource().getURL());
            } catch (IOException e) {
                throw new RuntimeException("加载FSScript资源URL失败: bundle="
                        + name + ", script=" + scriptName + ", path=" + path, e);
            }
            if (fsscript != null) {
                name2Path.put(scriptName, path);
                return fsscript;
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
                        log.debug("读取资源URL失败: {}", resource.getResource(), e);
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
                    log.debug("读取资源URL失败: {}", ress[0], e);
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
