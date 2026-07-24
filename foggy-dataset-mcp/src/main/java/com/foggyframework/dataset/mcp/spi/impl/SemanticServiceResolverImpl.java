package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.LegacySemanticModelCatalogReadAdapter;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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
    private final SemanticModelCatalogReadPort modelCatalogReadPort;

    @Autowired
    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            SemanticModelCatalogService semanticModelCatalogService) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.modelCatalogReadPort = semanticModelCatalogService;
    }

    /**
     * Compatibility constructor retained for callers compiled against the old
     * Spring wiring shape.
     */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader,
            SemanticModelCatalogService semanticModelCatalogService) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.modelCatalogReadPort = semanticModelCatalogService != null
                ? semanticModelCatalogService
                : new LegacySemanticModelCatalogReadAdapter(
                        systemBundlesContext, queryModelLoader);
    }

    /** Compatibility constructor for non-Spring callers. */
    @Deprecated(since = "9.3.5", forRemoval = false)
    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            SystemBundlesContext systemBundlesContext,
            QueryModelLoader queryModelLoader) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.modelCatalogReadPort = new LegacySemanticModelCatalogReadAdapter(
                systemBundlesContext, queryModelLoader);
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
        return modelCatalogReadPort.getAllModelNames(canonicalNamespace);
    }

    @Override
    public void invalidateModelCache() {
        // Compatibility no-op. Model/source mutations publish through the
        // shared lifecycle authority; MCP no longer owns a second names cache.
        log.debug("Ignoring legacy MCP model-cache invalidation; shared catalog authority is current");
    }

}
