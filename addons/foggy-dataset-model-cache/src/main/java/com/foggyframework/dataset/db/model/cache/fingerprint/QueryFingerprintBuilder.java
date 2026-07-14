package com.foggyframework.dataset.db.model.cache.fingerprint;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;

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
        JdbcQuery jdbcQuery = context.getQuery();

        QueryFingerprint.QueryFingerprintBuilder builder = QueryFingerprint.builder()
                .modelName(request.getQueryModel())
                .pageNo(context.getRequest().getPage())
                .pageSize(context.getRequest().getPageSize());

        Optional<SecurityPolicyFingerprint> securityPolicy = SecurityPolicyFingerprint.from(context);
        if (securityPolicy.isPresent()) {
            SecurityPolicyFingerprint policy = securityPolicy.get();
            builder.fieldAccessHash(policy.fieldAccessHash())
                    .deniedColumnsHash(policy.deniedColumnsHash())
                    .systemSliceHash(policy.systemSliceHash())
                    .securityContextHash(policy.securityContextHash())
                    .securityPolicyHash(policy.combinedHash());
        } else {
            builder.hasIncompleteSecurityPolicy(true);
        }

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
            builder.hasUnsupportedValue(result.hasUnsupportedValue);
        } else {
            // 回退：从 request 提取
            ConditionExtractResult result = extractConditionsFromRequest(request);
            builder.conditionSignatures(result.signatures);
            builder.hasRawSql(result.hasRawSql);
            builder.hasNonDeterministic(result.hasNonDeterministic);
            builder.hasUnsupportedValue(result.hasUnsupportedValue);
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
                String calcHash = calc.getName() + ":" + StableCanonicalEncoder.sha256(calc.getDeclare());
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
        result.hasRawSql = query.isRawSqlConditionAdded();

        String where = canonicalizeJdbcList(query.getWhere(), result);
        if (where != null) {
            result.signatures.add(nodeSignature("where", where));
        }

        String having = canonicalizeJdbcList(query.getHaving(), result);
        if (having != null) {
            result.signatures.add(nodeSignature("having", having));
        }
        return result;
    }

    /**
     * Canonicalizes a JDBC condition list while preserving its tree and
     * sequence. JDBC links are stored on each child, so globally sorting leaf
     * signatures would erase parentheses and may also change link semantics.
     */
    private String canonicalizeJdbcList(JdbcQuery.JdbcListCond cond, ConditionExtractResult result) {
        if (cond == null || cond.getConds() == null || cond.getConds().isEmpty()) {
            return null;
        }

        List<String> children = new ArrayList<>(cond.getConds().size());
        for (JdbcQuery.JdbcCond c : cond.getConds()) {
            String child = canonicalizeJdbcCondition(c, result);
            if (child != null) {
                children.add(child);
            }
        }
        return encodeSignatures(children);
    }

    private String canonicalizeJdbcCondition(JdbcQuery.JdbcCond condition,
                                             ConditionExtractResult result) {
        if (condition == null) {
            result.hasUnsupportedValue = true;
            return nodeSignature("unsupported", encodeCanonical(null, result));
        }

        String link = StableCanonicalEncoder.segment("link",
                encodeCanonical(condition.getLink(), result));
        if (condition instanceof JdbcQuery.SqlFragmentCond sqlFragment) {
            result.hasRawSql = true;
            if (containsNonDeterministicFunction(sqlFragment.getSqlFragment())) {
                result.hasNonDeterministic = true;
            }
            return nodeSignature("raw-sql", link
                    + StableCanonicalEncoder.segment("sql",
                    encodeCanonical(sqlFragment.getSqlFragment(), result)));
        }
        if (condition instanceof JdbcQuery.QueryTypeValueCond queryTypeValue) {
            return nodeSignature("query-type-value", link
                    + StableCanonicalEncoder.segment("name",
                    encodeCanonical(queryTypeValue.getName(), result))
                    + StableCanonicalEncoder.segment("queryType",
                    encodeCanonical(queryTypeValue.getQueryType(), result))
                    + StableCanonicalEncoder.segment("value",
                    encodeCanonical(queryTypeValue.getValue(), result)));
        }
        if (condition instanceof JdbcQuery.ValueCond valueCondition) {
            if (containsNonDeterministicFunction(valueCondition.getSqlFragment())) {
                result.hasNonDeterministic = true;
            }
            return nodeSignature("sql-value", link
                    + StableCanonicalEncoder.segment("sql",
                    encodeCanonical(valueCondition.getSqlFragment(), result))
                    + StableCanonicalEncoder.segment("value",
                    encodeCanonical(valueCondition.getValue(), result)));
        }
        if (condition instanceof JdbcQuery.ListValueCond listValueCondition) {
            if (containsNonDeterministicFunction(listValueCondition.getSqlFragment())) {
                result.hasNonDeterministic = true;
            }
            return nodeSignature("sql-list-value", link
                    + StableCanonicalEncoder.segment("sql",
                    encodeCanonical(listValueCondition.getSqlFragment(), result))
                    + StableCanonicalEncoder.segment("value",
                    encodeCanonical(listValueCondition.getValue(), result)));
        }
        if (condition instanceof JdbcQuery.JdbcGroupCond group) {
            String children = canonicalizeJdbcList(group, result);
            if (children == null) {
                children = encodeSignatures(Collections.emptyList());
            }
            return nodeSignature("jdbc-group", link
                    + StableCanonicalEncoder.segment("children", children));
        }

        result.hasUnsupportedValue = true;
        return nodeSignature("unsupported", link
                + StableCanonicalEncoder.segment("type",
                encodeCanonical(condition.getClass(), result)));
    }

    /**
     * 从 request 提取条件签名（不包含权限条件）
     */
    private ConditionExtractResult extractConditionsFromRequest(DbQueryRequestDef request) {
        ConditionExtractResult result = new ConditionExtractResult();

        if (request.getSlice() != null) {
            for (SliceRequestDef slice : request.getSlice()) {
                result.signatures.add(canonicalizeSlice(slice, result));
            }
        }

        // Top-level slices are implicitly combined with AND, so only this
        // level is safely commutative. Nested group boundaries remain inside
        // each recursively computed subtree signature.
        Collections.sort(result.signatures);
        return result;
    }

    /**
     * Recursively canonicalizes one slice subtree. Each node is hashed before
     * it is returned so structurally different boolean expressions cannot
     * collapse merely because they contain the same leaves.
     */
    private String canonicalizeSlice(CondRequestDef cond, ConditionExtractResult result) {
        if (cond == null) {
            result.hasUnsupportedValue = true;
            return nodeSignature("unsupported", encodeCanonical(null, result));
        }
        if (cond._isExpressionCondition()) {
            result.hasRawSql = true;
            if (containsNonDeterministicFunction(cond.getExpr())) {
                result.hasNonDeterministic = true;
            }
            return nodeSignature("expression", StableCanonicalEncoder.segment("expression",
                    encodeCanonical(cond.getExpr(), result)));
        }
        if (cond._isLogicalGroup()) {
            List<String> children = new ArrayList<>(cond._getGroupChildren().size());
            for (CondRequestDef child : cond._getGroupChildren()) {
                children.add(canonicalizeSlice(child, result));
            }
            // AND and OR operands are commutative, but only within this exact
            // subtree. Sorting globally would erase parenthesis structure.
            Collections.sort(children);
            return nodeSignature("request-group",
                    StableCanonicalEncoder.segment("link",
                            encodeCanonical(cond._getGroupLink(), result))
                            + StableCanonicalEncoder.segment("children",
                            encodeSignatures(children)));
        }

        return nodeSignature("request-leaf",
                StableCanonicalEncoder.segment("field",
                        encodeCanonical(cond.getField(), result))
                        + StableCanonicalEncoder.segment("op",
                        encodeCanonical(cond.getOp(), result))
                        + StableCanonicalEncoder.segment("value",
                        encodeCanonical(cond.getValue(), result))
                        + StableCanonicalEncoder.segment("maxDepth",
                        encodeCanonical(cond.getMaxDepth(), result)));
    }

    private String encodeCanonical(Object value, ConditionExtractResult result) {
        Optional<String> encoded = StableCanonicalEncoder.encode(value);
        if (encoded.isEmpty()) {
            result.hasUnsupportedValue = true;
            return StableCanonicalEncoder.segment("unsupported", "");
        }
        return encoded.get();
    }

    private String encodeSignatures(List<String> signatures) {
        StringBuilder encoded = new StringBuilder(
                StableCanonicalEncoder.segment("size", Integer.toString(signatures.size())));
        for (String signature : signatures) {
            encoded.append(StableCanonicalEncoder.segment("child", signature));
        }
        return encoded.toString();
    }

    private String nodeSignature(String type, String payload) {
        return type + ":" + StableCanonicalEncoder.sha256(
                StableCanonicalEncoder.segment("type", type)
                        + StableCanonicalEncoder.segment("payload", payload));
    }

    /**
     * 检查 SQL 片段是否包含非确定性函数
     */
    private boolean containsNonDeterministicFunction(String sql) {
        if (sql == null || sql.isEmpty()) {
            return false;
        }
        String upperSql = sql.toUpperCase(Locale.ROOT);
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
        boolean hasUnsupportedValue = false;
    }
}
