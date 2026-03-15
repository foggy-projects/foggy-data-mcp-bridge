package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 分组小计与总计行追加步骤
 *
 * <p>当查询请求启用 {@code withSubtotals=true} 时，在聚合查询结果中：
 * <ul>
 *   <li>按第一维度分组，在每组末尾追加小计行</li>
 *   <li>在所有数据末尾追加总计行</li>
 *   <li>通过 {@code _rowType} 字段标记行类型：data / subtotal / grandTotal</li>
 * </ul>
 *
 * <p>仅对有 groupBy 且包含至少两个维度的聚合查询生效。
 * 单维度分组时仅追加总计行。
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
public class SubtotalStep implements DataSetResultStep {

    private static final String ROW_TYPE_FIELD = "_rowType";
    private static final String ROW_TYPE_DATA = "data";
    private static final String ROW_TYPE_SUBTOTAL = "subtotal";
    private static final String ROW_TYPE_GRAND_TOTAL = "grandTotal";

    @Override
    public int process(ModelResultContext ctx) {
        DbQueryRequestDef requestDef = ctx.getRequest().getParam();

        // 前置检查：未启用、无 groupBy、无数据 → 跳过
        if (!requestDef.isWithSubtotals()) {
            return CONTINUE;
        }
        if (!requestDef.hasGroupBy()) {
            return CONTINUE;
        }
        if (ctx.getPagingResult() == null || ctx.getPagingResult().isEmpty()) {
            return CONTINUE;
        }

        try {
            appendSubtotals(ctx, requestDef);
        } catch (Exception e) {
            log.warn("SubtotalStep 处理失败，跳过小计生成: {}", e.getMessage());
            // 非致命错误，不影响原始查询结果
        }

        return CONTINUE;
    }

    @Override
    public int order() {
        // 在大部分后处理之后执行（越小越靠后）
        return -200;
    }

    @SuppressWarnings("unchecked")
    private void appendSubtotals(ModelResultContext ctx, DbQueryRequestDef requestDef) {
        List<GroupRequestDef> groupByList = requestDef.getGroupBy();

        // 分离维度列和度量列
        List<String> dimensionFields = new ArrayList<>();
        Map<String, String> measureAggs = new LinkedHashMap<>(); // field -> aggType

        for (GroupRequestDef g : groupByList) {
            if (g.getAgg() != null && !g.getAgg().isEmpty()) {
                measureAggs.put(g.getField(), g.getAgg().toUpperCase());
            } else {
                dimensionFields.add(g.getField());
            }
        }

        // 也从 parsedInlineExpressions 中获取聚合信息（内联表达式场景）
        if (ctx.getParsedInlineExpressions() != null
                && ctx.getParsedInlineExpressions().getColumnAggregations() != null) {
            Map<String, String> colAggs = ctx.getParsedInlineExpressions().getColumnAggregations();
            for (Map.Entry<String, String> entry : colAggs.entrySet()) {
                if (!measureAggs.containsKey(entry.getKey())) {
                    measureAggs.put(entry.getKey(), entry.getValue().toUpperCase());
                }
            }
        }

        if (measureAggs.isEmpty()) {
            log.debug("无度量列，跳过小计生成");
            return;
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) ctx.getPagingResult().getItems();
        List<Map<String, Object>> result = new ArrayList<>(items.size() + items.size() / 3 + 1);

        // 标记所有原始行
        for (Map<String, Object> row : items) {
            row.put(ROW_TYPE_FIELD, ROW_TYPE_DATA);
            result.add(row);
        }

        // 多维度时：按第一维度分组追加小计行
        if (dimensionFields.size() >= 2) {
            result = insertGroupSubtotals(result, dimensionFields, measureAggs);
        }

        // 总计行
        Map<String, Object> grandTotal = buildAggregateRow(items, dimensionFields, measureAggs, ROW_TYPE_GRAND_TOTAL);
        // 总计行维度列显示标签
        if (!dimensionFields.isEmpty()) {
            grandTotal.put(dimensionFields.get(0), DatasetMessages.getMessage("subtotal.grand.total"));
            for (int i = 1; i < dimensionFields.size(); i++) {
                grandTotal.put(dimensionFields.get(i), "");
            }
        }
        result.add(grandTotal);

        ctx.getPagingResult().setItems(result);
    }

    /**
     * 按第一维度分组，在每组末尾插入小计行
     */
    private List<Map<String, Object>> insertGroupSubtotals(
            List<Map<String, Object>> rows,
            List<String> dimensionFields,
            Map<String, String> measureAggs) {

        String primaryDim = dimensionFields.get(0);
        String subtotalLabel = DatasetMessages.getMessage("subtotal.sub.total");
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> currentGroup = new ArrayList<>();
        Object currentGroupValue = null;
        boolean firstGroup = true;

        for (Map<String, Object> row : rows) {
            if (!ROW_TYPE_DATA.equals(row.get(ROW_TYPE_FIELD))) {
                result.add(row);
                continue;
            }

            Object groupValue = row.get(primaryDim);
            if (!firstGroup && !Objects.equals(groupValue, currentGroupValue)) {
                // 组切换，插入前一组的小计
                result.add(buildSubtotalRow(currentGroup, dimensionFields, measureAggs, primaryDim, currentGroupValue, subtotalLabel));
                currentGroup.clear();
            }

            firstGroup = false;
            currentGroupValue = groupValue;
            currentGroup.add(row);
            result.add(row);
        }

        // 最后一组的小计
        if (!currentGroup.isEmpty()) {
            result.add(buildSubtotalRow(currentGroup, dimensionFields, measureAggs, primaryDim, currentGroupValue, subtotalLabel));
        }

        return result;
    }

