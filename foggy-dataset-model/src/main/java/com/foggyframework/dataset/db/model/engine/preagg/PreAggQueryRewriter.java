package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预聚合查询重写器
 * <p>
 * 将原始查询重写为使用预聚合表的查询。
 * </p>
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>单表模式：仅使用预聚合表</li>
 *   <li>混合模式：预聚合表 UNION 原始表（Lambda 架构）</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggQueryRewriter {

    private final JdbcQueryModel queryModel;
    private final ApplicationContext applicationContext;

    public PreAggQueryRewriter(JdbcQueryModel queryModel, ApplicationContext applicationContext) {
        this.queryModel = queryModel;
        this.applicationContext = applicationContext;
    }

    /**
     * 重写查询以使用预聚合表
     *
     * @param matchResult  匹配结果
     * @param jdbcQuery    原始 JdbcQuery
     * @param queryRequest 查询请求
     * @param queryEngine  查询引擎（用于获取原始 SQL 参数）
     * @return 重写结果
     */
    public PreAggRewriteResult rewrite(PreAggregationMatchResult matchResult,
                                        JdbcQuery jdbcQuery,
                                        DbQueryRequestDef queryRequest,
                                        JdbcModelQueryEngine queryEngine) {
        if (!matchResult.isMatched()) {
            return PreAggRewriteResult.notApplied();
        }

        PreAggregation preAgg = matchResult.getPreAggregation();
        boolean needsRollup = matchResult.isNeedsRollup();
        boolean isHybrid = matchResult.isHybridQuery();

        try {
            String sql;
            List<Object> params;

            if (isHybrid) {
                // 混合查询模式：UNION SQL
                Object watermark = matchResult.getWatermark();
                sql = buildHybridSql(preAgg, jdbcQuery, queryRequest, queryEngine, watermark);
                params = buildHybridParams(queryEngine.getValues(), watermark);

                if (log.isInfoEnabled()) {
                    log.info("Rewrote query to HYBRID mode using pre-aggregation '{}', watermark={}",
                            preAgg.getName(), watermark);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Hybrid SQL: {}", sql);
                }

                return PreAggRewriteResult.hybrid(preAgg, sql, params, needsRollup, watermark);
            } else {
                // 单表模式：仅预聚合表
                sql = buildPreAggSql(preAgg, jdbcQuery, queryRequest, needsRollup);
                params = queryEngine.getValues();

                if (log.isInfoEnabled()) {
                    log.info("Rewrote query to use pre-aggregation '{}', needsRollup={}", preAgg.getName(), needsRollup);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Pre-aggregation SQL: {}", sql);
                }

                return PreAggRewriteResult.applied(preAgg, sql, params, needsRollup);
            }
        } catch (Exception e) {
            log.warn("Failed to rewrite query for pre-aggregation '{}': {}",
                    preAgg.getName(), e.getMessage(), e);
            return PreAggRewriteResult.notApplied();
        }
    }

    /**
     * 构建混合查询 SQL（Lambda 架构）
     * <p>
     * 生成的 SQL 结构：
     * <pre>
     * SELECT [columns with aggregation]
     * FROM (
     *   SELECT [columns] FROM preagg_table WHERE watermark_col <= ?
     *   UNION ALL
     *   SELECT [columns] FROM source_table WHERE watermark_col > ?
     * ) AS combined
     * GROUP BY [dimension columns]
     * ORDER BY ...
     * </pre>
     * </p>
     */
    private String buildHybridSql(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                   DbQueryRequestDef queryRequest, JdbcModelQueryEngine queryEngine,
                                   Object watermark) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String sourceTableName = queryModel.getJdbcModel().getTableName();
        String watermarkColumn = parseWatermarkColumn(preAgg.getWatermarkColumn());

        // 外层 SELECT（带聚合函数）
        List<String> outerSelectColumns = buildOuterSelectColumns(preAgg, jdbcQuery, "combined");
        List<String> groupByColumns = buildGroupByColumns(preAgg, jdbcQuery, "combined");

        sql.append("SELECT ");
        sql.append(String.join(", ", outerSelectColumns));
        sql.append(" FROM (");

        // 内层 UNION: 预聚合表部分
        sql.append(" SELECT ");
        List<String> preAggInnerColumns = buildInnerSelectColumns(preAgg, jdbcQuery, "pa", true);
        sql.append(String.join(", ", preAggInnerColumns));
        sql.append(" FROM ").append(preAggTableName).append(" pa");
        sql.append(" WHERE ").append("pa.").append(watermarkColumn).append(" <= ?");

        // UNION ALL
        sql.append(" UNION ALL ");

        // 内层 UNION: 原始表部分
        sql.append("SELECT ");
        List<String> sourceInnerColumns = buildInnerSelectColumns(preAgg, jdbcQuery, "src", false);
        sql.append(String.join(", ", sourceInnerColumns));
        sql.append(" FROM ").append(sourceTableName).append(" src");
        sql.append(" WHERE ").append("src.").append(watermarkColumn).append(" > ?");

        // 添加原始查询的 WHERE 条件（如果有）
        String originalWhere = buildOriginalWhereClause(jdbcQuery, "src");
        if (originalWhere != null && !originalWhere.isEmpty()) {
            sql.append(" AND ").append(originalWhere);
        }

        sql.append(") AS combined");

        // GROUP BY（混合查询必须聚合）
        if (!groupByColumns.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
        }

        // ORDER BY
        String orderByClause = buildOrderByClause(jdbcQuery, "combined");
        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return sql.toString();
    }

    /**
     * 构建混合查询参数
     * <p>
     * 参数顺序：watermark（用于预聚合表）、watermark（用于原始表）、原始查询参数
     * </p>
     */
    private List<Object> buildHybridParams(List<Object> originalParams, Object watermark) {
        List<Object> params = new ArrayList<>();
        params.add(watermark);  // 预聚合表 WHERE
        params.add(watermark);  // 原始表 WHERE
        if (originalParams != null) {
            params.addAll(originalParams);
        }
        return params;
    }

    /**
     * 构建外层 SELECT 列（带聚合函数）
     */
    private List<String> buildOuterSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                // 度量列：聚合
                DbAggregation agg = measureAggregations.get(columnName);
                String aggFunc = getAggregationFunction(agg);
                columns.add(aggFunc + "(" + alias + "." + columnAlias + ") AS " + columnAlias);
            } else {
                // 维度列：直接引用
                columns.add(alias + "." + columnAlias + " AS " + columnAlias);
            }
        }

        return columns;
    }

    /**
     * 构建内层 SELECT 列（不带聚合函数）
     */
    private List<String> buildInnerSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                                  String alias, boolean isPreAggTable) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                if (isPreAggTable) {
                    // 预聚合表：使用映射的列名
                    String preAggColumnName = measureColumnNames.get(columnName);
                    if (preAggColumnName == null) {
                        preAggColumnName = columnName + "_sum";
                    }
                    columns.add(alias + "." + preAggColumnName + " AS " + columnAlias);
                } else {
                    // 原始表：直接使用度量列
                    String sqlColumnName = getSqlColumnName(column);
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                }
            } else {
                // 维度/属性列
                String sqlColumnName = getSqlColumnName(column);
                columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
            }
        }

        return columns;
    }

    /**
     * 解析水位线列名
     */
    private String parseWatermarkColumn(String watermarkColumn) {
        if (watermarkColumn == null) {
            return "created_at"; // 默认
        }
        // 格式：dimensionName$propertyName 或 dimensionName$id
        int dollarIndex = watermarkColumn.indexOf('$');
        if (dollarIndex > 0) {
            return watermarkColumn.substring(0, dollarIndex);
        }
        return watermarkColumn;
    }

    /**
     * 构建原始 WHERE 条件（用于混合查询的原始表部分）
     */
    private String buildOriginalWhereClause(JdbcQuery jdbcQuery, String alias) {
        // 简化实现：暂时不处理复杂的 WHERE 条件
        // TODO: 实现完整的 WHERE 条件转换
        return "";
    }

    // ==================== 单表模式方法（原有实现） ====================

    /**
     * 构建使用预聚合表的 SQL
     */
    private String buildPreAggSql(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                   DbQueryRequestDef queryRequest, boolean needsRollup) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String alias = "pa"; // 预聚合表别名

        // SELECT 子句
        sql.append("SELECT ");
        List<String> selectColumns = buildSelectColumns(preAgg, jdbcQuery, alias, needsRollup);
        sql.append(String.join(", ", selectColumns));

        // FROM 子句
        sql.append(" FROM ").append(preAggTableName).append(" ").append(alias);

        // WHERE 子句（从原始查询中提取适用的条件）
        String whereClause = buildWhereClause(preAgg, jdbcQuery, alias);
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }

        // GROUP BY 子句（如果需要 rollup）
        if (needsRollup) {
            List<String> groupByColumns = buildGroupByColumns(preAgg, jdbcQuery, alias);
            if (!groupByColumns.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
            }
        }

        // ORDER BY 子句
        String orderByClause = buildOrderByClause(jdbcQuery, alias);
        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return sql.toString();
    }

    /**
     * 获取预聚合表的完整表名（包括 schema）
     */
    private String getFullTableName(PreAggregation preAgg) {
        String schema = preAgg.getSchema();
        String tableName = preAgg.getTableName();

        if (schema != null && !schema.isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }

    /**
     * 构建 SELECT 列
     */
    private List<String> buildSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                             String alias, boolean needsRollup) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();
        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                // 度量列：从预聚合表中获取对应的列名
                String preAggColumnName = measureColumnNames.get(columnName);
                if (preAggColumnName == null) {
                    preAggColumnName = columnName + "_sum"; // 默认命名
                }

                if (needsRollup) {
                    // 需要 rollup：根据聚合类型包装
                    DbAggregation agg = measureAggregations.get(columnName);
                    String aggFunc = getAggregationFunction(agg);
                    columns.add(aggFunc + "(" + alias + "." + preAggColumnName + ") AS " + columnAlias);
                } else {
                    // 不需要 rollup：直接使用
                    columns.add(alias + "." + preAggColumnName + " AS " + columnAlias);
                }
            } else {
                // 维度/属性列：直接映射
                String sqlColumnName = getSqlColumnName(column);
                if (needsRollup) {
                    // rollup 时维度列可能需要聚合处理
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                } else {
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                }
            }
        }

        return columns;
    }

    /**
     * 获取聚合函数名
     */
    private String getAggregationFunction(DbAggregation agg) {
        if (agg == null) {
            return "SUM";
        }
        switch (agg) {
            case SUM:
            case COUNT:  // COUNT 在 rollup 时变成 SUM
                return "SUM";
            case MIN:
                return "MIN";
            case MAX:
                return "MAX";
            case AVG:
                return "AVG";
            default:
                return "SUM";
        }
    }

    /**
     * 获取列的 SQL 列名
     */
    private String getSqlColumnName(DbColumn column) {
        if (column.getSqlColumn() != null) {
            return column.getSqlColumn().getName();
        }
        return column.getAlias();
    }

    /**
     * 构建 WHERE 子句
     * <p>
     * 从原始查询的 WHERE 条件中提取适用于预聚合表的条件。
     * 注意：预聚合表可能没有所有原始表的列，需要过滤。
     * </p>
     */
    private String buildWhereClause(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        // 简化实现：暂时不处理复杂的 WHERE 条件重写
        // 完整实现需要遍历 jdbcQuery.getWhere() 并转换条件
        // TODO: 实现完整的 WHERE 条件重写
        return "";
    }

    /**
     * 构建 GROUP BY 列（用于 rollup）
     */
    private List<String> buildGroupByColumns(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        List<String> groupByColumns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return groupByColumns;
        }

        for (DbColumn column : select.getColumns()) {
            // 只有维度/属性列需要 GROUP BY
            if (column.isDimension() || column.isProperty()) {
                String columnAlias = column.getAlias();
                groupByColumns.add(alias + "." + columnAlias);
            }
        }

        return groupByColumns;
    }

    /**
     * 构建 ORDER BY 子句
     */
    private String buildOrderByClause(JdbcQuery jdbcQuery, String alias) {
        JdbcQuery.JdbcOrder order = jdbcQuery.getOrder();
        if (order == null || order.getOrders() == null || order.getOrders().isEmpty()) {
            return "";
        }

        List<String> orderParts = new ArrayList<>();
        for (var orderColumn : order.getOrders()) {
            DbColumn column = orderColumn.getSelectColumn();
            String columnAlias = column.getAlias();
            String direction = orderColumn.getOrder();
            orderParts.add(alias + "." + columnAlias + " " + direction);
        }

        return String.join(", ", orderParts);
    }
}
