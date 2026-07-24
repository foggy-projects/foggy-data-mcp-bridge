package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberExternalPatch;
import com.foggyframework.dataset.model.semantic.member.SyntheticMemberQueryModelResolver;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在 QueryFacade 主链中为 synthetic member-QM 合并外部权限 patch。
 */
@Component
@Order(-10)
public class SyntheticMemberExternalPatchStep implements DataSetResultStep {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        if (ctx == null || ctx.getRequest() == null || ctx.getRequest().getParam() == null) {
            return CONTINUE;
        }

        QueryModel queryModel = ctx.getQueryModel();
        if (!isSyntheticMemberQueryModel(queryModel)) {
            return CONTINUE;
        }

        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();
        SyntheticMemberExternalPatch patch = resolvePatch(queryRequest.getExtData());
        if (patch == null || patch.isEmpty()) {
            return CONTINUE;
        }

        LinkedHashSet<String> schemaFields = resolveSchemaFields(queryModel);
        LinkedHashSet<String> visibleColumns = normalizeVisibleColumns(patch.getVisibleColumns(), schemaFields);

        mergeColumns(queryRequest, visibleColumns);
        mergeSlice(queryRequest, patch.getForcedSlice(), schemaFields);
        mergeOrderBy(queryRequest, visibleColumns, patch.getForcedOrderBy(), schemaFields);

        ctx.getExtData().put(SyntheticMemberExternalPatch.EXT_DATA_KEY, patch);
        ctx.getExtData().put("syntheticMemberVisibleColumns", visibleColumns);
        ctx.getExtData().put("syntheticMemberEffectiveColumns", queryRequest.getColumns());

