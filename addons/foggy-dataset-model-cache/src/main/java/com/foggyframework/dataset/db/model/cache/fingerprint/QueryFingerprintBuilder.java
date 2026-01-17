package com.foggyframework.dataset.db.model.cache.fingerprint;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询指纹构建器
 * <p>
 * 从 {@link ModelResultContext} 构建 {@link QueryFingerprint}。
 * 支持从 JdbcQuery（包含权限条件）或 DbQueryRequestDef 构建。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Component
public class QueryFingerprintBuilder {

    /**
     * 非确定性函数列表
     */
    private static final Set<String> NON_DETERMINISTIC_FUNCTIONS = Set.of(
            "RAND", "RANDOM", "NOW", "CURRENT_TIMESTAMP", "CURRENT_DATE",
            "CURRENT_TIME", "UUID", "NEWID", "SYSDATE", "GETDATE"
    );

    /**
     * 从 ModelResultContext 构建指纹
     * <p>
     * 优先使用 JdbcQuery（包含完整权限条件），
     * 如果 JdbcQuery 尚未构建，则从 request 提取。
     * </p>
     *
     * @param context 查询上下文
     * @return 查询指纹
     */
    public QueryFingerprint build(ModelResultContext context) {
        DbQueryRequestDef request = context.getRequest().getParam();
        JdbcQuery jdbcQuery = context.getJdbcQuery();

        QueryFingerprint.QueryFingerprintBuilder builder = QueryFingerprint.builder()
                .modelName(request.getQueryModel())
                .pageNo(context.getRequest().getPageNo())
                .pageSize(context.getRequest().getPageSize());

        // 提取列
        builder.columns(extractColumns(request, context));

        // 提取分组
        builder.groupBy(extractGroupBy(request));

        // 提取排序
        builder.orderBy(extractOrderBy(request));

        // 计算字段数量
        if (context.getCalculatedColumns() != null) {
            builder.calculatedFieldCount(context.getCalculatedColumns().size());
        }

        // 提取条件签名
        if (jdbcQuery != null) {
            // 优先从 JdbcQuery 提取（包含权限条件）
            ConditionExtractResult result = extractConditionsFromJdbcQuery(jdbcQuery);
            builder.conditionSignatures(result.signatures);
            builder.hasRawSql(result.hasRawSql);
            builder.hasNonDeterministic(result.hasNonDeterministic);
        } else {
            // 回退：从 request 提取
            ConditionExtractResult result = extractConditionsFromRequest(request);
            builder.conditionSignatures(result.signatures);
            builder.hasRawSql(result.hasRawSql);
            builder.hasNonDeterministic(result.hasNonDeterministic);
        }

        return builder.build();
    }

    /**
     * 提取查询列
     */
    private List<String> extractColumns(DbQueryRequestDef request, ModelResultContext context) {
        List<String> columns = new ArrayList<>();

        // 从 request 获取列名
        if (request.getColumns() != null) {
            columns.addAll(request.getColumns());
        }

        // 添加计算字段名
        if (context.getCalculatedColumns() != null) {
            for (CalculatedDbColumn calc : context.getCalculatedColumns()) {
                // 使用计算字段的表达式哈希，因为相同名称可能有不同表达式
                String calcHash = calc.getName() + ":" + DigestUtils.md5Hex(calc.getDeclare());
                columns.add(calcHash);
            }
        }

        Collections.sort(columns);
        return columns;
    }

