package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;

/**
 * 组合查询结果 -- 延迟执行 CTE/子查询 JOIN
 *
 * <p>由 {@link DataSetResult#withJoin} 创建，在首次调用 {@code toList()} / {@code execute()}
 * 时触发：
 * <ol>
 *   <li>对左右两个 QM 分别调用 {@code generateSql()} 获取 SQL + params</li>
 *   <li>{@link CteComposer#compose} 拼接为 CTE（或子查询）</li>
 *   <li>通过 {@link DataSource} 执行最终 SQL</li>
 *   <li>返回 {@link DataSetResult}</li>
 * </ol>
 *
 * <p>实现 {@link PropertyFunction} 以支持 fsscript 中的链式调用。</p>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
public class ComposedDataSetResult implements PropertyFunction {

    private static final Logger logger = LoggerFactory.getLogger(ComposedDataSetResult.class);

    private final SemanticQueryServiceV3 queryService;
    private final SemanticRequestContext requestContext;
    private final DataSource dataSource;

    /**
     * 左侧查询参数（原始 dsl() 参数）
     */
    private final Map<String, Object> leftParams;

    /**
     * 右侧查询参数
     */
    private final Map<String, Object> rightParams;

    /**
     * JOIN 配置
     */
    private final String joinType;
    private final String joinKey;

    /**
     * 缓存的执行结果
     */
    private DataSetResult cachedResult;

    public ComposedDataSetResult(SemanticQueryServiceV3 queryService,
                                 SemanticRequestContext requestContext,
                                 DataSource dataSource,
                                 Map<String, Object> leftParams,
                                 Map<String, Object> rightParams,
                                 String joinType,
                                 String joinKey) {
        this.queryService = queryService;
        this.requestContext = requestContext;
        this.dataSource = dataSource;
        this.leftParams = leftParams;
        this.rightParams = rightParams;
        this.joinType = joinType;
        this.joinKey = joinKey;
    }

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        // 延迟执行：第一次调用任何方法时触发 SQL 组合 + 执行
        DataSetResult result = execute();
        return result.invoke(evaluator, methodName, args);
    }

    /**
     * 执行组合查询
     */
    @SuppressWarnings("unchecked")
    public DataSetResult execute() {
        if (cachedResult != null) {
            return cachedResult;
        }

        String leftModel = (String) leftParams.get("model");
        String rightModel = (String) rightParams.get("model");

        logger.debug("Executing composed query: {} {} JOIN {} ON {}",
                leftModel, joinType, rightModel, joinKey);

        // 1. 生成左侧 SQL
        SemanticQueryRequest leftRequest = buildSemanticRequest(leftParams);
        SqlGenerationResult leftSql = queryService.generateSql(leftModel, leftRequest, requestContext);

        // 2. 生成右侧 SQL
        SemanticQueryRequest rightRequest = buildSemanticRequest(rightParams);
        SqlGenerationResult rightSql = queryService.generateSql(rightModel, rightRequest, requestContext);

        // 3. 构建 CTE 单元
        CteUnit leftUnit = new CteUnit("cte_0", leftSql.getSql(), leftSql.getParams(), null);
        CteUnit rightUnit = new CteUnit("cte_1", rightSql.getSql(), rightSql.getParams(), null);

        // 4. 构建 JoinSpec
        JoinSpec joinSpec = new JoinSpec(joinType, joinKey, joinKey, null, null);

        // 5. 检测方言决定使用 CTE 还是子查询
        FDialect dialect = DbUtils.getDialect(dataSource);
        boolean useCte = dialect.supportsCte();

        // 6. 组合 SQL
        ComposedSql composed = CteComposer.compose(leftUnit, rightUnit, joinSpec, useCte);

        logger.debug("Composed SQL (cte={}): {}", useCte, composed.getSql());

        // 7. 执行
        List<Map<String, Object>> items = DataSourceQueryUtils.getDatasetTemplate(dataSource)
                .getTemplate()
                .queryForList(composed.getSql(), composed.getParams().toArray(new Object[0]));

        cachedResult = new DataSetResult(items, null);
        return cachedResult;
    }

    /**
     * 从 dsl() 参数构建 SemanticQueryRequest（复用 DslQueryFunction 的逻辑）
     */
    @SuppressWarnings("unchecked")
    private SemanticQueryRequest buildSemanticRequest(Map<String, Object> params) {
        SemanticQueryRequest request = new SemanticQueryRequest();

        Object columns = params.get("columns");
        if (columns instanceof List) {
            request.setColumns(toStringList((List<?>) columns));
        }

        Object slice = params.get("slice");
        if (slice instanceof List) {
            request.setSlice(convertSliceItems((List<?>) slice));
        }

        Object orderBy = params.get("orderBy");
        if (orderBy instanceof List) {
            request.setOrderBy(convertOrderItems((List<?>) orderBy));
        }

        Object groupBy = params.get("groupBy");
        if (groupBy instanceof List) {
            request.setGroupBy(convertGroupByItems((List<?>) groupBy));
        }

        Object timeWindow = params.get("timeWindow");
        if (timeWindow instanceof Map) {
            request.setTimeWindow(new LinkedHashMap<>((Map<String, Object>) timeWindow));
        }

        Object limit = params.get("limit");
        if (limit instanceof Number) {
            request.setLimit(((Number) limit).intValue());
        }

        Object start = params.get("start");
        if (start instanceof Number) {
            request.setStart(((Number) start).intValue());
        }

        Object returnTotal = params.get("returnTotal");
        if (returnTotal instanceof Boolean) {
            request.setReturnTotal((Boolean) returnTotal);
        }

        Object distinct = params.get("distinct");
        if (distinct instanceof Boolean) {
            request.setDistinct((Boolean) distinct);
        }

        return request;
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.SliceItem> convertSliceItems(List<?> rawSlice) {
        List<SemanticQueryRequest.SliceItem> result = new ArrayList<>();
        for (Object item : rawSlice) {
            if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                SemanticQueryRequest.SliceItem si = new SemanticQueryRequest.SliceItem();
                si.setField((String) map.get("field"));
                si.setOp((String) map.get("op"));
                si.setValue(map.get("value"));
                result.add(si);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.OrderItem> convertOrderItems(List<?> rawOrderBy) {
        List<SemanticQueryRequest.OrderItem> result = new ArrayList<>();
        for (Object item : rawOrderBy) {
            SemanticQueryRequest.OrderItem oi = new SemanticQueryRequest.OrderItem();
            if (item instanceof String str) {
                str = str.trim();
                if (str.startsWith("-")) {
                    oi.setField(str.substring(1));
                    oi.setDir("desc");
                } else {
                    oi.setField(str);
                    oi.setDir("asc");
                }
            }
            result.add(oi);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.GroupByItem> convertGroupByItems(List<?> rawGroupBy) {
        List<SemanticQueryRequest.GroupByItem> result = new ArrayList<>();
        for (Object item : rawGroupBy) {
            if (item instanceof String str) {
                result.add(new SemanticQueryRequest.GroupByItem(str, null));
            } else if (item instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) item;
                result.add(new SemanticQueryRequest.GroupByItem(
                        (String) map.get("field"),
                        (String) map.get("agg")
                ));
            }
        }
        return result;
    }

    private List<String> toStringList(List<?> raw) {
        List<String> result = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o != null) result.add(o.toString());
        }
        return result;
    }

    @Override
    public String toString() {
        return "ComposedDataSetResult{" + leftParams.get("model") + " " + joinType + " JOIN " +
                rightParams.get("model") + " ON " + joinKey + "}";
    }
}
