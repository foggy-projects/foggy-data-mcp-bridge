package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.Data;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询执行上下文
 * <p>
 * 在 SQL 构建后、执行前/后传递的上下文信息。
 * 供 {@link QueryExecutionStep} 使用。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class QueryExecutionContext {

    /**
     * 系统上下文
     */
    private SystemBundlesContext systemBundlesContext;

    /**
     * 模型结果上下文（包含请求信息、缓存配置等）
     */
    private ModelResultContext modelResultContext;

    /**
     * 查询引擎（包含已构建的 SQL 信息）
     */
    private JdbcModelQueryEngine queryEngine;

    /**
     * 数据源
     */
    private DataSource dataSource;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * Prepare 阶段的选项
     */
    private ManagedRelationOptions managedRelationOptions;

    // ==================== SQL 相关 ====================

    /**
     * 当前 SQL（可被 Step 修改）
     */
    private String sql;

    /**
     * SQL 参数（可被 Step 修改）
     */
    private List<Object> params;

    /**
     * 分页 SQL（带 LIMIT/OFFSET）
     */
    private String pagingSql;

    // ==================== 聚合查询预聚合优化 ====================

    /**
     * 预聚合聚合 SQL（用于 returnTotal 优化）
     * <p>
     * 当主查询是明细查询但聚合查询可以使用预聚合时，
     * 此字段存储优化后的聚合 SQL。
     * </p>
     */
    private String preAggAggregateSql;

    /**
     * 预聚合聚合 SQL 参数
     */
    private List<Object> preAggAggregateParams;

    /**
     * 预聚合聚合 SQL 使用的预聚合名称
     */
    private String preAggAggregatePreAggName;

    // ==================== 执行控制 ====================

    /**
     * 是否跳过 SQL 执行（如缓存命中）
     */
    private boolean skipExecution;

    /**
     * 预置结果（如缓存命中时设置）
     */
    private PagingResultImpl cachedResult;

    // ==================== 执行结果 ====================

    /**
     * SQL 执行结果
     */
    private PagingResultImpl executionResult;

    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;

    // ==================== 扩展数据 ====================

    /**
     * 扩展数据（供 Step 间传递自定义数据）
     */
    private Map<String, Object> extData = new HashMap<>();

    /**
     * 获取扩展数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtData(String key) {
        return (T) extData.get(key);
    }

    /**
     * 设置扩展数据
     */
    public void setExtData(String key, Object value) {
        extData.put(key, value);
    }

    /**
     * 获取最终结果（优先返回缓存结果）
     */
    public PagingResultImpl getResult() {
        return cachedResult != null ? cachedResult : executionResult;
    }
}
