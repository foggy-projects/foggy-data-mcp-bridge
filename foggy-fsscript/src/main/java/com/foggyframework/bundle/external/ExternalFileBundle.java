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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部文件系统Bundle实现
 *
 * <p>支持从外部文件系统目录加载数据模型文件（.tm, .qm, .fsscript等）。
 * 与 {@link com.foggyframework.bundle.BundleImpl} 不同，此实现直接操作文件系统，
 * 而不是通过Spring的Resource抽象访问classpath资源。
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
    private Map<String, String> name2Path = new HashMap<>();

    private final Object KEY = new Object();

    public ExternalFileBundle(SystemBundlesContext systemBundlesContext) {
        this.systemBundlesContext = systemBundlesContext;
    }

    @Override
    public int getMode() {
        return MODE_EXTERNAL;
    }

    @Override
    public void clearCache() {
        synchronized (KEY) {
            name2Path.clear();
        }
    }

    @Override
    public Resource[] findResources(String pattern) {
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
            return path.endsWith(suffix) || path.endsWith("/" + suffix) || path.equals(suffix);
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
                log.error("加载外部Fsscript失败: {}", t.getMessage());
                if (log.isDebugEnabled()) {
                    t.printStackTrace();
                }
                // 发生错误，移除缓存，重新加载
                return loadFsscript(name, loader, errorIfNotFound);
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("ExternalFileBundle{name='%s', basePath='%s'}", name, basePath);
    }
}
