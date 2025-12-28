package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 自动 GroupBy 处理步骤
 * <p>
 * 根据 {@link InlineExpressionPreprocessStep} 识别的聚合信息，自动构建 groupBy。
 * 此功能始终启用（autoGroupBy 参数已废弃）。
 * </p>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>根据 {@link ModelResultContext.ParsedInlineExpressions#getColumnAggregations()} 构建 groupBy 列表</li>
 *   <li>校验 orderBy 字段：存在 GROUP BY 时，ORDER BY 字段必须在 SELECT 中，否则警告并忽略</li>
 * </ol>
 *
 * <h3>处理规则</h3>
 * <ol>
 *   <li>如果 columnAggregations 为空（无聚合列），不处理</li>
 *   <li>非聚合列自动加入 groupBy（不带 agg）</li>
 *   <li>聚合列加入 groupBy 并带上 agg 标记</li>
 *   <li>移除不在 SELECT 中的 orderBy 字段（警告）</li>
 * </ol>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@Component
@Order(10)  // 在 InlineExpressionPreprocessStep(5) 之后执行
public class AutoGroupByStep implements DataSetResultStep {

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();

        List<String> columns = queryRequest.getColumns();
        if (columns == null || columns.isEmpty()) {
            return CONTINUE;
        }

        // 获取预处理结果
        ModelResultContext.ParsedInlineExpressions parsed = ctx.getParsedInlineExpressions();

        // 如果没有预处理结果或没有聚合列，不处理
        if (parsed == null || !parsed.hasAggregation()) {
            if (log.isDebugEnabled()) {
                log.debug("autoGroupBy: 未检测到聚合列，跳过处理");
            }
            return CONTINUE;
        }

        Map<String, String> columnAggregations = parsed.getColumnAggregations();

        // 构建新的 groupBy 列表
        List<GroupRequestDef> newGroupBy = buildAutoGroupBy(
                queryRequest.getGroupBy(),
                columns,
                columnAggregations
        );

        queryRequest.setGroupBy(newGroupBy);

        if (log.isDebugEnabled()) {
            log.debug("autoGroupBy: 自动补充 groupBy，结果: {}", newGroupBy);
        }

        // 校验并清理 orderBy（存在 GROUP BY 时，ORDER BY 字段必须在 SELECT 中）
        validateAndCleanOrderBy(queryRequest, columns);

        return CONTINUE;
    }

    /**
     * 构建自动补充后的 groupBy 列表
     */
    private List<GroupRequestDef> buildAutoGroupBy(
            List<GroupRequestDef> originalGroupBy,
            List<String> columns,
            Map<String, String> columnAggregations) {

        // 收集原有 groupBy 中的字段名
        Set<String> existingGroupByFields = new HashSet<>();
        List<GroupRequestDef> newGroupBy = new ArrayList<>();

        if (originalGroupBy != null) {
            for (GroupRequestDef g : originalGroupBy) {
                existingGroupByFields.add(g.getField());
                newGroupBy.add(g);
            }
        }

        // 遍历 columns，构建 groupBy（包含聚合列和非聚合列）
        for (String columnName : columns) {
            if (existingGroupByFields.contains(columnName)) {
                continue;
            }

            GroupRequestDef group = new GroupRequestDef();
            group.setField(columnName);

            String aggType = columnAggregations.get(columnName);
            if (aggType != null) {
                // 聚合列，设置 agg 字段（稍后由 SimpleSqlJdbcQueryVisitor 根据 groupByName 过滤）
                group.setAgg(aggType);
                if (log.isDebugEnabled()) {
                    log.debug("autoGroupBy: 添加聚合列 '{}' (agg={}) 到 groupBy（仅用于元数据，不会出现在 SQL GROUP BY）", columnName, aggType);
                }
            } else {
                // 非聚合列，不设置 agg
                if (log.isDebugEnabled()) {
                    log.debug("autoGroupBy: 添加非聚合列 '{}' 到 GROUP BY", columnName);
                }
            }

            newGroupBy.add(group);
            existingGroupByFields.add(columnName);
        }

        return newGroupBy;
    }

    /**
     * 校验并清理 orderBy 字段
     * <p>
     * 存在 GROUP BY 时，ORDER BY 字段必须在 SELECT 列中，否则会导致 SQL 错误。
     * 对于不在 SELECT 中的 orderBy 字段，记录警告并移除。
     * </p>
     *
     * @param queryRequest 查询请求
     * @param columns      SELECT 列名列表
     */
    private void validateAndCleanOrderBy(DbQueryRequestDef queryRequest, List<String> columns) {
        List<OrderRequestDef> orderBy = queryRequest.getOrderBy();
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }

        // 构建 SELECT 列名集合（用于快速查找）
        Set<String> selectColumns = new HashSet<>(columns);

        // 检查并移除不合法的 orderBy 字段
        List<OrderRequestDef> validOrderBy = new ArrayList<>();
        List<String> removedFields = new ArrayList<>();

        for (OrderRequestDef order : orderBy) {
            String field = order.getField();
            if (selectColumns.contains(field)) {
                validOrderBy.add(order);
            } else {
                removedFields.add(field);
            }
        }

        // 如果有字段被移除，记录警告并更新 orderBy
        if (!removedFields.isEmpty()) {
            log.warn("autoGroupBy: orderBy 字段 {} 不在 SELECT 列中，已忽略（存在 GROUP BY 时，ORDER BY 字段必须在 SELECT 中）",
                    removedFields);
            queryRequest.setOrderBy(validOrderBy.isEmpty() ? null : validOrderBy);
        }
    }
}
