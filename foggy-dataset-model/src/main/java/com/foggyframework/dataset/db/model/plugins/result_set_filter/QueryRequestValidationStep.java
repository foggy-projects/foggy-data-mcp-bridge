package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
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

    @Resource
    DatasetProperties datasetProperties;
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

    /**
     * 支持的父子维 hierarchy 操作符名称白名单。
     *
     * <p>这些操作符的方向、距离和 {@code maxDepth} 语义较复杂，这里只维护校验所需的名称集合，
     * 不在此处重复展开行为定义。详细说明可直接查看：
     * <ul>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.HierarchyOperator}</li>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.ChildrenOfOperator}</li>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.DescendantsOfOperator}</li>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.SelfAndDescendantsOfOperator}</li>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.AncestorsOfOperator}</li>
     *   <li>{@link com.foggyframework.dataset.db.model.engine.formula.hierarchy.SelfAndAncestorsOfOperator}</li>
     *   <li>样例文档：{@code docs/8.1.10.beta/P1-维度成员内部QM映射/P1-维度成员内部QM映射-MVP样例.md}</li>
     * </ul>
     */
    private static final Set<String> HIERARCHY_OPERATORS = Set.of(
            "childrenof", "descendantsof", "selfanddescendantsof",
            "ancestorsof", "selfandancestorsof"
    );

    /**
     * 不需要 value 的操作符（空值检查类操作符）
     */
    private static final Set<String> NULL_VALUE_OPERATORS = Set.of(
            // 旧格式
            "null", "!null", "null|empty", "!null&!empty",
            // 新格式（SqlFormula 定义的别名）
            "isnull", "is null", "isnotnull", "is not null",
            "isnullandempty", "isnotnullandempty"
    );

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        DbQueryRequestDef queryRequest = ctx.getRequest().getParam();

        if (log.isDebugEnabled()) {
            log.debug("=== Query Request Validation Start ===");
        }

        // 1. 校验 slice 条件
        validateSlice(queryRequest.getSlice());
        validateSlice(queryRequest.getHaving());

        // 2. 校验 groupBy
        validateGroupBy(queryRequest.getGroupBy());

        // 3. 校验 orderBy
        validateOrderBy(queryRequest.getOrderBy());

        if (log.isDebugEnabled()) {
            log.debug("=== Query Request Validation Passed ===");
        }

        int defaultLimit = datasetProperties != null ? datasetProperties.getDefaultLimit() : 1000;
        ctx.getRequest().resolveForQuery(defaultLimit);

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
        // 1. 如果是逻辑组（$or 或 $and），递归校验子条件
        if (item._isLogicalGroup()) {
            validateCondChildren(item._getGroupChildren());
            return;
        }

        // $expr 表达式条件：field / op / value 不适用，表达式合法性由 SqlExpFactory
        // 在编译期兜底（非法函数或语法会抛 SecurityException / IllegalArgumentException）。
        // 见 BUG-001-slice-expr-validation-gap。
        if (StringUtils.isNotEmpty(item.getExpr())) {
            return;
        }

        // 2. 校验 field 不为空
        if (StringUtils.isEmpty(item.getField())) {
            throw RX.throwAUserTip(DatasetMessages.validationSliceFieldRequired(index));
        }

        String field = item.getField();

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

            // 1. 如果是逻辑组（$or 或 $and），递归校验
            if (item._isLogicalGroup()) {
                validateCondChildren(item._getGroupChildren());
                continue;
            }

            // $expr 表达式条件：field / op / value 不适用，由 SqlExpFactory 在编译期兜底。
            // 见 BUG-001-slice-expr-validation-gap。
            if (StringUtils.isNotEmpty(item.getExpr())) {
                continue;
            }

            // 2. 校验 field 不为空
            if (StringUtils.isEmpty(item.getField())) {
                throw RX.throwAUserTip(DatasetMessages.validationSliceFieldRequired(i));
            }

            String field = item.getField();

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



            // 2. 校验 order 只能是 asc 或 desc
            if(StringUtils.isNotEmpty(item.getDir())) {
                String order = item.getDir().toLowerCase();
                if (!SUPPORTED_SORT_DIRECTIONS.contains(order)) {
                    throw RX.throwAUserTip(DatasetMessages.validationOrderByDirInvalid(i, field, order));
                }
            }
        }
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    /**
     * 检查操作符是否合法（通过 SqlFormulaService 或层级操作符集合）
     */
    private boolean isValidOperator(String op) {
        if (op == null) {
            return false;
        }
        // 检查标准 SQL 操作符
        if (sqlFormulaService.supports(op)) {
            return true;
        }
        // 检查层级操作符（父子维度查询）
        return HIERARCHY_OPERATORS.contains(op.toLowerCase());
    }

    /**
     * 获取所有支持的操作符列表（用于错误提示）
     */
    private String getSupportedOperators() {
        return "=, !=, >, >=, <, <=, in, not in, like, left_like, right_like, " +
               "is null, is not null, [], [), (], (), " +
               "childrenOf, descendantsOf, selfAndDescendantsOf, ancestorsOf, selfAndAncestorsOf, similar, hybrid";
    }

    /**
     * 检查是否为空值操作符（这些操作符不需要 value）
     */
    private boolean isNullValueOperator(String op) {
        if (op == null) {
            return false;
        }
        return NULL_VALUE_OPERATORS.contains(op.toLowerCase());
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
