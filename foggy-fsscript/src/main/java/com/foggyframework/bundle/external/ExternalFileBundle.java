package com.foggyframework.bundle.external;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.core.ex.RX;
import com.foggyframework.fsscript.closure.file.ResourceFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部资源Bundle实现
 *
 * <p>支持从外部文件系统目录或 Spring Resource location 加载数据模型文件
 * （.tm, .qm, .fsscript等）。
 *
 * <h3>目录结构约定：</h3>
 * <pre>
 * {basePath}/
 *   ├── model/
 *   │   ├── XxxModel.tm
 *   │   └── YyyModel.tm
 *   ├── query/
 *   │   ├── XxxQueryModel.qm
 *   │   └── YyyQueryModel.qm
 *   └── dicts.fsscript
 * </pre>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Getter
@Setter
@Slf4j
public class ExternalFileBundle implements Bundle {

    public static final int MODE_EXTERNAL = 3;

    /**
     * Bundle名称
     */
    private String name;

    /**
     * 外部目录的根路径
     */
    private String basePath;

    /**
     * 与basePath相同，用于兼容现有代码
     */
    private String rootPath;

    /**
     * Bundle定义
     */
    private ExternalBundleDefinition bundleDefinition;

    /**
     * 系统Bundle上下文
     */
    private SystemBundlesContext systemBundlesContext;

    /**
     * 文件名到路径的缓存
     */
    private Map<String, String> name2Path = new ConcurrentHashMap<>();

    public ExternalFileBundle(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public int getMode() {
        return MODE_EXTERNAL;
    }

    @Override
    public void clearCache() {
        name2Path.clear();
    }

    @Override
    public Resource[] findResources(String pattern) {
        if (ExternalBundleResourceSupport.isSpringResourceLocation(basePath)) {
            try {
                Resource[] resources = ExternalBundleResourceSupport.getResources(
                        ExternalBundleResourceSupport.toPatternLocation(basePath, pattern));
                if (log.isDebugEnabled()) {
                    for (Resource resource : resources) {
                        log.debug("找到外部Resource资源: {}", resource.getURL());
                    }
                }
                return resources;
            } catch (IOException e) {
                log.error("查找外部Resource资源失败: basePath={}, pattern={}", basePath, pattern, e);
                return new Resource[0];
            }
        }

        List<Resource> resources = new ArrayList<>();
        Path baseDir = Paths.get(basePath);

        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            log.warn("外部Bundle目录不存在或不是目录: {}", basePath);
            return new Resource[0];
        }

        try {
            // 使用 Java PathMatcher 进行 glob 匹配
            final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relativePath = baseDir.relativize(file);
                    // 尝试匹配相对路径
                    if (matcher.matches(relativePath) || matchGlob(pattern, relativePath.toString().replace("\\", "/"))) {
                        resources.add(new FileSystemResource(file.toFile()));
                        if (log.isDebugEnabled()) {
                            log.debug("找到外部资源: {}", file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("访问文件失败: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("遍历外部目录失败: {}", basePath, e);
        }

        return resources.toArray(new Resource[0]);
    }

    /**
     * 简单的glob模式匹配
     */
    private boolean matchGlob(String pattern, String path) {
        // 处理 **/*.ext 模式（匹配任意目录下的特定扩展名文件）
        if (pattern.startsWith("**/")) {
            String suffix = pattern.substring(3);
            // 如果 suffix 是 *.ext 格式
            if (suffix.startsWith("*.")) {
                String ext = suffix.substring(1); // .ext
                return path.endsWith(ext);
            }
            // 如果 suffix 是文件名
            return path.equals(suffix) || path.endsWith("/" + suffix);
        }
        // 处理简单的 *.ext 模式
        if (pattern.startsWith("*.")) {
            String ext = pattern.substring(1);
            return path.endsWith(ext);
        }
        // 精确匹配
        return path.equals(pattern) || path.endsWith("/" + pattern);
    }

    @Override
    public BundleResource[] findBundleResources(String path) {
        Resource[] resources = findResources(path);
        BundleResource[] bundleResources = new BundleResource[resources.length];
        for (int i = 0; i < resources.length; i++) {
            bundleResources[i] = new BundleResource(this, resources[i]);
        }
        return bundleResources;
    }

    @Override
    public BundleResource findBundleResource(String name, boolean errorIfNotFound) {
        Resource[] resources = findResources("**/" + name);

        if (resources.length == 1) {
            if (log.isDebugEnabled()) {
                try {
                    log.debug("找到外部资源: {}", resources[0].getURL());
                } catch (IOException e) {
                    log.debug("找到外部资源: {}", resources[0]);
                }
            }
            return new BundleResource(this, resources[0]);
        }

        if (resources.length == 0) {
            if (errorIfNotFound) {
                throw RX.RESOURCE_NOT_FOUND.throwErrorWithFormatArgs(name + " in " + basePath);
            }
            return null;
        }

        throw RX.throwB("在外部目录中找到多个同名文件: " + name);
    }

    @Override
    public BundleDefinition getDefinition() {
        return bundleDefinition;
    }

    @Override
    public String getPackageName() {
        return bundleDefinition.getPackageName();
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
            log.debug("已缓存外部FSScript路径未命中加载器，准备重新查找资源: bundle={}, script={}, path={}",
                    name, scriptName, path);
        } catch (RuntimeException e) {
            log.warn("加载已缓存外部FSScript路径失败，准备清理路径缓存并重新查找资源: bundle={}, script={}, path={}",
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
                throw new RuntimeException("加载外部FSScript资源URL失败: bundle="
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
    public String toString() {
        return String.format("ExternalFileBundle{name='%s', basePath='%s'}", name, basePath);
    }
}
