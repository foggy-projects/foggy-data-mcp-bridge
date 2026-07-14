package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.NamespaceContext;
import com.foggyframework.dataset.db.model.spi.NamespaceScope;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 语义服务解析器实现
 *
 * <p>统一调用 V3 版本的语义服务。
 *
 * <p>模型发现由 model 模块的 namespace catalog authority 提供：
 * <ul>
 *   <li>Spring 运行时直接消费 {@link SemanticModelCatalogService}</li>
 *   <li>旧的非 Spring 构造器保留无缓存扫描兼容路径</li>
 *   <li>目录和 source 变更由核心 lifecycle authority 收敛，不在 MCP 双写失效</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Component
public class SemanticServiceResolverImpl implements SemanticServiceResolver,
        ApplicationListener<FsscriptRemoveEvent> {

    private final SemanticServiceV3 semanticServiceV3;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final SystemBundlesContext systemBundlesContext;
    private final QueryModelLoader queryModelLoader;
    private final SemanticModelCatalogService semanticModelCatalogService;

    @Autowired
    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            SemanticModelCatalogService semanticModelCatalogService) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.systemBundlesContext = systemBundlesContext;
        this.queryModelLoader = queryModelLoader;
        this.semanticModelCatalogService = semanticModelCatalogService;
    }

    /** Compatibility constructor for non-Spring callers. */
    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader) {
        this(semanticServiceV3, semanticQueryServiceV3, systemBundlesContext,
                queryModelLoader, null);
    }

    @PostConstruct
    public void init() {
        log.info("SemanticServiceResolver initialized with shared namespace catalog authority (V3)");
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
            log.debug("检测到 QM 文件变化；共享 lifecycle catalog 负责发布新视图");
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
        return getAllModelNames(NamespaceContext.getNamespace());
    }

    @Override
    public List<String> getAllModelNames(String namespace) {
        String canonicalNamespace = namespace == null ? "" : namespace.trim();
        if (semanticModelCatalogService != null) {
            return semanticModelCatalogService.getAllModelNames(canonicalNamespace);
        }
        try (NamespaceScope ignored = NamespaceContext.open(canonicalNamespace)) {
            return scanAllModelNames();
        }
    }

    @Override
    public void invalidateModelCache() {
        // Compatibility no-op. Model/source mutations publish through the
        // shared lifecycle authority; MCP no longer owns a second names cache.
        log.debug("Ignoring legacy MCP model-cache invalidation; shared catalog authority is current");
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
