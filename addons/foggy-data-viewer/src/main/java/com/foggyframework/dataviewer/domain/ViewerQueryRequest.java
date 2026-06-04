package com.foggyframework.dataviewer.domain;

import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 前端查询请求
 * <p>
 * 用于数据浏览器前端发起的数据查询请求。
 * 直接使用 DSL 格式 (SliceRequestDef, OrderRequestDef)，无需转换。
 */
@Data
public class ViewerQueryRequest {

    /**
     * 可选命名空间。HTTP X-NS header 优先于此字段。
     */
    private String namespace;

    /**
     * 运行时扩展参数。
     * <p>
     * 仅透传到 DbQueryRequestDef.extData，供 QM 中显式声明的运行时表达式使用，
     * 不自动转换为 slice / where 条件。
     */
    private Map<String, Object> extData;

    /**
     * 起始位置
     */
    private Integer start = 0;

    /**
     * 每页大小
     */
    private Integer limit = 50;

    /**
     * 查询列。为空时由底层查询模型使用默认列。
     */
    private List<String> columns;

    /**
     * 用户过滤条件 (DSL slice 格式)
     * 直接传入 SliceRequestDef 数组
     */
    private List<SliceRequestDef> slice;

    /**
     * 排序条件 (DSL orderBy 格式)
     */
    private List<OrderRequestDef> orderBy;

    /**
     * 动态分组字段（聚合模式）
     */
    private List<GroupRequestDef> groupBy;

    /**
     * 聚合项
     */
    private List<AggregationItem> aggregations;

    @Data
    public static class AggregationItem {
        private String field;
        private String type; // sum, avg, min, max, count
    }
}
