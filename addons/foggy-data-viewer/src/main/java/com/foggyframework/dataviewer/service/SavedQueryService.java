package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.QueryVisibility;
import com.foggyframework.dataviewer.domain.SavedQueryDef;
import com.foggyframework.dataviewer.repository.SavedQueryRepository;
import com.foggyframework.dataset.db.model.spi.SecurityIdentityResolver;
import com.foggyframework.dataset.db.model.spi.SecurityIdentityResolver.ResolvedIdentity;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 保存查询服务
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SavedQueryService {

    @Autowired(required = false)
    private SecurityIdentityResolver identityResolver;

    @Autowired
    private SavedQueryRepository savedQueryRepository;

    @Autowired
    private QueryCacheService queryCacheService;

    /**
     * 检查 SPI 是否可用
     */
    public boolean isAvailable() {
        return identityResolver != null;
    }

    /**
     * 保存查询
     */
    public SavedQueryDef save(SaveQueryRequest request, String authorization) {
        ResolvedIdentity identity = identityResolver.resolve(authorization);

        SavedQueryDef def = SavedQueryDef.builder()
                .id(UUID.randomUUID().toString())
                .model(request.getModel())
                .title(request.getTitle())
                .description(request.getDescription())
                .columns(request.getColumns())
                .slice(request.getSlice())
                .orderBy(request.getOrderBy())
                .groupBy(request.getGroupBy())
                .calculatedFields(request.getCalculatedFields())
                .ownerId(identity.userId())
                .ownerDeptId(identity.deptId())
                .ownerTenantId(identity.tenantId())
                .visibility(request.getVisibility() != null ? request.getVisibility() : QueryVisibility.PRIVATE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        log.info("Saving query '{}' for user {} on model {}", request.getTitle(), identity.userId(), request.getModel());
        return savedQueryRepository.save(def);
    }

    /**
     * 列出用户可见的保存查询
     */
    public List<SavedQueryDef> list(String model, String authorization) {
        ResolvedIdentity identity = identityResolver.resolve(authorization);
        return savedQueryRepository.findVisibleQueries(
                model, identity.userId(), identity.deptId(), identity.tenantId()
        );
    }

    /**
     * 获取单个查询详情
     */
    public Optional<SavedQueryDef> get(String id) {
        return savedQueryRepository.findById(id);
    }

    /**
     * 更新查询（仅 owner）
     */
    public Optional<SavedQueryDef> update(String id, SaveQueryRequest request, String authorization) {
        ResolvedIdentity identity = identityResolver.resolve(authorization);

        return savedQueryRepository.findById(id)
                .filter(def -> def.getOwnerId().equals(identity.userId()))
                .map(def -> {
                    def.setTitle(request.getTitle());
                    def.setDescription(request.getDescription());
                    def.setColumns(request.getColumns());
                    def.setSlice(request.getSlice());
                    def.setOrderBy(request.getOrderBy());
                    def.setGroupBy(request.getGroupBy());
                    def.setCalculatedFields(request.getCalculatedFields());
                    def.setVisibility(request.getVisibility() != null ? request.getVisibility() : def.getVisibility());
                    def.setUpdatedAt(Instant.now());
                    return savedQueryRepository.save(def);
                });
    }

    /**
     * 删除查询（仅 owner）
     */
    public boolean delete(String id, String authorization) {
        ResolvedIdentity identity = identityResolver.resolve(authorization);

        return savedQueryRepository.findById(id)
                .filter(def -> def.getOwnerId().equals(identity.userId()))
                .map(def -> {
                    savedQueryRepository.delete(def);
                    log.info("Deleted saved query '{}' by user {}", def.getTitle(), identity.userId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * 应用保存的查询（创建临时 cached_query 并返回 queryId）
     */
    public Optional<String> apply(String id) {
        return savedQueryRepository.findById(id)
                .map(def -> {
                    QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
                    request.setModel(def.getModel());
                    request.setTitle(def.getTitle());
                    request.setColumns(def.getColumns());
                    request.setSlice(def.getSlice());
                    request.setOrderBy(def.getOrderBy());
                    request.setGroupBy(def.getGroupBy());
                    request.setCalculatedFields(def.getCalculatedFields());

                    CachedQueryContext ctx = queryCacheService.cacheQuery(request, null);
                    log.info("Applied saved query '{}' → cached queryId: {}", def.getTitle(), ctx.getQueryId());
                    return ctx.getQueryId();
                });
    }

    /**
     * 保存查询请求
     */
    @Data
    public static class SaveQueryRequest {
        private String model;
        private String title;
        private String description;
        private List<String> columns;
        private List<com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef> slice;
        private List<com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef> orderBy;
        private List<com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef> groupBy;
        private List<com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef> calculatedFields;
        private QueryVisibility visibility;
    }
}