    /**
     * 构建一个小计行（含维度标签填充）
     */
    private Map<String, Object> buildSubtotalRow(
            List<Map<String, Object>> groupRows,
            List<String> dimensionFields,
            Map<String, String> measureAggs,
            String primaryDim, Object primaryDimValue,
            String subtotalLabel) {

        Map<String, Object> subtotal = buildAggregateRow(groupRows, dimensionFields, measureAggs, ROW_TYPE_SUBTOTAL);
        subtotal.put(primaryDim, primaryDimValue);
        for (int i = 1; i < dimensionFields.size(); i++) {
            subtotal.put(dimensionFields.get(i), subtotalLabel);
        }
        return subtotal;
    }

    /**
     * 根据聚合类型计算汇总行
     */
    private Map<String, Object> buildAggregateRow(
            List<Map<String, Object>> rows,
            List<String> dimensionFields,
            Map<String, String> measureAggs,
            String rowType) {

        Map<String, Object> aggRow = new LinkedHashMap<>();
        aggRow.put(ROW_TYPE_FIELD, rowType);

        for (Map.Entry<String, String> entry : measureAggs.entrySet()) {
            String field = entry.getKey();
            String agg = entry.getValue();
            Object value = aggregate(rows, field, agg);
            aggRow.put(field, value);
        }

        return aggRow;
    }

    /**
     * 对一组行的某个字段执行聚合运算
     */
    private Object aggregate(List<Map<String, Object>> rows, String field, String aggType) {
        if (rows.isEmpty()) {
            return null;
        }

        switch (aggType) {
            case "SUM":
            case "COUNT":
            case "COUNTD":
            case "COUNT_DISTINCT":
                return sumValues(rows, field);

            case "AVG":
                return avgValues(rows, field);

            case "MIN":
                return minValue(rows, field);

            case "MAX":
                return maxValue(rows, field);

            default:
                // 未知聚合类型，尝试求和
                if (isNumericField(rows, field)) {
                    return sumValues(rows, field);
                }
                return "-";
        }
    }

    private Object sumValues(List<Map<String, Object>> rows, String field) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean hasValue = false;
        for (Map<String, Object> row : rows) {
            Number val = toNumber(row.get(field));
            if (val != null) {
                sum = sum.add(toBigDecimal(val));
                hasValue = true;
            }
        }
        return hasValue ? formatNumber(sum) : null;
    }

    private Object avgValues(List<Map<String, Object>> rows, String field) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Map<String, Object> row : rows) {
            Number val = toNumber(row.get(field));
            if (val != null) {
                sum = sum.add(toBigDecimal(val));
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return formatNumber(sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP));
    }

    private Object minValue(List<Map<String, Object>> rows, String field) {
        BigDecimal min = null;
        for (Map<String, Object> row : rows) {
            Number val = toNumber(row.get(field));
            if (val != null) {
                BigDecimal bd = toBigDecimal(val);
                if (min == null || bd.compareTo(min) < 0) {
                    min = bd;
                }
            }
        }
        return min != null ? formatNumber(min) : null;
    }

    private Object maxValue(List<Map<String, Object>> rows, String field) {
        BigDecimal max = null;
        for (Map<String, Object> row : rows) {
            Number val = toNumber(row.get(field));
            if (val != null) {
                BigDecimal bd = toBigDecimal(val);
                if (max == null || bd.compareTo(max) > 0) {
                    max = bd;
                }
            }
        }
        return max != null ? formatNumber(max) : null;
    }

    private boolean isNumericField(List<Map<String, Object>> rows, String field) {
        for (Map<String, Object> row : rows) {
            Object val = row.get(field);
            if (val instanceof Number) {
                return true;
            }
            if (val != null) {
                return false;
            }
        }
        return false;
    }

    private Number toNumber(Object val) {
        if (val instanceof Number) {
            return (Number) val;
        }
        return null;
    }

    private BigDecimal toBigDecimal(Number val) {
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val instanceof Long || val instanceof Integer) {
            return BigDecimal.valueOf(val.longValue());
        }
        return BigDecimal.valueOf(val.doubleValue());
    }

    /**
     * 格式化数字：整数返回 Long，小数返回 BigDecimal（保留原精度）
     */
    private Object formatNumber(BigDecimal bd) {
        if (bd.stripTrailingZeros().scale() <= 0) {
            try {
                return bd.longValueExact();
            } catch (ArithmeticException e) {
                return bd;
            }
        }
        return bd;
    }
}
