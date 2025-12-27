package com.foggyframework.dataset.db.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.db.model.impl.query.DbQueryGroupColumnImpl;
import com.foggyframework.dataset.db.model.impl.utils.SqlQueryObject;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 聚合SQL优化器
 * <p>
 * 优化聚合查询的子查询，只保留必要的字段和表关联，提升性能。
 * </p>
 *
 * <h3>优化原理</h3>
 * <pre>
 * 原始方式：
 *   SELECT sum(tx.col1), sum(tx.col2), count(*)
 *   FROM (
 *     SELECT col1, col2, col3, col4, ... col100  -- 所有列
 *     FROM main_table
 *     LEFT JOIN table1 ON ...
 *     LEFT JOIN table2 ON ...
 *     ... (多个不必要的 JOIN)
 *     WHERE ...
 *   ) tx
 *
 * 优化后：
 *   SELECT sum(tx.col1), sum(tx.col2), count(*)
 *   FROM (
 *     SELECT col1, col2                          -- 只保留必要列
 *     FROM main_table
 *     LEFT JOIN table_for_col2 ON ...            -- 只保留必要的 JOIN
 *     WHERE ...
 *   ) tx
 * </pre>
 */
@Slf4j
public class AggSqlOptimizer {

    private final JdbcQueryModel jdbcQueryModel;
    private final JdbcQuery originalQuery;
    private final SystemBundlesContext systemBundlesContext;
    private final DbQueryRequestDef queryRequest;

    /**
     * 优化结果
     */
    public static class OptimizationResult {
        private final String originalSql;
        private final String optimizedSql;
        private final int originalColumnCount;
        private final int optimizedColumnCount;
        private final int originalJoinCount;
        private final int optimizedJoinCount;
        private final boolean optimizationApplied;

        public OptimizationResult(String originalSql, String optimizedSql,
                                  int originalColumnCount, int optimizedColumnCount,
                                  int originalJoinCount, int optimizedJoinCount,
                                  boolean optimizationApplied) {
            this.originalSql = originalSql;
            this.optimizedSql = optimizedSql;
            this.originalColumnCount = originalColumnCount;
            this.optimizedColumnCount = optimizedColumnCount;
            this.originalJoinCount = originalJoinCount;
            this.optimizedJoinCount = optimizedJoinCount;
            this.optimizationApplied = optimizationApplied;
        }

        public String getOriginalSql() {
            return originalSql;
        }

        public String getOptimizedSql() {
            return optimizedSql;
        }

        public int getOriginalColumnCount() {
            return originalColumnCount;
        }

        public int getOptimizedColumnCount() {
            return optimizedColumnCount;
        }

        public int getOriginalJoinCount() {
            return originalJoinCount;
        }

        public int getOptimizedJoinCount() {
            return optimizedJoinCount;
        }

        public boolean isOptimizationApplied() {
            return optimizationApplied;
        }

        public String getSummary() {
            if (!optimizationApplied) {
                return "优化未应用";
            }
            return String.format("列数: %d -> %d (减少 %d), JOIN数: %d -> %d (减少 %d)",
                    originalColumnCount, optimizedColumnCount, originalColumnCount - optimizedColumnCount,
                    originalJoinCount, optimizedJoinCount, originalJoinCount - optimizedJoinCount);
        }
    }

    public AggSqlOptimizer(JdbcQueryModel jdbcQueryModel, JdbcQuery originalQuery,
                           SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest) {
        this.jdbcQueryModel = jdbcQueryModel;
        this.originalQuery = originalQuery;
        this.systemBundlesContext = systemBundlesContext;
        this.queryRequest = queryRequest;
    }