        return CONTINUE;
    }

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
        for (DbQueryColumn queryColumn : queryModel.getJdbcQueryColumns()) {
            if (queryColumn != null && StringUtils.isNotEmpty(queryColumn.getName())) {
                fields.add(queryColumn.getName());
            }
        }
        return fields;
    }

    private SyntheticMemberExternalPatch resolvePatch(Object extData) {
        if (extData == null) {
            return null;
        }
        if (extData instanceof SyntheticMemberExternalPatch patch) {
            return patch;
        }
        if (extData instanceof Map<?, ?> map) {
            Object nested = map.get(SyntheticMemberExternalPatch.EXT_DATA_KEY);
            if (nested != null) {
                return resolvePatch(nested);
            }
            if (containsPatchKey(map)) {
                return objectMapper.convertValue(map, SyntheticMemberExternalPatch.class);
            }
        }
        return null;
    }

    private boolean containsPatchKey(Map<?, ?> map) {
        return map.containsKey("visibleColumns")
                || map.containsKey("forcedSlice")
                || map.containsKey("forcedOrderBy");
    }

    private LinkedHashSet<String> normalizeVisibleColumns(List<String> visibleColumns, Set<String> schemaFields) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (visibleColumns == null || visibleColumns.isEmpty()) {
            return normalized;
        }
        for (String field : visibleColumns) {
            if (schemaFields.contains(field)) {
                normalized.add(field);
            }
        }
        if (normalized.isEmpty()) {
            throw RX.throwAUserTip("synthetic member-QM external patch 未命中任何可见列");
        }
        return normalized;
    }

    private void mergeColumns(DbQueryRequestDef queryRequest, LinkedHashSet<String> visibleColumns) {
        if (visibleColumns.isEmpty()) {
            return;
        }

        List<String> requestColumns = queryRequest.getColumns();
        if (requestColumns == null || requestColumns.isEmpty()) {
            queryRequest.setColumns(new ArrayList<>(visibleColumns));
            return;
        }

        List<String> effectiveColumns = new ArrayList<>();
        for (String field : requestColumns) {
            if (visibleColumns.contains(field)) {
                effectiveColumns.add(field);
            }
        }
        if (effectiveColumns.isEmpty()) {
            throw RX.throwAUserTip("synthetic member-QM 请求列在 visibleColumns 求交后为空");
        }
        queryRequest.setColumns(effectiveColumns);
    }

    private void mergeSlice(DbQueryRequestDef queryRequest,
                            List<SliceRequestDef> forcedSlice,
                            Set<String> schemaFields) {
        if (forcedSlice == null || forcedSlice.isEmpty()) {
            return;
        }

        validateSliceFields(forcedSlice, schemaFields, "forcedSlice");

        List<SliceRequestDef> merged = new ArrayList<>(forcedSlice);
        if (queryRequest.getSlice() != null && !queryRequest.getSlice().isEmpty()) {
            merged.addAll(queryRequest.getSlice());
        }
        queryRequest.setSlice(merged);
    }

    private void validateSliceFields(List<? extends CondRequestDef> conditions,
                                     Set<String> schemaFields,
                                     String source) {
        for (CondRequestDef condition : conditions) {
            if (condition == null) {
                continue;
            }
            if (condition._isLogicalGroup()) {
                validateSliceFields(condition._getGroupChildren(), schemaFields, source);
                continue;
            }
            String field = condition.getField();
            if (StringUtils.isNotEmpty(field) && !schemaFields.contains(field)) {
                throw RX.throwAUserTip("synthetic member-QM " + source + " 包含非法字段: " + field);
            }
        }
    }

    private void mergeOrderBy(DbQueryRequestDef queryRequest,
                              LinkedHashSet<String> visibleColumns,
                              List<OrderRequestDef> forcedOrderBy,
                              Set<String> schemaFields) {
        List<OrderRequestDef> sanitizedRequestOrderBy = sanitizeRequestOrderBy(
                queryRequest.getOrderBy(),
                visibleColumns,
                schemaFields
        );
        List<OrderRequestDef> sanitizedForcedOrderBy = sanitizeForcedOrderBy(forcedOrderBy, schemaFields);

        if (sanitizedRequestOrderBy.isEmpty() && sanitizedForcedOrderBy.isEmpty()) {
            queryRequest.setOrderBy(sanitizedRequestOrderBy);
            return;
        }

        if (sanitizedForcedOrderBy.isEmpty()) {
            queryRequest.setOrderBy(sanitizedRequestOrderBy);
            return;
        }

        Set<String> forcedFields = new LinkedHashSet<>();
        for (OrderRequestDef order : sanitizedForcedOrderBy) {
            forcedFields.add(order.getField());
        }

        List<OrderRequestDef> merged = new ArrayList<>();
        for (OrderRequestDef order : sanitizedRequestOrderBy) {
            if (!forcedFields.contains(order.getField())) {
                merged.add(order);
            }
        }
        merged.addAll(sanitizedForcedOrderBy);
        queryRequest.setOrderBy(merged);
    }

    private List<OrderRequestDef> sanitizeRequestOrderBy(List<OrderRequestDef> orderBy,
                                                         LinkedHashSet<String> visibleColumns,
                                                         Set<String> schemaFields) {
        if (orderBy == null || orderBy.isEmpty()) {
            return new ArrayList<>();
        }

        List<OrderRequestDef> sanitized = new ArrayList<>();
        for (OrderRequestDef item : orderBy) {
            if (item == null || StringUtils.isEmpty(item.getField())) {
                continue;
            }
            if (!schemaFields.contains(item.getField())) {
                sanitized.add(item);
                continue;
            }
            if (!visibleColumns.isEmpty() && !visibleColumns.contains(item.getField())) {
                continue;
            }
            sanitized.add(cloneOrder(item));
        }
        return sanitized;
    }

    private List<OrderRequestDef> sanitizeForcedOrderBy(List<OrderRequestDef> orderBy,
                                                        Set<String> schemaFields) {
        if (orderBy == null || orderBy.isEmpty()) {
            return new ArrayList<>();
        }

        List<OrderRequestDef> sanitized = new ArrayList<>();
        for (OrderRequestDef item : orderBy) {
            if (item == null || StringUtils.isEmpty(item.getField())) {
                continue;
            }
            if (!schemaFields.contains(item.getField())) {
                throw RX.throwAUserTip("synthetic member-QM forcedOrderBy 包含非法字段: " + item.getField());
            }
            sanitized.add(cloneOrder(item));
        }
        return sanitized;
    }

    private OrderRequestDef cloneOrder(OrderRequestDef source) {
        OrderRequestDef target = new OrderRequestDef();
        target.setField(source.getField());
        target.setDir(source.getDir());
        target.setNullFirst(source.isNullFirst());
        target.setNullLast(source.isNullLast());
        return target;
    }
}
