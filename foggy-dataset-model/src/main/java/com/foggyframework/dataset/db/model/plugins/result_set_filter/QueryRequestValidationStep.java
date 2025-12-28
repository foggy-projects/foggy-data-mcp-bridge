package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.spi.DbAggregation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 查询请求参数校验步骤
 * <p>
 * 在查询执行前校验请求参数的完整性和合法性，确保：
 * <ol>
 *   <li>slice 条件的 field、op、value 字段不为空</li>
 *   <li>op 操作符是系统支持的（通过 SqlFormulaService 获取）</li>
 *   <li>groupBy 的 field、agg 字段不为空，agg 类型合法</li>
 *   <li>orderBy 的 field、dir 字段不为空，dir 只能是 asc/desc</li>
 * </ol>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@Component
@Order(0)  // 最先执行，确保后续步骤接收的是合法参数
public class QueryRequestValidationStep implements DataSetResultStep {

    @Resource
    private SqlFormulaService sqlFormulaService;

    /**
     * 支持的聚合类型（来自 DbAggregation 枚举）
     */
    private static final Set<String> SUPPORTED_AGG_TYPES = Arrays.stream(DbAggregation.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    /**
     * 支持的排序方向
     */
    private static final Set<String> SUPPORTED_SORT_DIRECTIONS = Set.of("asc", "desc");

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();

        if (log.isDebugEnabled()) {
            log.debug("=== Query Request Validation Start ===");
        }

        // 1. 校验 slice 条件
        validateSlice(queryRequest.getSlice());

        // 2. 校验 groupBy
        validateGroupBy(queryRequest.getGroupBy());

        // 3. 校验 orderBy
        validateOrderBy(queryRequest.getOrderBy());

        if (log.isDebugEnabled()) {
            log.debug("=== Query Request Validation Passed ===");
        }

        return CONTINUE;
    }

    /**
     * 校验查询条件（递归处理嵌套条件）
     */
    private void validateSlice(List<SliceRequestDef> sliceList) {
        if (sliceList == null || sliceList.isEmpty()) {
            return;
        }

        for (int i = 0; i < sliceList.size(); i++) {
            validateSliceItem(sliceList.get(i), i);
        }
    }

    /**
     * 校验单个查询条件项
     */
    private void validateSliceItem(SliceRequestDef item, int index) {
        // 1. 校验 field 不为空
        if (StringUtils.isEmpty(item.getField())) {
            throw RX.throwAUserTip(DatasetMessages.validationSliceFieldRequired(index));
        }

        String field = item.getField();

        // 2. 如果有 children，递归校验（OR 逻辑组）
        if (item.getChildren() != null && !item.getChildren().isEmpty()) {
            validateCondChildren(item.getChildren());
            return;  // children 存在时，本级不需要 op 和 value
        }

        // 3. 校验 op 不为空
        if (StringUtils.isEmpty(item.getOp())) {
            throw RX.throwAUserTip(DatasetMessages.validationSliceOpRequired(index, field));
        }

        String op = item.getOp();

        // 4. 校验 op 是否合法（通过 SqlFormulaService 检查）
        if (!isValidOperator(op)) {
            String supportedOps = getSupportedOperators();
            throw RX.throwAUserTip(DatasetMessages.validationSliceOpInvalid(index, field, op, supportedOps));
        }

        // 5. 校验 value 不为空（空值操作符除外）
        if (!isNullValueOperator(op) && isEmpty(item.getValue())) {
            throw RX.throwAUserTip(DatasetMessages.validationSliceValueRequired(index, field, op));
        }
    }