    /**
     * 构建优化后的聚合SQL
     *
     * @param innerSqlWithoutOrder 原始的内部SQL（不含 ORDER BY）
     * @param countToSum           是否将 COUNT 转换为 SUM（用于 GroupBy 场景）
     * @return 优化结果，包含原始SQL、优化后SQL和统计信息
     */
    public OptimizationResult buildOptimizedAggSql(String innerSqlWithoutOrder, boolean countToSum) {
        List<DbColumn> selectColumns = originalQuery.getSelect().getColumns();

        // 1. 分析需要聚合的列
        // 判断逻辑：
        // - AggregationDbColumn 且 groupByName == null 表示是聚合列
        // - 普通 DbColumn 且 getAggregation() != null && != NONE 表示是聚合列
        List<DbColumn> aggRequiredColumns = new ArrayList<>();
        for (DbColumn column : selectColumns) {
            if (isAggregateColumn(column)) {
                aggRequiredColumns.add(column);
            }
        }

        // 如果没有聚合列，直接返回原始SQL
        if (aggRequiredColumns.isEmpty()) {
            String originalAggSql = buildAggSqlFromInner(innerSqlWithoutOrder, selectColumns, countToSum);
            return new OptimizationResult(originalAggSql, originalAggSql,
                    selectColumns.size(), selectColumns.size(),
                    countJoins(), countJoins(), false);
        }

        // 2. 收集分组列（如果有 GROUP BY）
        // 分组列也需要保留在子查询中，否则 GROUP BY 会失效
        if (originalQuery.getGroup() != null && originalQuery.getGroup().getGroups() != null) {
            for (DbQueryGroupColumnImpl groupColumn : originalQuery.getGroup().getGroups()) {
                if (groupColumn.getAggColumn() != null) {
                    // 分组列也需要加入 aggRequiredColumns，这样才会出现在 SELECT 中
                    if (!aggRequiredColumns.contains(groupColumn.getAggColumn())) {
                        aggRequiredColumns.add(groupColumn.getAggColumn());
                    }
                }
            }
        }

        // 3. 直接在原始 query 上替换 SELECT 列生成精简的内部 SQL
        String optimizedInnerSql = buildOptimizedInnerSql(aggRequiredColumns);

        // 4. 构建最终的聚合 SQL
        String optimizedAggSql = buildAggSqlFromInner(optimizedInnerSql, aggRequiredColumns, countToSum);
        String originalAggSql = buildAggSqlFromInner(innerSqlWithoutOrder, selectColumns, countToSum);

        int originalJoinCount = countJoins();

        if (log.isDebugEnabled()) {
            log.debug("聚合SQL优化: 列数 {} -> {}",
                    selectColumns.size(), aggRequiredColumns.size());
        }

        return new OptimizationResult(originalAggSql, optimizedAggSql,
                selectColumns.size(), aggRequiredColumns.size(),
                originalJoinCount, originalJoinCount, true);
    }

    /**
     * 生成精简的内部 SQL
     * <p>
     * 直接在原始 query 上替换 SELECT 列，保留原有的 FROM/JOIN/WHERE/GROUP 结构。
     * 这样可以避免重建 JOIN 时嵌套维度 ON 条件丢失的问题。
     * </p>
     */
    private String buildOptimizedInnerSql(List<DbColumn> requiredColumns) {
        // 保存原始 SELECT
        JdbcQuery.JdbcSelect originalSelect = originalQuery.getSelect();

        // 替换为精简的 SELECT
        originalQuery.getSelect().setColumns(requiredColumns);

        // 生成 SQL
        SimpleSqlJdbcQueryVisitor visitor = new SimpleSqlJdbcQueryVisitor(
                systemBundlesContext.getApplicationContext(), jdbcQueryModel, null);
        originalQuery.accept(visitor);
        String optimizedInnerSql = visitor.getSqlWithoutOrder();

        // 恢复原始 SELECT（因为原始 query 可能还会被使用）
        originalQuery.getSelect().setColumns(originalSelect.getColumns());

        return optimizedInnerSql;
    }

