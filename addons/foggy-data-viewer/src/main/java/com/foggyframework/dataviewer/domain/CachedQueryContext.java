package com.foggyframework.dataviewer.domain;

import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * 缓存的查询上下文
 * <p>
 * 存储在MongoDB中，用于数据浏览器根据queryId获取查询参数。
 * 复用 foggy-dataset-model 中的请求定义类实现类型安全。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cached_queries")
public class CachedQueryContext {

    /**
     * 查询ID（用于构建浏览器URL）
     */
    @Id
    private String queryId;

    /**
     * 查询模型名称
     */
    private String model;

    /**
     * 要显示的列
     */
    private List<String> columns;

    /**
     * 过滤条件
     */
    private List<SliceRequestDef> slice;

    /**
     * 分组条件
     */
    private List<GroupRequestDef> groupBy;

    /**
     * 排序条件
     */
    private List<OrderRequestDef> orderBy;

    /**
     * 动态计算字段
     */
    private List<CalculatedFieldDef> calculatedFields;

    /**
     * 数据视图标题
     */
    private String title;

    /**
     * 原始用户授权上下文
     */
    private String authorization;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 过期时间（MongoDB TTL索引会自动删除过期文档）
     */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    /**
     * 预估行数
     */
    private Long estimatedRowCount;

    /**
     * 列元数据（用于前端渲染）
     */
    private List<ColumnSchema> schema;

    /**
     * 列元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnSchema {
        /**
         * 列名
         */
        private String name;

        /**
         * 数据类型（TEXT, NUMBER, DAY等）
         */
        private String type;

        /**
         * 显示标题
         */
        private String title;

        /**
         * 是否可过滤
         */
        private boolean filterable;

        /**
         * 是否可聚合
         */
        private boolean aggregatable;
    }

    /**
     * 构建 DbQueryRequestDef
     * <p>
     * 将缓存的查询上下文转换为 QueryFacade 可执行的请求对象
     *
     * @return DbQueryRequestDef 实例
     */
    public DbQueryRequestDef toDbQueryRequestDef() {
        DbQueryRequestDef def = new DbQueryRequestDef();
        def.setQueryModel(this.model);
        def.setColumns(this.columns);
        def.setSlice(this.slice);
        def.setGroupBy(this.groupBy);
        def.setOrderBy(this.orderBy);
        def.setCalculatedFields(this.calculatedFields);
        def.setReturnTotal(true);
        return def;
    }
}