    /**
     * 校验嵌套条件（children 是 CondRequestDef 类型）
     */
    private void validateCondChildren(List<CondRequestDef> children) {
        if (children == null || children.isEmpty()) {
            return;
        }

        for (int i = 0; i < children.size(); i++) {
            CondRequestDef item = children.get(i);

            // 1. 校验 field 不为空
            if (StringUtils.isEmpty(item.getField())) {
                throw RX.throwAUserTip(DatasetMessages.validationSliceFieldRequired(i));
            }

            String field = item.getField();

            // 2. 如果有 children，递归校验
            if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                validateCondChildren(item.getChildren());
                continue;
            }

            // 3. 校验 op 不为空
            if (StringUtils.isEmpty(item.getOp())) {
                throw RX.throwAUserTip(DatasetMessages.validationSliceOpRequired(i, field));
            }

            String op = item.getOp();

            // 4. 校验 op 是否合法
            if (!isValidOperator(op)) {
                String supportedOps = getSupportedOperators();
                throw RX.throwAUserTip(DatasetMessages.validationSliceOpInvalid(i, field, op, supportedOps));
            }

            // 5. 校验 value 不为空（空值操作符除外）
            if (!isNullValueOperator(op) && isEmpty(item.getValue())) {
                throw RX.throwAUserTip(DatasetMessages.validationSliceValueRequired(i, field, op));
            }
        }
    }

    /**
     * 校验分组字段
     */
    private void validateGroupBy(List<GroupRequestDef> groupByList) {
        if (groupByList == null || groupByList.isEmpty()) {
            return;
        }

        for (int i = 0; i < groupByList.size(); i++) {
            GroupRequestDef item = groupByList.get(i);

            // 1. 校验 field 不为空
            if (StringUtils.isEmpty(item.getField())) {
                throw RX.throwAUserTip(DatasetMessages.validationGroupByFieldRequired(i));
            }

            String field = item.getField();

            // 2. 如果指定了 agg，则校验其合法性
            if (StringUtils.isNotEmpty(item.getAgg())) {
                String agg = item.getAgg().toUpperCase();

                if (!SUPPORTED_AGG_TYPES.contains(agg)) {
                    String supportedAggs = String.join(", ", SUPPORTED_AGG_TYPES);
                    throw RX.throwAUserTip(DatasetMessages.validationGroupByAggInvalid(i, field, agg, supportedAggs));
                }
            }
        }
    }

    /**
     * 校验排序字段
     */
    private void validateOrderBy(List<OrderRequestDef> orderByList) {
        if (orderByList == null || orderByList.isEmpty()) {
            return;
        }

        for (int i = 0; i < orderByList.size(); i++) {
            OrderRequestDef item = orderByList.get(i);

            // 1. 校验 field 不为空
            if (StringUtils.isEmpty(item.getField())) {
                throw RX.throwAUserTip(DatasetMessages.validationOrderByFieldRequired(i));
            }

            String field = item.getField();

            // 2. 校验 order 不为空
            if (StringUtils.isEmpty(item.getOrder())) {
                throw RX.throwAUserTip(DatasetMessages.validationOrderByDirRequired(i, field));
            }

            String order = item.getOrder().toLowerCase();

            // 3. 校验 order 只能是 asc 或 desc
            if (!SUPPORTED_SORT_DIRECTIONS.contains(order)) {
                throw RX.throwAUserTip(DatasetMessages.validationOrderByDirInvalid(i, field, order));
            }
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 检查操作符是否合法（通过 SqlFormulaService）
     */
    private boolean isValidOperator(String op) {
        return sqlFormulaService.supports(op);
    }

    /**
     * 获取所有支持的操作符列表（用于错误提示）
     */
    private String getSupportedOperators() {
        return "=, !=, ===, >, >=, <, <=, in, !in, like, !like, [], [), (], (), null, !null, null|empty, !null&!empty, bit_in";
    }

    /**
     * 检查是否为空值操作符（这些操作符不需要 value）
     */
    private boolean isNullValueOperator(String op) {
        return "null".equals(op) || "!null".equals(op) ||
               "null|empty".equals(op) || "!null&!empty".equals(op);
    }

    /**
     * 检查值是否为空
     */
    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        return false;
    }
}
