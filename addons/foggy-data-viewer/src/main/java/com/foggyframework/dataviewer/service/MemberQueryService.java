package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.domain.MemberQueryResponse;
import com.foggyframework.dataviewer.domain.MemberQueryResponse.MemberOption;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 维度成员查询服务（data-viewer adapter 层）
 * <p>
 * 职责：
 * 1. 接收前端的 qmModel + fieldName + 查询参数
 * 2. 映射到内部 synthetic member-QM（自动推导 selectionFieldName / displayFieldName）
 * 3. 调用 QueryFacade 执行查询
 * 4. 归一化成 MemberQueryResponse
 * <p>
 * 不暴露 synthetic member-QM 名称和 /jdbc-model/ URL 给前端。
 */
@Slf4j
@RequiredArgsConstructor
public class MemberQueryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;
    private static final String SEPARATOR = "$";
    private static final String SYNTHETIC_SEPARATOR = "#";

    /** 本版本前端只启用 childrenOf */
    private static final List<String> SUPPORTED_HIERARCHY_OPS = List.of(
            "childrenOf", "descendantsOf", "selfAndDescendantsOf",
            "ancestorsOf", "selfAndAncestorsOf"
    );

    private final QueryFacade queryFacade;

    /**
     * 查询维度成员
     */
    public MemberQueryResponse query(MemberQueryRequest req) {
        String qmModel = req.getQmModel();
        String fieldName = req.getFieldName();

        // 推导维度基名、selectionFieldName、displayFieldName
        FieldMapping mapping = resolveFieldMapping(fieldName);
        String syntheticModelName = qmModel + SYNTHETIC_SEPARATOR + mapping.dimBaseName;

        int start = req.getStart() != null ? req.getStart() : 0;
        int limit = req.getLimit() != null ? Math.min(req.getLimit(), MAX_LIMIT) : DEFAULT_LIMIT;

        // 构建主查询（keyword + hierarchy + 分页）
        List<MemberOption> items = new ArrayList<>();
        long total = 0;

        DbQueryRequestDef queryDef = buildQueryDef(syntheticModelName, req);
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(start);
        pagingRequest.setLimit(limit);

        try {
            PagingResultImpl result = queryFacade.queryModelData(pagingRequest);
            items = mapItems(result.getItems(), mapping.hierarchyEnabled);
            total = result.getTotal() > 0 ? result.getTotal() : items.size();
        } catch (Exception e) {
            log.error("Member query failed for {}#{}: {}", qmModel, fieldName, e.getMessage(), e);
            throw new RuntimeException("维度成员查询失败: " + e.getMessage(), e);
        }

        // 回填已选值（单独查询）
        List<MemberOption> selectedItems = null;
        if (req.getSelectedValues() != null && !req.getSelectedValues().isEmpty()) {
            selectedItems = querySelectedValues(syntheticModelName, req.getSelectedValues());
        }

        return MemberQueryResponse.builder()
                .qmModel(qmModel)
                .fieldName(fieldName)
                .selectionFieldName(mapping.selectionFieldName)
                .displayFieldName(mapping.displayFieldName)
                .hierarchical(mapping.hierarchyEnabled ? true : null)
                .hierarchyOps(mapping.hierarchyEnabled ? SUPPORTED_HIERARCHY_OPS : null)
                .items(items)
                .selectedItems(selectedItems)
                .total(total)
                .hasMore(start + items.size() < total)
                .build();
    }

    /**
     * 构建查询请求
     */
    private DbQueryRequestDef buildQueryDef(String syntheticModelName, MemberQueryRequest req) {
        DbQueryRequestDef def = new DbQueryRequestDef();
        def.setQueryModel(syntheticModelName);
        def.setColumns(List.of("id", "caption"));
        def.setReturnTotal(true);
        def.setDistinct(true);

        List<SliceRequestDef> slices = new ArrayList<>();

        // keyword 搜索（对 caption 做 like）
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            SliceRequestDef keywordSlice = new SliceRequestDef();
            keywordSlice.setField("caption");
            keywordSlice.setOp("like");
            keywordSlice.setValue(req.getKeyword().trim());
            slices.add(keywordSlice);
        }

        // hierarchy 过滤
        if (req.getHierarchy() != null && req.getHierarchy().getOp() != null) {
            SliceRequestDef hierarchySlice = new SliceRequestDef();
            hierarchySlice.setField("id");
            hierarchySlice.setOp(req.getHierarchy().getOp());
            hierarchySlice.setValue(req.getHierarchy().getValue());
            slices.add(hierarchySlice);
        }

        if (!slices.isEmpty()) {
            def.setSlice(slices);
        }

        // 默认按 caption 排序
        OrderRequestDef orderBy = new OrderRequestDef();
        orderBy.setField("caption");
        orderBy.setDir("ASC");
        def.setOrderBy(List.of(orderBy));

        return def;
    }

    /**
     * 回填已选值（通过 id in (...) 查询）
     */
    private List<MemberOption> querySelectedValues(String syntheticModelName, List<Object> selectedValues) {
        try {
            DbQueryRequestDef def = new DbQueryRequestDef();
            def.setQueryModel(syntheticModelName);
            def.setColumns(List.of("id", "caption"));

            SliceRequestDef inSlice = new SliceRequestDef();
            inSlice.setField("id");
            inSlice.setOp("in");
            inSlice.setValue(selectedValues);
            def.setSlice(List.of(inSlice));

            PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
            pagingRequest.setParam(def);
            pagingRequest.setStart(0);
            pagingRequest.setLimit(selectedValues.size());

            PagingResultImpl result = queryFacade.queryModelData(pagingRequest);
            return mapItems(result.getItems(), false);
        } catch (Exception e) {
            log.warn("Failed to resolve selected values for {}: {}", syntheticModelName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 将查询结果映射为 MemberOption 列表
     */
    @SuppressWarnings("unchecked")
    private List<MemberOption> mapItems(List<?> rawItems, boolean includeHierarchy) {
        if (rawItems == null) return List.of();

        List<MemberOption> options = new ArrayList<>(rawItems.size());
        for (Object item : rawItems) {
            if (item instanceof Map<?, ?> row) {
                MemberOption.MemberOptionBuilder builder = MemberOption.builder()
                        .value(row.get("id"))
                        .label(row.get("caption") != null ? row.get("caption").toString() : null);

                if (includeHierarchy) {
                    builder.parentValue(row.get("parentId"));
                    if (row.get("depth") instanceof Number depth) {
                        builder.depth(depth.intValue());
                    }
                    if (row.get("hasChildren") instanceof Boolean hasChildren) {
                        builder.hasChildren(hasChildren);
                    }
                }

                options.add(builder.build());
            }
        }
        return options;
    }

    /**
     * 推导字段映射
     * <p>
     * customer$caption → dimBaseName=customer, selectionFieldName=customer$id, displayFieldName=customer$caption
     * customer$id     → dimBaseName=customer, selectionFieldName=customer$id, displayFieldName=customer$caption
     * team$caption    → dimBaseName=team, ...
     */
    private FieldMapping resolveFieldMapping(String fieldName) {
        String dimBaseName;
        String selectionFieldName;
        String displayFieldName;

        if (fieldName.contains(SEPARATOR)) {
            int idx = fieldName.indexOf(SEPARATOR);
            dimBaseName = fieldName.substring(0, idx);
            selectionFieldName = dimBaseName + "$id";
            displayFieldName = dimBaseName + "$caption";
        } else {
            // 无 $ 分隔的字段，直接用作维度基名
            dimBaseName = fieldName;
            selectionFieldName = fieldName + "$id";
            displayFieldName = fieldName + "$caption";
        }

        return new FieldMapping(dimBaseName, selectionFieldName, displayFieldName, false);
    }

    private record FieldMapping(
            String dimBaseName,
            String selectionFieldName,
            String displayFieldName,
            boolean hierarchyEnabled
    ) {}
}