    /**
     * 提取分组列
     */
    private List<String> extractGroupBy(DbQueryRequestDef request) {
        if (request.getGroupBy() == null || request.getGroupBy().isEmpty()) {
            return Collections.emptyList();
        }

        return request.getGroupBy().stream()
                .map(g -> g.getField() + (g.getAgg() != null ? ":" + g.getAgg() : ""))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 提取排序
     */
    private List<String> extractOrderBy(DbQueryRequestDef request) {
        if (request.getOrderBy() == null || request.getOrderBy().isEmpty()) {
            return Collections.emptyList();
        }

        return request.getOrderBy().stream()
                .map(o -> o.getField() + ":" + (o.getDir() != null ? o.getDir() : "asc"))
                .collect(Collectors.toList()); // 保持原顺序，不排序
    }

    /**
     * 从 JdbcQuery 提取条件签名（包含权限注入的条件）
     */
    private ConditionExtractResult extractConditionsFromJdbcQuery(JdbcQuery query) {
        ConditionExtractResult result = new ConditionExtractResult();

        // 提取 WHERE 条件
        extractConditionsRecursive(query.getWhere(), result);

        // 提取 HAVING 条件
        extractConditionsRecursive(query.getHaving(), result);

        // 排序确保一致性
        Collections.sort(result.signatures);
        return result;
    }

    /**
     * 递归提取条件
     */
    private void extractConditionsRecursive(JdbcQuery.JdbcListCond cond, ConditionExtractResult result) {
        if (cond == null || cond.getConds() == null) {
            return;
        }

        for (JdbcQuery.JdbcCond c : cond.getConds()) {
            if (c instanceof JdbcQuery.SqlFragmentCond sqlFrag) {
                // 原始 SQL 片段 → 标记不可缓存
                result.hasRawSql = true;
                // 仍然记录签名（用于调试）
                result.signatures.add("raw:" + DigestUtils.md5Hex(sqlFrag.getSqlFragment()));

                // 检查是否包含非确定性函数
                if (containsNonDeterministicFunction(sqlFrag.getSqlFragment())) {
                    result.hasNonDeterministic = true;
                }
            } else if (c instanceof JdbcQuery.QueryTypeValueCond qtvc) {
                // 结构化条件（如 Mongo 风格）
                String valueHash = computeValueHash(qtvc.getValue());
                result.signatures.add(qtvc.getName() + ":" + qtvc.getQueryType() + ":" + valueHash);
            } else if (c instanceof JdbcQuery.ValueCond vc) {
                // 带值的 SQL 条件
                String valueHash = computeValueHash(vc.getValue());
                String fragHash = DigestUtils.md5Hex(vc.getSqlFragment());
                result.signatures.add("sql:" + fragHash + ":" + valueHash);

                // 检查是否包含非确定性函数
                if (containsNonDeterministicFunction(vc.getSqlFragment())) {
                    result.hasNonDeterministic = true;
                }
            } else if (c instanceof JdbcQuery.ListValueCond lvc) {
                // IN 条件
                String valueHash = computeValueHash(lvc.getValue());
                String fragHash = DigestUtils.md5Hex(lvc.getSqlFragment());
                result.signatures.add("list:" + fragHash + ":" + valueHash);
            } else if (c instanceof JdbcQuery.JdbcGroupCond gc) {
                // 分组条件，递归处理
                result.signatures.add("group:" + gc.getLink() + ":(");
                extractConditionsRecursive(gc, result);
                result.signatures.add(")");
            }
        }
    }

    /**
     * 从 request 提取条件签名（不包含权限条件）
     */
    private ConditionExtractResult extractConditionsFromRequest(DbQueryRequestDef request) {
        ConditionExtractResult result = new ConditionExtractResult();

        if (request.getSlice() != null) {
            for (SliceRequestDef slice : request.getSlice()) {
                extractSliceRecursive(slice, result);
            }
        }

        Collections.sort(result.signatures);
        return result;
    }

    /**
     * 递归提取 slice 条件
     */
    private void extractSliceRecursive(SliceRequestDef slice, ConditionExtractResult result) {
        if (slice._hasChildren()) {
            result.signatures.add("group:" + slice.getLink() + ":(");
            for (var child : slice.getChildren()) {
                extractSliceRecursive(child, result);
            }
            result.signatures.add(")");
        } else {
            String valueHash = computeValueHash(slice.getValue());
            result.signatures.add(slice.getField() + ":" + slice.getOp() + ":" + valueHash);
        }
    }

    /**
     * 计算值的哈希
     */
    private String computeValueHash(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            String joined = list.stream()
                    .map(Object::toString)
                    .sorted()
                    .collect(Collectors.joining(","));
            return DigestUtils.md5Hex(joined);
        }
        return DigestUtils.md5Hex(value.toString());
    }

    /**
     * 检查 SQL 片段是否包含非确定性函数
     */
    private boolean containsNonDeterministicFunction(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        String upperSql = sql.toUpperCase();
        for (String func : NON_DETERMINISTIC_FUNCTIONS) {
            if (upperSql.contains(func + "(")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 条件提取结果
     */
    private static class ConditionExtractResult {
        List<String> signatures = new ArrayList<>();
        boolean hasRawSql = false;
        boolean hasNonDeterministic = false;
    }
}