    /**
     * 基于内部SQL构建聚合SQL
     */
    private String buildAggSqlFromInner(String innerSql, List<DbColumn> columns, boolean countToSum) {
        SqlQueryObject sqlQueryObject = new SqlQueryObject(innerSql, "tx");
        List<String> aggExpressions = new ArrayList<>();

        for (DbColumn column : columns) {
            String alias = column.getAlias();
            String colRef = "tx." + alias;

            // 对于 AggregationDbColumn，检查 groupByName 来判断是聚合列还是分组列
            if (column instanceof AggregationDbColumn) {
                AggregationDbColumn aggColumn = (AggregationDbColumn) column;
                if (aggColumn.getGroupByName() == null) {
                    // 这是一个聚合列，需要从 declare 中提取聚合类型
                    String declare = aggColumn.getDeclare();
                    // declare 格式如: SUM(a1.quantity), AVG(a1.price) 等
                    // 我们需要替换其中的表别名为 tx
                    String aggExpr = replaceTableAliasForAgg(declare, colRef, alias);
                    aggExpressions.add(aggExpr);
                } else {
                    // 这是一个分组列，在外层聚合时返回 null
                    aggExpressions.add("null `" + alias + "`");
                }
                continue;
            }

            // 普通列，检查 getAggregation()
            DbAggregation agg = column.getAggregation();
            if (agg == null) {
                agg = DbAggregation.NONE;
            }

            switch (agg) {
                case AVG:
                    aggExpressions.add("avg(" + colRef + ") `" + alias + "`");
                    break;
                case SUM:
                    aggExpressions.add("sum(" + colRef + ") `" + alias + "`");
                    break;
                case COUNT:
                    if (countToSum) {
                        aggExpressions.add("sum(" + colRef + ") `" + alias + "`");
                    } else {
                        aggExpressions.add("count(*) `" + alias + "`");
                    }
                    break;
                case MAX:
                    aggExpressions.add("max(" + colRef + ") `" + alias + "`");
                    break;
                case MIN:
                    aggExpressions.add("min(" + colRef + ") `" + alias + "`");
                    break;
                case NONE:
                default:
                    // NONE 不做聚合，返回 null
                    aggExpressions.add("null `" + alias + "`");
                    break;
            }
        }

        // 添加 count(*) as total
        aggExpressions.add("count(*) `total`");

        StringBuilder sb = new StringBuilder();
        sb.append("select ");
        sb.append(String.join(",\n       ", aggExpressions));
        sb.append("\nfrom (").append(innerSql).append(") tx");

        return sb.toString();
    }

    /**
     * 为聚合表达式替换表别名
     * <p>
     * 将 "SUM(a1.quantity)" 转换为 "sum(tx.quantity) `quantity`"
     * </p>
     */
    private String replaceTableAliasForAgg(String declare, String colRef, String alias) {
        String lowerDeclare = declare.toLowerCase();

        // 检测聚合函数类型
        if (lowerDeclare.startsWith("sum(")) {
            return "sum(" + colRef + ") `" + alias + "`";
        } else if (lowerDeclare.startsWith("avg(")) {
            return "avg(" + colRef + ") `" + alias + "`";
        } else if (lowerDeclare.startsWith("count(")) {
            return "sum(" + colRef + ") `" + alias + "`"; // 外层用 SUM 聚合内层的 COUNT
        } else if (lowerDeclare.startsWith("max(")) {
            return "max(" + colRef + ") `" + alias + "`";
        } else if (lowerDeclare.startsWith("min(")) {
            return "min(" + colRef + ") `" + alias + "`";
        } else if (lowerDeclare.startsWith("group_concat(")) {
            return "group_concat(" + colRef + ") `" + alias + "`";
        }

        // 默认使用 SUM
        return "sum(" + colRef + ") `" + alias + "`";
    }

    /**
     * 计算原始查询的 JOIN 数量
     */
    private int countJoins() {
        return countJoins(originalQuery);
    }

    private int countJoins(JdbcQuery query) {
        int count = 0;
        JdbcQuery.JdbcFrom from = query.getFrom();
        if (from != null) {
            if (from.getPreJoins() != null) {
                count += from.getPreJoins().size();
            }
            if (from.getJoins() != null) {
                count += from.getJoins().size();
            }
        }
        return count;
    }

    /**
     * 判断列是否为聚合列
     * <p>
     * 判断逻辑：
     * <ul>
     *   <li>AggregationDbColumn 且 groupByName == null 表示是聚合列（SUM/AVG/COUNT/MAX/MIN等）</li>
     *   <li>AggregationDbColumn 且 groupByName != null 表示是分组列</li>
     *   <li>普通 DbColumn 且 getAggregation() != null && != NONE 表示是聚合列</li>
     * </ul>
     * </p>
     *
     * @param column 数据库列
     * @return true 如果是聚合列
     */
    private boolean isAggregateColumn(DbColumn column) {
        // 检查 AggregationDbColumn
        if (column instanceof AggregationDbColumn) {
            AggregationDbColumn aggColumn = (AggregationDbColumn) column;
            // groupByName == null 表示这是一个聚合列（SUM/AVG/COUNT等）
            // groupByName != null 表示这是一个分组列
            return aggColumn.getGroupByName() == null;
        }

        // 普通列，检查 getAggregation()
        DbAggregation agg = column.getAggregation();
        return agg != null && agg != DbAggregation.NONE;
    }
}
