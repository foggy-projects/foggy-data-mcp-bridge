package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import javax.sql.DataSource;
import java.util.*;

/**
 * QM Compose 查询结果包装 -- 轻量 DataFrame
 *
 * <p>持有单个 QM 查询的结果集，提供列提取、行访问等操作。
 * 主要用于 fsscript {@code dsl()} 函数的返回值，
 * 支持 ID 下推（{@link #column(String)}）和结果遍历。</p>
 *
 * <p>实现 {@link PropertyFunction} 以支持 fsscript 中的方法调用：
 * {@code ds.column('field')}、{@code ds.toList()} 等。</p>
 *
 * <pre>{@code
 * // fsscript 中的用法
 * const ds = dsl({ model: 'SaleOrderQM', columns: ['partner$id'], limit: 10 });
 * ds.column('partner$id')   // → [1, 2, 3, ...]
 * ds.toList()               // → [{partner$id: 1}, ...]
 * ds.first()                // → {partner$id: 1}
 * ds.size()                 // → 10
 * }</pre>
 *
 * @author Foggy Framework
 * @since 8.2.0
 */
public class DataSetResult implements PropertyFunction {

    private final List<Map<String, Object>> items;
    private final SemanticQueryResponse rawResponse;

    /**
     * 原始 dsl() 查询参数（用于 withJoin 时重新生成 SQL）
     */
    private Map<String, Object> dslParams;

    /**
     * Compose 上下文（用于 withJoin）
     */
    private ComposeContext composeContext;

    public DataSetResult(List<Map<String, Object>> items, SemanticQueryResponse rawResponse) {
        this.items = items != null ? items : Collections.emptyList();
        this.rawResponse = rawResponse;
    }

    public Map<String, Object> getDslParams() {
        return dslParams;
    }

    public void setDslParams(Map<String, Object> dslParams) {
        this.dslParams = dslParams;
    }

    public ComposeContext getComposeContext() {
        return composeContext;
    }

    public void setComposeContext(ComposeContext composeContext) {
        this.composeContext = composeContext;
    }

    /**
     * Compose 上下文 -- 持有 withJoin 所需的服务引用
     */
    public static class ComposeContext {
        private final SemanticQueryServiceV3 queryService;
        private final SemanticRequestContext requestContext;
        private final DataSource dataSource;

        public ComposeContext(SemanticQueryServiceV3 queryService,
                              SemanticRequestContext requestContext,
                              DataSource dataSource) {
            this.queryService = queryService;
            this.requestContext = requestContext;
            this.dataSource = dataSource;
        }

        public SemanticQueryServiceV3 getQueryService() { return queryService; }
        public SemanticRequestContext getRequestContext() { return requestContext; }
        public DataSource getDataSource() { return dataSource; }
    }

