package com.foggyframework.dataset.db.model.engine.compose;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

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

    public DataSetResult(List<Map<String, Object>> items, SemanticQueryResponse rawResponse) {
        this.items = items != null ? items : Collections.emptyList();
        this.rawResponse = rawResponse;
    }

    // ---- PropertyFunction：让 fsscript 中 ds.method() 能正确分发 ----

    @Override
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
            default -> throw new IllegalArgumentException(
                    "DataSetResult has no method '" + methodName + "'. " +
                    "Available: column(field), toList(), first(), size(), isEmpty(), value(field)");
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

    @Override
    public String toString() {
        return "DataSetResult{rows=" + items.size() + "}";
    }
}
