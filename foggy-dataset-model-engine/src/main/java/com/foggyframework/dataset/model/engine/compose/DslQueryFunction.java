package com.foggyframework.dataset.model.engine.compose;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.plan.ColumnObjectNormalizer;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.port.SemanticQueryExecutionPort;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;

/**
 * fsscript {@code dsl()} 内置函数 -- QM Compose 的核心桥梁
 *
 * <p>在 AI 生成的 compose 脚本中执行语义查询，返回 {@link DataSetResult}。
 * 每次 compose 脚本执行时创建新实例（非单例），注入当前请求的上下文。</p>
 *
 * <pre>{@code
 * // fsscript 中的用法
 * const orders = dsl({
 *     model: 'SaleOrderQM',
 *     columns: ['partner$id', 'partner$caption', 'sum(amountTotal) as totalSales'],
 *     orderBy: ['-totalSales'],
 *     limit: 10
 * });
 * }</pre>
 *
 * <p>与 {@code LoadTableModelFunction} 的关键区别：
 * <ul>
 *   <li>非单例 -- 每次执行创建新实例，携带请求级上下文</li>
 *   <li>执行实际查询 -- 通过窄 semantic execution port 执行</li>
 *   <li>返回结果集 -- 返回 {@link DataSetResult} 而非模型定义</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
public class DslQueryFunction implements FsscriptFunction {

    private static final Logger logger = LoggerFactory.getLogger(DslQueryFunction.class);

    /**
     * 单次 compose 脚本中允许的最大查询次数（防止无限循环）
     */
    private static final int MAX_QUERY_COUNT = 20;

    private final SemanticQueryExecutionPort queryExecutionPort;
    private final ComposeSemanticPlanningPort planningPort;
    private final SemanticRequestContext requestContext;

    /**
     * 数据源（可选，用于 withJoin 的 CTE 组合执行）
     */
    private final DataSource dataSource;

    /**
     * 查询计数器（防止脚本中死循环调用 dsl）
     */
    private int queryCount = 0;

    /**
     * @param queryService   语义查询服务
     * @param requestContext 请求上下文（namespace + 安全信息）
     */
    public DslQueryFunction(SemanticQueryServiceV3 queryService,
                            SemanticRequestContext requestContext) {
        this(queryService, requestContext, null);
    }

    /**
     * @param queryService   语义查询服务
     * @param requestContext 请求上下文（namespace + 安全信息）
     * @param dataSource     数据源（用于 withJoin 的 CTE 组合执行，可选）
     */
    public DslQueryFunction(SemanticQueryServiceV3 queryService,
                            SemanticRequestContext requestContext,
                            DataSource dataSource) {
        this(queryService, queryService, requestContext, dataSource);
    }

    public DslQueryFunction(SemanticQueryExecutionPort queryExecutionPort,
                            ComposeSemanticPlanningPort planningPort,
                            SemanticRequestContext requestContext,
                            DataSource dataSource) {
        this.queryExecutionPort = queryExecutionPort;
        this.planningPort = planningPort;
        this.requestContext = requestContext;
        this.dataSource = dataSource;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object executeFunction(ExpEvaluator evaluator, Object... args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("dsl() requires a parameter object, e.g.: dsl({ model: 'MyQM', columns: [...] })");
        }

        // 防护：限制单脚本查询次数
        queryCount++;
        if (queryCount > MAX_QUERY_COUNT) {
            throw new IllegalStateException(
                    "Exceeded maximum query count (" + MAX_QUERY_COUNT + ") in a single compose script. " +
                    "Check for loops or reduce the number of dsl() calls.");
        }

        Object arg = args[0];
        if (!(arg instanceof Map)) {
            throw new IllegalArgumentException("dsl() parameter must be an object, got: " +
                    (arg == null ? "null" : arg.getClass().getSimpleName()));
        }

        Map<String, Object> params = (Map<String, Object>) arg;
        String model = (String) params.get("model");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("dsl() requires 'model' field, e.g.: dsl({ model: 'SaleOrderQM', ... })");
        }

        SemanticQueryRequest request = buildRequest(params);

        logger.debug("dsl() executing query #{}: model={}, columns={}, limit={}",
                queryCount, model, request.getColumns(), request.getLimit());

        SemanticQueryResponse response = queryExecutionPort.queryModel(
                model, request, "execute", requestContext);

        logger.debug("dsl() query #{} completed: model={}, rows={}",
                queryCount, model, response.getItems() != null ? response.getItems().size() : 0);

        DataSetResult result = new DataSetResult(response.getItems(), response);

        // 保留原始参数（供 withJoin 重新生成 SQL）
        result.setDslParams(params);

        // 设置 compose 上下文（供 withJoin 使用）
        if (dataSource != null) {
            result.setComposeContext(new DataSetResult.ComposeContext(
                    planningPort, requestContext, dataSource));
        }

        return result;
    }

    /**
     * 将 fsscript 对象参数映射为 {@link SemanticQueryRequest}
     *
     * <p>支持的字段与 {@code dataset.query_model} 的 payload 一致：
     * columns, slice, having, orderBy, groupBy, limit, start, returnTotal, distinct, calculatedFields, timeWindow</p>
     */
    @SuppressWarnings("unchecked")
    private SemanticQueryRequest buildRequest(Map<String, Object> params) {
        SemanticQueryRequest request = new SemanticQueryRequest();

        // columns
        Object columns = params.get("columns");
        if (columns instanceof List) {
            request.setColumns(toStringList((List<?>) columns));
        }

        // slice -- fsscript 对象数组直接透传（Map 结构与 SliceItem JSON 一致）
        Object slice = params.get("slice");
        if (slice instanceof List) {
            request.setSlice(convertSliceItems((List<?>) slice));
        }

        Object having = params.get("having");
        if (having instanceof List) {
            request.setHaving(convertSliceItems((List<?>) having));
        }

        Object postSlice = params.get("postSlice");
        if (postSlice instanceof List) {
            request.setPostSlice(convertSliceItems((List<?>) postSlice));
        }

        // orderBy -- 支持简写：'-field' → {field, dir:'desc'}，'field' → {field, dir:'asc'}
        Object orderBy = params.get("orderBy");
        if (orderBy instanceof List) {
            request.setOrderBy(convertOrderItems((List<?>) orderBy));
        }

        // groupBy
        Object groupBy = params.get("groupBy");
        if (groupBy instanceof List) {
            request.setGroupBy(convertGroupByItems((List<?>) groupBy));
        }

        // timeWindow -- object shape matches SemanticQueryRequest.timeWindow
        Object timeWindow = params.get("timeWindow");
        if (timeWindow instanceof Map) {
            request.setTimeWindow(new LinkedHashMap<>((Map<String, Object>) timeWindow));
        }

        Object calculatedFields = params.get("calculatedFields");
        if (calculatedFields instanceof List) {
            request.setCalculatedFields(convertCalculatedFields((List<?>) calculatedFields));
        }

        // limit
        Object limit = params.get("limit");
        if (limit instanceof Number) {
            request.setLimit(((Number) limit).intValue());
        }

        // start
        Object start = params.get("start");
        if (start instanceof Number) {
            request.setStart(((Number) start).intValue());
        }

        // returnTotal
        Object returnTotal = params.get("returnTotal");
        if (returnTotal instanceof Boolean) {
            request.setReturnTotal((Boolean) returnTotal);
        }

        // distinct
        Object distinct = params.get("distinct");
        if (distinct instanceof Boolean) {
            request.setDistinct((Boolean) distinct);
        }

        // hints -- 标记来源为 compose 脚本
        Map<String, Object> hints = new HashMap<>();
        hints.put("fromCompose", true);
        request.setHints(hints);

        return request;
    }

    /**
     * 转换 slice 条件列表
     * <p>fsscript 对象数组结构与 {@link SemanticQueryRequest.SliceItem} JSON 一致，
     * 直接映射字段即可。</p>
     */
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

                // $or / $and 嵌套
                Object orGroup = map.get("$or");
                if (orGroup instanceof List) {
                    si.setOr(convertSliceItems((List<?>) orGroup));
                }
                Object andGroup = map.get("$and");
                if (andGroup instanceof List) {
                    si.setAnd(convertSliceItems((List<?>) andGroup));
                }

                result.add(si);
            }
        }
        return result;
    }

    /**
     * 转换排序项
     * <p>支持简写格式：
     * <ul>
     *   <li>{@code '-field'} → desc</li>
     *   <li>{@code 'field'} → asc</li>
     *   <li>{@code 'field desc'} → desc</li>
     *   <li>{@code {field: 'f', dir: 'desc'}} → 对象格式</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<SemanticQueryRequest.OrderItem> convertOrderItems(List<?> rawOrderBy) {
        List<SemanticQueryRequest.OrderItem> result = new ArrayList<>();
        for (Object item : rawOrderBy) {
            try {
                result.add(ComposeOrderByNormalizer.toOrderItem(item));
            } catch (IllegalArgumentException ex) {
                continue;
            }
        }
        return result;
    }

    /**
     * 转换分组项
     * <p>支持简写：{@code 'field'} → {@code {field: 'field'}}</p>
     */
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

    @SuppressWarnings("unchecked")
    private List<CalculatedFieldDef> convertCalculatedFields(List<?> rawCalculatedFields) {
        List<CalculatedFieldDef> result = new ArrayList<>();
        for (Object item : rawCalculatedFields) {
            if (item instanceof CalculatedFieldDef def) {
                result.add(def);
            } else if (item instanceof Map) {
                result.add(convertCalculatedField((Map<String, Object>) item));
            }
        }
        return result;
    }

    private CalculatedFieldDef convertCalculatedField(Map<String, Object> map) {
        CalculatedFieldDef def = new CalculatedFieldDef();
        def.setName((String) map.get("name"));
        def.setCaption((String) map.get("caption"));
        def.setExpression((String) map.get("expression"));
        def.setDescription((String) map.get("description"));
        def.setAgg((String) map.get("agg"));
        def.setEmptyDefault(map.get("emptyDefault"));
        return def;
    }

    /**
     * Convert a column list to {@code List<String>} required by
     * {@link SemanticQueryRequest#setColumns(List)}.
     *
     * <p>G5 Phase 1 (F4): {@link Map} entries (e.g. {@code {field, agg, as}}) are
     * normalized to their canonical string form (e.g. {@code "SUM(amount) AS total"})
     * via {@link ColumnObjectNormalizer}. F1-F3 string entries pass through unchanged.
     * Other types (rare in this legacy path) fall back to {@code toString()}.</p>
     *
     * <p>Throws {@link IllegalArgumentException} with a {@code COLUMN_*} error-code
     * prefix on F4 validation failure (missing field, unknown agg, etc.).</p>
     */
    private List<String> toStringList(List<?> raw) {
        return ColumnObjectNormalizer.normalizeColumnsToStrings(raw);
    }

    // ---- FsscriptFunction 接口方法 ----

    @Override
    public Object threadSafeAccept(Object t) {
        throw new UnsupportedOperationException("dsl() must be called with named parameters: dsl({ model: '...', ... })");
    }

    @Override
    public List<Exp> getArgDefs() {
        return Collections.emptyList();
    }

    @Override
    public Object autoApply(ExpEvaluator ee) {
        throw new IllegalArgumentException("dsl() requires parameters");
    }

    @Override
    public Object apply(Object[] objects) {
        if (objects == null || objects.length == 0) {
            throw new IllegalArgumentException("dsl() requires a parameter object");
        }
        return executeFunction(null, objects);
    }

    @Override
    public String toString() {
        return "dsl({model, columns, slice, orderBy, limit, ...})";
    }
}
