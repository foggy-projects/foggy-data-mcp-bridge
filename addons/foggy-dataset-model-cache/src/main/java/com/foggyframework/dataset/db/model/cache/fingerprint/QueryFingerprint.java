package com.foggyframework.dataset.db.model.cache.fingerprint;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

/**
 * 查询指纹
 * <p>
 * 基于完整的查询信息（包含权限条件）计算，用于缓存键生成。
 * 在 beforeQuery Step 执行后构建，确保包含所有运行时注入的条件。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
@Builder
public class QueryFingerprint {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 查询列（排序后）
     */
    private List<String> columns;

    /**
     * 分组列（排序后）
     */
    private List<String> groupBy;

    /**
     * 条件签名列表
     * <p>
     * 格式: field:op:valueHash，如 "customerId:=:abc123"
     * 包含权限注入的条件。
     * </p>
     */
    private List<String> conditionSignatures;

    /**
     * 排序签名
     */
    private List<String> orderBy;

    /**
     * 分页参数
     */
    private int pageNo;
    private int pageSize;

    /**
     * 是否包含原始 SQL 片段
     * <p>
     * 包含原始 SQL 的查询不可缓存，因为无法准确判断其影响范围。
     * </p>
     */
    private boolean hasRawSql;

    /**
     * 是否包含非确定性函数
     * <p>
     * 如 RAND(), NOW(), UUID() 等，每次执行结果不同。
     * </p>
     */
    private boolean hasNonDeterministic;

    /**
     * 计算字段数量
     */
    private int calculatedFieldCount;

    /**
     * 判断是否可缓存
     *
     * @return true 表示可缓存
     */
    public boolean isCacheable() {
        return !hasRawSql && !hasNonDeterministic;
    }

    /**
     * 计算缓存键
     * <p>
     * 返回格式: qc:{modelName}:{hash}
     * </p>
     *
     * @return 缓存键，如果不可缓存返回 null
     */
    public String toCacheKey() {
        if (!isCacheable()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("c:").append(joinOrEmpty(columns)).append("|");
        sb.append("g:").append(joinOrEmpty(groupBy)).append("|");
        sb.append("w:").append(joinOrEmpty(conditionSignatures)).append("|");
        sb.append("o:").append(joinOrEmpty(orderBy)).append("|");
        sb.append("p:").append(pageNo).append("-").append(pageSize);

        // 使用 MD5 压缩
        String hash = DigestUtils.md5Hex(sb.toString());
        return "qc:" + modelName + ":" + hash;
    }

    /**
     * 生成用于调试的可读键
     */
    public String toDebugKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("QueryFingerprint{");
        sb.append("model=").append(modelName);
        sb.append(", columns=").append(columns);
        sb.append(", groupBy=").append(groupBy);
        sb.append(", conditions=").append(conditionSignatures);
        sb.append(", orderBy=").append(orderBy);
        sb.append(", page=").append(pageNo).append("/").append(pageSize);
        sb.append(", cacheable=").append(isCacheable());
        if (hasRawSql) sb.append(", hasRawSql");
        if (hasNonDeterministic) sb.append(", hasNonDeterministic");
        sb.append("}");
        return sb.toString();
    }

    private String joinOrEmpty(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }
}
