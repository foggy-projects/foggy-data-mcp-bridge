package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.semantic.member.permission.*;
import com.foggyframework.dataset.model.spi.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 在 QueryFacade 主链中为 synthetic member-QM 合并内部成员权限 patch。
 * <p>
 * 执行优先级 @Order(-20)，在 external patch（-10）之前执行。
 * <p>
 * 执行链路：schema → TM.patch → QM.patch → [本步骤输出] → external patch → request
 */
@Component
@Order(-20)
@Slf4j
public class SyntheticMemberInternalPatchStep implements DataSetResultStep {

    public static final String EFFECTIVE_PERMISSION_KEY = "syntheticMemberInternalPermission";

    @Resource
    private QueryModelLoader queryModelLoader;

    private final SyntheticMemberPermissionResolver permissionResolver = new SyntheticMemberPermissionResolver();

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.getRequest() == null || ctx.getRequest().getParam() == null) {
            return CONTINUE;
        }

        QueryModel queryModel = ctx.getQueryModel();
        if (!isSyntheticMemberQueryModel(queryModel)) {
            return CONTINUE;
        }

        String modelName = queryModel.getName();
        int sep = modelName.indexOf(SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
        String sourceModelName = modelName.substring(0, sep);
        String dimFieldBase = modelName.substring(sep + 1);

        SyntheticMemberEffectivePermission effective = resolveEffectivePermission(
                sourceModelName, dimFieldBase, ctx.getNamespace()
        );

        if (effective == null || effective.isEmpty()) {
            return CONTINUE;
        }

        // 存储 effective permission 供后续步骤使用（如 QueryBuilderStep）
        ctx.getExtData().put(EFFECTIVE_PERMISSION_KEY, effective);

        if (!effective.hasPatch()) {
            return CONTINUE;
        }

        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();
        LinkedHashSet<String> schemaFields = resolveSchemaFields(queryModel);

        // --- patch 应用 ---
        applyVisibleColumns(queryRequest, effective.getVisibleColumns(), schemaFields);
        applyForcedSlice(queryRequest, effective.getForcedSlice(), schemaFields, ctx, sourceModelName, dimFieldBase);
        applyForcedOrderBy(queryRequest, effective.getVisibleColumns(), effective.getForcedOrderBy(), schemaFields);
        applyHierarchyCheck(queryRequest, effective, schemaFields);

        return CONTINUE;
    }

    // ==================== 权限解析 ====================

    private SyntheticMemberEffectivePermission resolveEffectivePermission(String sourceModelName,
                                                                          String dimFieldBase,
                                                                          String namespace) {
        QueryModel sourceModel;
        try {
            sourceModel = queryModelLoader.getJdbcQueryModel(sourceModelName, namespace);
        } catch (Exception e) {
            log.warn("内部成员权限解析失败，无法加载源 QM: {}", sourceModelName, e);
            return null;
        }
        if (sourceModel == null) {
            return null;
        }

        // TM 维度级权限
        MemberPermissionDef tmPermission = resolveTmPermission(sourceModel, dimFieldBase);

        // QM 级权限
        QmMemberPermissionDef qmPermission = resolveQmPermission(sourceModel, dimFieldBase);

        if (tmPermission == null && qmPermission == null) {
            return null;
        }

        return permissionResolver.resolve(tmPermission, qmPermission);
    }

    private MemberPermissionDef resolveTmPermission(QueryModel sourceModel, String dimFieldBase) {
        TableModel tableModel = sourceModel.getJdbcModel();
        if (tableModel == null || tableModel.getDimensions() == null) {
            return null;
        }
        for (DbDimension dim : tableModel.getDimensions()) {
            if (StringUtils.equals(dim.getEffectiveName(), dimFieldBase) && dim instanceof DbDimensionSupport ds) {
                return ds.getMemberPermission();
            }
        }
        return null;
    }

    private QmMemberPermissionDef resolveQmPermission(QueryModel sourceModel, String dimFieldBase) {
        QueryModelSupport qms = sourceModel.getDecorate(QueryModelSupport.class);
        if (qms == null || qms.getMemberPermissions() == null) {
            return null;
        }
        for (QmMemberPermissionDef def : qms.getMemberPermissions()) {
            if (StringUtils.equals(def.getDimension(), dimFieldBase)) {
                return def;
            }
        }
        return null;
    }

    // ==================== patch 应用 ====================

    private void applyVisibleColumns(DbQueryRequestDef queryRequest,
                                      List<String> visibleColumns,
                                      LinkedHashSet<String> schemaFields) {
        if (visibleColumns == null || visibleColumns.isEmpty()) {
            return;
        }

        // 与 schema 求交
        LinkedHashSet<String> effectiveVisible = new LinkedHashSet<>();
        for (String col : visibleColumns) {
            if (schemaFields.contains(col)) {
                effectiveVisible.add(col);
            }
        }
        if (effectiveVisible.isEmpty()) {
            return;
        }

        // 与请求列求交
        List<String> requestColumns = queryRequest.getColumns();
        if (requestColumns == null || requestColumns.isEmpty()) {
            queryRequest.setColumns(new ArrayList<>(effectiveVisible));
        } else {
            List<String> intersected = new ArrayList<>();
            for (String col : requestColumns) {
                if (effectiveVisible.contains(col)) {
                    intersected.add(col);
                }
            }
            if (intersected.isEmpty()) {
                throw RX.throwAUserTip("synthetic member-QM 内部权限 visibleColumns 与请求列求交后为空");
            }
            queryRequest.setColumns(intersected);
        }
    }

    private void applyForcedSlice(DbQueryRequestDef queryRequest,
                                   List<MemberPermissionSliceDef> forcedSlice,
                                   LinkedHashSet<String> schemaFields,
                                   ModelResultContext ctx,
                                   String sourceModelName,
                                   String dimFieldBase) {
        if (forcedSlice == null || forcedSlice.isEmpty()) {
            return;
        }

        // 构建 valueBuilder 上下文
        Map<String, Object> valueContext = buildValueBuilderContext(ctx);

        List<SliceRequestDef> sliceItems = new ArrayList<>();
        for (MemberPermissionSliceDef sliceDef : forcedSlice) {
            String field = sliceDef.getField();
            if (StringUtils.isEmpty(field) || !schemaFields.contains(field)) {
                throw RX.throwAUserTip("synthetic member-QM 内部权限字段不存在: field=" + field
                        + ", qmModel=" + sourceModelName
                        + ", memberField=" + dimFieldBase + SyntheticMemberQueryModelResolver.FIELD_SEPARATOR + "caption");
            }

            Object resolvedValue = sliceDef.resolveValue(valueContext);
            SliceRequestDef sliceItem = new SliceRequestDef(field, sliceDef.getOp(), resolvedValue);
            sliceItems.add(sliceItem);
        }

        if (sliceItems.isEmpty()) {
            return;
        }

        // 前置合并（forcedSlice + request slice）
        List<SliceRequestDef> merged = new ArrayList<>(sliceItems);
        if (queryRequest.getSlice() != null && !queryRequest.getSlice().isEmpty()) {
            merged.addAll(queryRequest.getSlice());
        }
        queryRequest.setSlice(merged);
    }

    private void applyForcedOrderBy(DbQueryRequestDef queryRequest,
                                     List<String> visibleColumns,
                                     List<OrderRequestDef> forcedOrderBy,
                                     LinkedHashSet<String> schemaFields) {
        if (forcedOrderBy == null || forcedOrderBy.isEmpty()) {
            return;
        }

        // 验证 forced order 字段在 schema 内
        List<OrderRequestDef> validForcedOrders = new ArrayList<>();
        for (OrderRequestDef order : forcedOrderBy) {
            if (order != null && StringUtils.isNotEmpty(order.getField()) && schemaFields.contains(order.getField())) {
                validForcedOrders.add(order);
            }
        }

        if (validForcedOrders.isEmpty()) {
            return;
        }

        Set<String> forcedFields = new LinkedHashSet<>();
        for (OrderRequestDef order : validForcedOrders) {
            forcedFields.add(order.getField());
        }

        // 保留 request 中非冲突的排序项
        List<OrderRequestDef> merged = new ArrayList<>();
        if (queryRequest.getOrderBy() != null) {
            for (OrderRequestDef order : queryRequest.getOrderBy()) {
                if (order != null && !forcedFields.contains(order.getField())) {
                    merged.add(order);
                }
            }
        }
        merged.addAll(validForcedOrders);
        queryRequest.setOrderBy(merged);
    }

    private void applyHierarchyCheck(DbQueryRequestDef queryRequest,
                                      SyntheticMemberEffectivePermission effective,
                                      LinkedHashSet<String> schemaFields) {
        // hierarchyEnabled=false 时，拦截层级操作
        if (Boolean.FALSE.equals(effective.getHierarchyEnabled())) {
            if (queryRequest.getSlice() != null) {
                for (SliceRequestDef slice : queryRequest.getSlice()) {
                    if (isHierarchyOp(slice.getOp())) {
                        throw RX.throwAUserTip("synthetic member-QM 内部权限已禁用层级操作");
                    }
                }
            }
        }

        // allowedHierarchyOps 白名单校验
        List<String> allowed = effective.getAllowedHierarchyOps();
        if (allowed != null && !allowed.isEmpty() && queryRequest.getSlice() != null) {
            for (SliceRequestDef slice : queryRequest.getSlice()) {
                if (isHierarchyOp(slice.getOp()) && !allowed.contains(slice.getOp())) {
                    throw RX.throwAUserTip("synthetic member-QM 内部权限不允许层级操作: " + slice.getOp());
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private boolean isSyntheticMemberQueryModel(QueryModel queryModel) {
        return queryModel != null
                && StringUtils.isNotEmpty(queryModel.getName())
                && queryModel.getName().contains(SyntheticMemberQueryModelResolver.MODEL_SEPARATOR);
    }

    private LinkedHashSet<String> resolveSchemaFields(QueryModel queryModel) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (queryModel == null || queryModel.getJdbcQueryColumns() == null) {
            return fields;
        }
        for (DbQueryColumn col : queryModel.getJdbcQueryColumns()) {
            if (col != null && StringUtils.isNotEmpty(col.getName())) {
                fields.add(col.getName());
            }
        }
        return fields;
    }

    private boolean isHierarchyOp(String op) {
        if (StringUtils.isEmpty(op)) {
            return false;
        }
        return "childrenOf".equals(op)
                || "descendantsOf".equals(op)
                || "ancestorsOf".equals(op)
                || "selfAndDescendantsOf".equals(op)
                || "selfAndAncestorsOf".equals(op);
    }

    private Map<String, Object> buildValueBuilderContext(ModelResultContext ctx) {
        Map<String, Object> context = new HashMap<>();
        if (ctx.getRequest() != null) {
            context.put("request", ctx.getRequest());
        }
        if (ctx.getSecurityContext() != null) {
            context.put("security", ctx.getSecurityContext());
        }
        if (ctx.getExtData() != null) {
            context.put("extData", ctx.getExtData());
        }
        if (ctx.getNamespace() != null) {
            context.put("namespace", ctx.getNamespace());
        }
        return context;
    }
}
