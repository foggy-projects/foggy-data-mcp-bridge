package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.file.DirectoryChangeListener;
import com.foggyframework.core.utils.file.WatchServiceFileTracer;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 语义服务解析器实现
 *
 * <p>统一调用 V3 版本的语义服务。
 *
 * <p>支持动态模型发现：
 * <ul>
 *   <li>缓存模型名称列表，避免每次扫描</li>
 *   <li>监听 bundle 目录，新 QM 文件创建时自动失效缓存</li>
 *   <li>只监听真实文件系统目录，JAR 包内资源不监听</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Component
public class SemanticServiceResolverImpl implements SemanticServiceResolver, DirectoryChangeListener,
        ApplicationListener<FsscriptRemoveEvent> {

    private final SemanticServiceV3 semanticServiceV3;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final SystemBundlesContext systemBundlesContext;
    private final QueryModelLoader queryModelLoader;

    /**
     * 缓存的模型名称列表（volatile 保证可见性）
     */
    private volatile List<String> cachedModelNames;

    /**
     * QM 文件扩展名
     */
    private static final Set<String> QM_EXTENSIONS = Set.of(".qm");

    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.systemBundlesContext = systemBundlesContext;
        this.queryModelLoader = queryModelLoader;
    }

    @PostConstruct
    public void init() {
        log.info("SemanticServiceResolver initialized (V3)");
        // 启动目录监听
        setupDirectoryWatching();
    }

    /**
     * 设置 bundle 目录监听
     *
     * <p>只监听真实文件系统目录（非 JAR 包内部资源）
     */
    private void setupDirectoryWatching() {
        WatchServiceFileTracer tracer = WatchServiceFileTracer.getInstance();
        if (!tracer.isAvailable()) {
            log.warn("WatchService 不可用，无法监听目录变化，新增 QM 文件需要重启服务才能生效");
            return;
        }

        int watchedCount = 0;
        for (Bundle bundle : systemBundlesContext.getBundleList()) {
            try {
                // 尝试获取 bundle 根目录对应的真实文件系统路径
                File bundleDir = getBundleDirectory(bundle);
                if (bundleDir != null && bundleDir.isDirectory()) {
                    boolean success = tracer.watchDirectory(bundleDir, QM_EXTENSIONS, this);
                    if (success) {
                        watchedCount++;
                        log.debug("已监听 bundle 目录: {} -> {}", bundle.getName(), bundleDir);
                    }
                } else {
                    log.debug("Bundle {} 不是文件系统目录，跳过监听", bundle.getName());
                }
            } catch (Exception e) {
                log.debug("无法监听 bundle {} 的目录: {}", bundle.getName(), e.getMessage());
            }
        }

        if (watchedCount > 0) {
            log.info("已启动 {} 个 bundle 目录的 QM 文件监听", watchedCount);
        } else {
            log.info("没有可监听的 bundle 目录（可能都在 JAR 包中）");
        }
    }

    /**
     * 获取 bundle 的真实文件系统目录
     *
     * @param bundle Bundle
     * @return 文件系统目录，如果不是真实目录则返回 null
     */
    private File getBundleDirectory(Bundle bundle) {
        try {
            // 尝试通过查找任意资源来获取目录
            Resource[] resources = bundle.findResources("**/*.qm");
            if (resources != null && resources.length > 0) {
                Resource firstResource = resources[0];
                // 只有 file: 协议的资源才是真实文件系统
                if (firstResource.isFile()) {
                    File file = firstResource.getFile();
                    // 返回 bundle 的根目录（向上找到 foggy/templates 目录）
                    return findBundleRootDirectory(file);
                }
            }
        } catch (Exception e) {
            // 忽略 - JAR 内资源会抛异常
            log.trace("获取 bundle {} 目录失败: {}", bundle.getName(), e.getMessage());
        }
        return null;
    }

    /**
     * 从 QM 文件路径向上查找 bundle 根目录
     */
    private File findBundleRootDirectory(File qmFile) {
        File parent = qmFile.getParentFile();
        // 向上查找，直到找到 "templates" 目录或到达根目录
        while (parent != null) {
            if ("templates".equals(parent.getName())) {
                return parent;
            }
            parent = parent.getParentFile();
        }
        // 如果没找到 templates，返回 QM 文件的父目录
        return qmFile.getParentFile();
    }

    // ==================== DirectoryChangeListener 实现 ====================

    @Override
    public void onFileCreated(File file) {
        log.info("检测到新 QM 文件: {}", file.getName());
        invalidateModelCache();
    }

    @Override
    public void onFileModified(File file) {
        log.debug("检测到 QM 文件修改: {}", file.getName());
        // 文件修改由 FsscriptRemoveEvent 处理，这里不需要额外操作
    }

    @Override
    public void onFileDeleted(File file) {
        log.debug("检测到 QM 文件删除: {}", file.getName());
        // 文件删除由 FsscriptRemoveEvent 处理，这里不需要额外操作
    }

    // ==================== ApplicationListener 实现 ====================

    @Override
    public void onApplicationEvent(FsscriptRemoveEvent event) {
        // 检查是否有 QM 文件变化
        List<Fsscript> removedFsscripts = event.getRemovedFsscripts();
        if (removedFsscripts == null || removedFsscripts.isEmpty()) {
            return;
        }

        boolean hasQmChange = removedFsscripts.stream()
                .filter(fs -> fs != null && fs.getPath() != null)
                .anyMatch(fs -> fs.getPath().endsWith(".qm"));

        if (hasQmChange) {
            log.info("检测到 QM 文件变化（通过 FsscriptRemoveEvent），清除模型缓存");
            invalidateModelCache();
        }
    }

    // ==================== SemanticServiceResolver 实现 ====================

    @Override
    public SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format,
                                                SemanticRequestContext context) {
        log.debug("Using SemanticServiceV3 for metadata generation, namespace={}", context.getNamespace());
        return semanticServiceV3.getMetadata(request, format, context);
    }

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                            SemanticRequestContext context) {
        log.debug("Using SemanticQueryServiceV3 for query execution, namespace={}", context.getNamespace());
        return semanticQueryServiceV3.queryModel(model, request, mode, context);
    }

    @Override
    public List<String> getAllModelNames() {
        // 快速路径：缓存命中
        List<String> cached = cachedModelNames;
        if (cached != null) {
            log.debug("返回缓存的模型列表: {} 个模型", cached.size());
            return cached;
        }

        // 慢路径：需要扫描（double-check locking）
        synchronized (this) {
            if (cachedModelNames != null) {
                return cachedModelNames;
            }
            cachedModelNames = scanAllModelNames();
            log.info("扫描并缓存模型列表: {} 个模型: {}", cachedModelNames.size(), cachedModelNames);
            return cachedModelNames;
        }
    }

    @Override
    public void invalidateModelCache() {
        cachedModelNames = null;
        log.info("模型名称缓存已清除");
    }

    /**
     * 扫描所有 bundle 中的 QM 文件
     */
    private List<String> scanAllModelNames() {
        java.util.Set<String> modelNames = new java.util.LinkedHashSet<>();
        List<BundleResource> qmFiles = new ArrayList<>();

        try {
            // 从所有 bundle 中查找 .qm 文件
            systemBundlesContext.getBundleList().forEach(bundle -> {
                try {
                    BundleResource[] resources = bundle.findBundleResources("**/*.qm");
                    if (resources != null) {
                        qmFiles.addAll(java.util.Arrays.asList(resources));
                    }
                } catch (Exception e) {
                    log.warn("从 bundle {} 查找 QM 文件时出错: {}", bundle.getName(), e.getMessage());
                }
            });

            // 加载每个 QM 文件并获取模型名称
            for (BundleResource qmFile : qmFiles) {
                try {
                    QueryModel qm = queryModelLoader.loadJdbcQueryModel(qmFile);
                    if (qm != null && qm.getName() != null) {
                        modelNames.add(qm.getName());
                    }
                } catch (Exception e) {
                    log.debug("加载 QM 文件时出错: {}, error: {}", qmFile.getResource().getDescription(), e.getMessage());
                }
            }

            log.debug("扫描发现 {} 个可用模型: {}", modelNames.size(), modelNames);

        } catch (Exception e) {
            log.warn("查找 QM 文件时出错: {}", e.getMessage());
        }

        return List.copyOf(modelNames);
    }
}
