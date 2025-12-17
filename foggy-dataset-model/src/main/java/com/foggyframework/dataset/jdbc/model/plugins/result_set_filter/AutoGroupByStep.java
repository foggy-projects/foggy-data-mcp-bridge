package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.expression.InlineExpressionParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 自动 GroupBy 处理步骤
 * <p>
 * 当 {@code autoGroupBy=true} 时，自动分析 columns 中的聚合表达式，
 * 并将非聚合列自动加入 groupBy。
 * </p>
 *
 * <h3>处理规则</h3>
 * <ol>
 *   <li>只有当用户主动传递的计算字段表达式包含聚合函数时才启用</li>
 *   <li>JM 中定义的 aggType 不触发自动处理</li>
 *   <li>自动识别 columns 中的聚合表达式（如 sum(salesAmount) as totalSales）</li>
 *   <li>非聚合列自动加入 groupBy</li>
 *   <li>聚合列加入 groupBy 并带上 agg 标记</li>
 * </ol>
 *
 * <h3>依赖</h3>
 * <p>
 * 依赖 {@link InlineExpressionPreprocessStep} 在 beforeQuery 阶段先执行，
 * 通过 AST 分析设置 {@link CalculatedFieldDef#getAgg()} 字段。
 * </p>
 *
 * <h3>示例</h3>
 * <pre>
 * 输入:
 * columns: ["product$categoryName", "date", "sum(salesAmount) as salesAmount2", "orderCount"]
 * groupBy: [{"field": "product$categoryName"}]
 * autoGroupBy: true
 *
 * 输出:
 * groupBy: [
 *   {"field": "product$categoryName"},
 *   {"field": "date"},
 *   {"field": "salesAmount2", "agg": "SUM"},
 *   {"field": "orderCount"}
 * ]
 * </pre>
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
        JdbcQueryRequestDef queryRequest = ctx.getRequest().getParam();

        // 检查是否启用 autoGroupBy
        if (!queryRequest.isAutoGroupBy()) {
            return CONTINUE;
        }

        List<String> columns = queryRequest.getColumns();
        if (columns == null || columns.isEmpty()) {
            return CONTINUE;
        }

        // 获取预处理结果
        ModelResultContext.ParsedInlineExpressions parsed = ctx.getParsedInlineExpressions();

        // 分析 columns，识别聚合表达式
        ColumnAnalysisResult analysisResult = analyzeColumns(columns, queryRequest.getCalculatedFields(), parsed);

        // 如果没有检测到聚合表达式，不处理
        if (!analysisResult.hasAggregation()) {
            if (log.isDebugEnabled()) {
                log.debug("autoGroupBy: 未检测到聚合表达式，跳过处理");
            }
            return CONTINUE;
        }

        // 构建新的 groupBy 列表
        List<GroupRequestDef> newGroupBy = buildAutoGroupBy(
                queryRequest.getGroupBy(),
                analysisResult
        );

        queryRequest.setGroupBy(newGroupBy);

        if (log.isDebugEnabled()) {
            log.debug("autoGroupBy: 自动补充 groupBy，结果: {}", newGroupBy);
        }

        return CONTINUE;
    }

    /**
     * 分析 columns，识别聚合表达式和非聚合列
     * <p>
     * 聚合信息来源于 {@link CalculatedFieldDef#getAgg()} 字段，
     * 该字段由 {@link InlineExpressionPreprocessStep} 通过 AST 分析填充。
     * </p>
     */
    private ColumnAnalysisResult analyzeColumns(
            List<String> columns,
            List<CalculatedFieldDef> calculatedFields,
            ModelResultContext.ParsedInlineExpressions parsed) {

        ColumnAnalysisResult result = new ColumnAnalysisResult();

        // 构建 calculatedFields 映射（name -> def）
        Map<String, CalculatedFieldDef> calcFieldMap = new HashMap<>();
        if (calculatedFields != null) {
            for (CalculatedFieldDef def : calculatedFields) {
                calcFieldMap.put(def.getName(), def);
            }
        }

        // 如果有预处理结果，直接使用
        Map<String, InlineExpressionParser.InlineExpression> aliasToExp =
                parsed != null ? parsed.getAliasToExpression() : null;

        for (String columnDef : columns) {
            // 检查是否是 calculatedFields 中的字段（包括预处理过的内联表达式）
            CalculatedFieldDef calcDef = calcFieldMap.get(columnDef);

            if (calcDef != null && StringUtils.isNotEmpty(calcDef.getAgg())) {
                // 带 agg 标记的聚合表达式（由 InlineExpressionPreprocessStep 通过 AST 分析设置）
                result.aggregationColumns.put(columnDef, calcDef.getAgg().toUpperCase());
                if (log.isDebugEnabled()) {
                    log.debug("autoGroupBy: 识别聚合列 '{}' (agg={})", columnDef, calcDef.getAgg());
                }
            } else {
                // 普通列或非聚合的计算字段
                result.nonAggregationColumns.add(columnDef);
            }
        }

        return result;
    }

    /**
     * 构建自动补充后的 groupBy 列表
     */
    private List<GroupRequestDef> buildAutoGroupBy(
            List<GroupRequestDef> originalGroupBy,
            ColumnAnalysisResult analysisResult) {

        // 收集原有 groupBy 中的字段名
        Set<String> existingGroupByFields = new HashSet<>();
        List<GroupRequestDef> newGroupBy = new ArrayList<>();

        if (originalGroupBy != null) {
            for (GroupRequestDef g : originalGroupBy) {
                existingGroupByFields.add(g.getField());
                newGroupBy.add(g);
            }
        }

        // 添加非聚合列到 groupBy（如果尚未存在）
        for (String col : analysisResult.nonAggregationColumns) {
            if (!existingGroupByFields.contains(col)) {
                GroupRequestDef group = new GroupRequestDef();
                group.setField(col);
                newGroupBy.add(group);
                existingGroupByFields.add(col);

                if (log.isDebugEnabled()) {
                    log.debug("autoGroupBy: 自动添加非聚合列 '{}'", col);
                }
            }
        }

        // 添加聚合列到 groupBy（带 agg 标记，如果尚未存在）
        for (Map.Entry<String, String> entry : analysisResult.aggregationColumns.entrySet()) {
            String fieldName = entry.getKey();
            String aggType = entry.getValue();

            if (!existingGroupByFields.contains(fieldName)) {
                GroupRequestDef group = new GroupRequestDef();
                group.setField(fieldName);
                group.setAgg(aggType);
                newGroupBy.add(group);
                existingGroupByFields.add(fieldName);

                if (log.isDebugEnabled()) {
                    log.debug("autoGroupBy: 自动添加聚合列 '{}' (agg={})", fieldName, aggType);
                }
            }
        }

        return newGroupBy;
    }

    /**
     * 列分析结果
     */
    private static class ColumnAnalysisResult {
        /**
         * 非聚合列（需要加入 groupBy，不带 agg）
         */
        List<String> nonAggregationColumns = new ArrayList<>();

        /**
         * 聚合列（需要加入 groupBy，带 agg）
         * key: 列名/别名, value: 聚合类型 (SUM, AVG, COUNT, MAX, MIN)
         */
        Map<String, String> aggregationColumns = new LinkedHashMap<>();

        boolean hasAggregation() {
            return !aggregationColumns.isEmpty();
        }
    }
}