    // ---- PropertyFunction：让 fsscript 中 ds.method() 能正确分发 ----

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        return switch (methodName) {
            case "column" -> {
                requireArgs(methodName, args, 1);
                yield column((String) args[0]);
            }
            case "toList" -> toList();
            case "first" -> first();
            case "size" -> size();
            case "isEmpty" -> isEmpty();
            case "value" -> {
                requireArgs(methodName, args, 1);
                yield value((String) args[0]);
            }
            case "getRawResponse" -> getRawResponse();
            case "withJoin" -> {
                // withJoin(rightDs, joinType, joinKey)
                requireArgs(methodName, args, 3);
                yield withJoin((DataSetResult) args[0], (String) args[1], (String) args[2]);
            }
            case "filter" -> {
                requireArgs(methodName, args, 1);
                yield filter((String) args[0]);
            }
            case "sort" -> {
                requireArgs(methodName, args, 1);
                yield sort((String) args[0]);
            }
            case "compute" -> {
                requireArgs(methodName, args, 2);
                yield compute((String) args[0], (String) args[1]);
            }
            case "joinInMemory" -> {
                // joinInMemory(rightDs, joinType, joinKey) or joinInMemory(rightDs, joinType, leftKey, rightKey)
                requireArgs(methodName, args, 3);
                if (args.length >= 4) {
                    yield joinInMemory((DataSetResult) args[0], (String) args[1],
                            (String) args[2], (String) args[3]);
                }
                yield joinInMemory((DataSetResult) args[0], (String) args[1], (String) args[2]);
            }
            default -> throw new IllegalArgumentException(
                    "DataSetResult has no method '" + methodName + "'. " +
                    "Available: column, toList, first, size, isEmpty, value, withJoin, joinInMemory, filter, sort, compute");
        };
    }

    private void requireArgs(String method, Object[] args, int count) {
        if (args == null || args.length < count) {
            throw new IllegalArgumentException(method + "() requires " + count + " argument(s)");
        }
    }

    // ---- 核心方法 ----

    /**
     * 提取单列值数组（去重）
     *
     * <p>典型用途：提取 ID 列表注入下一个查询的 slice.value（ID 下推模式）</p>
     *
     * <pre>{@code
     * const ids = ds.column('partner$id'); // → [1, 2, 3]
     * dsl({ model: 'CrmLeadQM', slice: [{ field: 'partner$id', op: 'in', value: ids }] });
     * }</pre>
     *
     * @param field 字段名
     * @return 该列所有非 null 值的列表（保持顺序，去重）
     */
    public List<Object> column(String field) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Object> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : items) {
            Object val = row.get(field);
            if (val != null) {
                seen.add(val);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * 返回全部行
     */
    public List<Map<String, Object>> toList() {
        return Collections.unmodifiableList(items);
    }

    /**
     * 返回首行，无数据时返回空 Map
     */
    public Map<String, Object> first() {
        return items.isEmpty() ? Collections.emptyMap() : items.get(0);
    }

    /**
     * 结果行数
     */
    public int size() {
        return items.size();
    }

    /**
     * 是否为空结果集
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 获取原始响应（含 schema、pagination、total 等完整信息）
     */
    public SemanticQueryResponse getRawResponse() {
        return rawResponse;
    }

    /**
     * 获取单个值（首行指定列），无数据时返回 null
     *
     * <p>适用于标量查询：{@code dsl({columns: ['count(id) as cnt']}).value('cnt')}</p>
     */
    public Object value(String field) {
        Map<String, Object> row = first();
        return row.isEmpty() ? null : row.get(field);
    }

    /**
     * 创建 CTE 组合查询（延迟执行）
     *
     * <p>在 fsscript 中：
     * <pre>{@code
     * const composed = leftDs.withJoin(rightDs, 'LEFT', 'partner_id');
     * return composed.toList();
     * }</pre>
     *
     * @param right    右侧数据集（必须有 dslParams）
     * @param joinType JOIN 类型（LEFT, INNER）
     * @param joinKey  JOIN 键名
     * @return ComposedDataSetResult（延迟执行，首次调用方法时触发 SQL 组合）
     */
    public ComposedDataSetResult withJoin(DataSetResult right, String joinType, String joinKey) {
        if (this.dslParams == null) {
            throw new IllegalStateException("withJoin() requires dslParams. This DataSetResult was not created by dsl().");
        }
        if (right.getDslParams() == null) {
            throw new IllegalStateException("withJoin() requires dslParams on the right DataSetResult.");
        }
        if (this.composeContext == null) {
            throw new IllegalStateException("withJoin() requires composeContext. Ensure DslQueryFunction provides it.");
        }

        return new ComposedDataSetResult(
                composeContext.getQueryService(),
                composeContext.getRequestContext(),
                composeContext.getDataSource(),
                this.dslParams,
                right.getDslParams(),
                joinType,
                joinKey
        );
    }

    // ---- 二次计算方法（返回新 DataSetResult，不修改原实例）----

    /**
     * 内存过滤 -- 使用 fsscript 表达式对每行求值，保留结果为 truthy 的行
     *
     * <pre>{@code
     * // fsscript 中的用法
     * const filtered = ds.filter('amountTotal > 1000');
     * const active = ds.filter("status == 'confirmed'");
     * }</pre>
     *
     * @param expr fsscript 布尔表达式，行字段作为变量
     * @return 新的 DataSetResult（仅含匹配行）
     */
    public DataSetResult filter(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("filter() requires a non-empty expression");
        }
        if (items.isEmpty()) {
            return new DataSetResult(Collections.emptyList(), null);
        }

        Exp compiled = new ExpParser().compileEl(expr);
        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> row : items) {
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                ee.setVar(entry.getKey(), entry.getValue());
            }
            Object result = compiled.evalValue(ee);
            if (isTruthy(result)) {
                filtered.add(row);
            }
        }

        return new DataSetResult(filtered, null);
    }

    /**
     * 内存排序 -- 按指定字段排序
     *
     * <p>支持简写：{@code '-field'} 降序，{@code 'field'} 升序</p>
     *
     * <pre>{@code
     * const sorted = ds.sort('-amountTotal');   // 按金额降序
     * const sorted2 = ds.sort('name');          // 按名称升序
     * }</pre>
     *
     * @param field 排序字段（前缀 '-' 表示降序）
     * @return 新的 DataSetResult（排序后）
     */
    public DataSetResult sort(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("sort() requires a non-empty field name");
        }
        if (items.size() <= 1) {
            return new DataSetResult(new ArrayList<>(items), null);
        }

        boolean desc = field.startsWith("-");
        String actualField = desc ? field.substring(1) : field;

        List<Map<String, Object>> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            Object va = a.get(actualField);
            Object vb = b.get(actualField);
            int cmp = compareValues(va, vb);
            return desc ? -cmp : cmp;
        });

        return new DataSetResult(sorted, null);
    }

    /**
     * 新增计算列 -- 对每行执行 fsscript 表达式，将结果写入新列
     *
     * <pre>{@code
     * // fsscript 中的用法
     * const withMargin = ds.compute('margin', 'revenue - cost');
     * const withLabel = ds.compute('label', "name + ' (' + region + ')'");
     * }</pre>
     *
     * @param name 新列名
     * @param expr fsscript 表达式，行字段作为变量
     * @return 新的 DataSetResult（每行增加计算列）
     */
    public DataSetResult compute(String name, String expr) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("compute() requires a non-empty column name");
        }
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("compute() requires a non-empty expression");
        }
        if (items.isEmpty()) {
            return new DataSetResult(Collections.emptyList(), null);
        }

        Exp compiled = new ExpParser().compileEl(expr);
        List<Map<String, Object>> computed = new ArrayList<>(items.size());

        for (Map<String, Object> row : items) {
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                ee.setVar(entry.getKey(), entry.getValue());
            }
            Object result = compiled.evalValue(ee);

            // 创建新行（不修改原始 row）
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            newRow.put(name, result);
            computed.add(newRow);
        }

        return new DataSetResult(computed, null);
    }

    // ---- 内存 JOIN（跨库合并查询）----

    /**
     * 内存 Hash JOIN -- 将两个已执行的 DataSetResult 在 Java 内存中合并
     *
     * <p>适用于跨库场景：左右两个 QM 来自不同 DataSource，无法使用 CTE 拼接。
     * 两侧各自独立执行后，在内存中按 joinKey 做 Hash JOIN。</p>
     *
     * <p>算法参考 {@code ResultSetQueryImpl.LeftJoin}：
     * 对右表建 HashMap 索引（O(m)），遍历左表逐行探测（O(n)），
     * 支持 1:N 多行匹配自动展开。</p>
     *
     * <pre>{@code
     * // fsscript 中的用法（跨库）
     * const orders = dsl({ model: 'SaleOrderQM', columns: ['partner$id', 'amountTotal'] });
     * const employees = dsl({ model: 'HrEmployeeQM', columns: ['partner$id', 'name'] });
     * return orders.joinInMemory(employees, 'LEFT', 'partner$id');
     * }</pre>
     *
     * <p>与 {@link #withJoin} 的区别：
     * <ul>
     *   <li>{@code withJoin} — SQL 层 CTE 组合，要求同库同 DataSource</li>
     *   <li>{@code joinInMemory} — Java 内存 Hash JOIN，支持跨库，但受 JVM 内存限制</li>
     * </ul>
     *
     * @param right    右侧数据集
     * @param joinType JOIN 类型：{@code "LEFT"}（保留左侧全部行）或 {@code "INNER"}（仅匹配行）
     * @param joinKey  JOIN 键名（左右两侧使用相同字段名）
     * @return 新的 DataSetResult（合并后的行，列为两侧列的并集）
     */
    public DataSetResult joinInMemory(DataSetResult right, String joinType, String joinKey) {
        return joinInMemory(right, joinType, joinKey, joinKey);
    }

    /**
     * 内存 Hash JOIN（支持左右不同 joinKey 名称）
     *
     * @param right    右侧数据集
     * @param joinType JOIN 类型（LEFT, INNER）
     * @param leftKey  左侧 JOIN 键名
     * @param rightKey 右侧 JOIN 键名
     * @return 新的 DataSetResult（合并后的行）
     */
    public DataSetResult joinInMemory(DataSetResult right, String joinType,
                                       String leftKey, String rightKey) {
        if (joinType == null || joinType.isBlank()) {
            throw new IllegalArgumentException("joinInMemory() requires a non-empty joinType");
        }
        if (leftKey == null || leftKey.isBlank()) {
            throw new IllegalArgumentException("joinInMemory() requires a non-empty leftKey");
        }
        if (rightKey == null || rightKey.isBlank()) {
            throw new IllegalArgumentException("joinInMemory() requires a non-empty rightKey");
        }

        String jt = joinType.trim().toUpperCase();
        if (!"LEFT".equals(jt) && !"INNER".equals(jt)) {
            throw new IllegalArgumentException("joinInMemory() joinType must be LEFT or INNER, got: " + joinType);
        }

        boolean isLeft = "LEFT".equals(jt);

        // 空集快速路径
        if (this.items.isEmpty()) {
            return new DataSetResult(Collections.emptyList(), null);
        }
        if (right.items.isEmpty()) {
            if (isLeft) {
                // LEFT JOIN：左表全保留，右侧列为 null
                List<Map<String, Object>> result = new ArrayList<>(items.size());
                for (Map<String, Object> leftRow : items) {
                    result.add(new LinkedHashMap<>(leftRow));
                }
                return new DataSetResult(result, null);
            } else {
                // INNER JOIN：右表空则无匹配
                return new DataSetResult(Collections.emptyList(), null);
            }
        }

        // 1. 对右表建 Hash 索引（O(m)），支持 1:N
        Map<Object, List<Map<String, Object>>> rightIndex = new LinkedHashMap<>();
        for (Map<String, Object> rightRow : right.items) {
            Object key = rightRow.get(rightKey);
            if (key != null) {
                rightIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(rightRow);
            }
        }

        // 2. 遍历左表，逐行探测（O(n)）
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> leftRow : items) {
            Object key = leftRow.get(leftKey);
            List<Map<String, Object>> matches = (key != null) ? rightIndex.get(key) : null;

            if (matches != null && !matches.isEmpty()) {
                // 匹配：1:N 展开
                for (Map<String, Object> rightRow : matches) {
                    Map<String, Object> merged = new LinkedHashMap<>(leftRow);
                    for (Map.Entry<String, Object> entry : rightRow.entrySet()) {
                        // 右侧列合并，同名列右侧不覆盖左侧（左表优先）
                        merged.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    result.add(merged);
                }
            } else if (isLeft) {
                // LEFT JOIN：无匹配行，保留左侧
                result.add(new LinkedHashMap<>(leftRow));
            }
            // INNER JOIN：无匹配行，丢弃
        }

        return new DataSetResult(result, null);
    }

    // ---- 内部辅助方法 ----

    /**
     * 判断值是否为 truthy（与 JavaScript 语义一致）
     */
    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }

    /**
     * 通用值比较（null-safe，支持 Comparable）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b);
            } catch (ClassCastException e) {
                // 类型不同时回退到字符串比较
                return a.toString().compareTo(b.toString());
            }
        }
        return a.toString().compareTo(b.toString());
    }

    @Override
    public String toString() {
        return "DataSetResult{rows=" + items.size() + "}";
    }
}
