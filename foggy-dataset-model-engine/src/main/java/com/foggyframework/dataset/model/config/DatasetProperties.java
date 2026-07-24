package com.foggyframework.dataset.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Foggy Dataset Model 配置属性
 * <p>
 * 配置前缀: foggy.dataset
 * 作用范围: foggy-dataset-model 模块
 *
 * <h3>配置示例：</h3>
 * <pre>
 * foggy:
 *   dataset:
 *     show-sql: true
 *     sql-format: false
 *     sql-log-level: DEBUG
 *     show-sql-parameters: true
 *     show-execution-time: true
 *     templates-path: classpath:/foggy/templates/
 * </pre>
 *
 * @author foggy-dataset-model
 * @since 8.1.10.beta
 */
@Data
public class DatasetProperties {

    /**
     * 是否在日志中打印生成的 SQL 语句
     * <p>默认: false（生产环境建议关闭）
     * <p>开发调试时建议开启，可以帮助理解 TM/QM 模型如何转换为 SQL
     *
     * <h3>示例输出：</h3>
     * <pre>
     * ========== Foggy Dataset SQL ==========
     * SELECT t0.order_id, t0.total_amount FROM fact_order t0 WHERE t0.status = ?
     * Parameters: [COMPLETED]
     * SQL execution time [FactOrderQueryModel]: 45 ms
     * =======================================
     * </pre>
     */
    private boolean showSql = false;

    /**
     * 是否格式化 SQL（多行显示）
     * <p>默认: false（单行，适合日志查找和grep）
     * <p>true: 多行显示，更易读但占用更多日志空间
     *
     * <h3>格式化效果：</h3>
     * <pre>
     * SELECT t0.order_id
     *   FROM fact_order t0
     *   LEFT JOIN dim_customer t1 ON t0.customer_id = t1.customer_id
     *   WHERE t0.status = ?
     *   ORDER BY t0.order_time DESC
     * </pre>
     */
    private boolean sqlFormat = false;

    /**
     * SQL 日志级别
     * <p>可选值: DEBUG, INFO
     * <p>默认: DEBUG
     * <p>建议开发环境使用 DEBUG，生产环境如需查看可使用 INFO
     */
    private String sqlLogLevel = "DEBUG";

    /**
     * 是否显示 SQL 参数值
     * <p>默认: true
     * <p>⚠️ 安全提示：参数可能包含敏感信息（如用户ID、金额等）
     * <p>生产环境建议根据安全策略决定是否开启
     */
    private boolean showSqlParameters = true;

    /**
     * 是否显示 SQL 执行时间
     * <p>默认: true
     * <p>帮助识别慢查询，优化性能
     */
    private boolean showExecutionTime = true;

    /**
     * 模型文件路径
     * <p>TM/QM 模型文件的存放位置
     * <p>默认: classpath:/foggy/templates/
     *
     * <h3>支持的路径格式：</h3>
     * <ul>
     *   <li>classpath:/foggy/templates/ - 类路径</li>
     *   <li>file:/data/models/ - 文件系统绝对路径</li>
     * </ul>
     */
    private String templatesPath = "classpath:/foggy/templates/";

    /**
     * 是否在应用启动时校验所有 QM 文件
     * <p>默认: false
     * <p>开启后会在启动时加载并校验所有 .qm 文件，提前发现配置错误
     * <p>适合开发和测试环境，生产环境可根据需要开启
     *
     * <h3>配置示例：</h3>
     * <pre>
     * foggy:
     *   dataset:
     *     validate-on-startup: true
     * </pre>
     */
    private boolean validateOnStartup = false;
    /**
     * 当查询请求中，没有指定 limit 时，默认的 limit 值
     */
    private int defaultLimit = 1000;

    /**
     * 请求入口默认值。
     */
    private RequestConfig request = new RequestConfig();

    /**
     * 数据源解析策略。
     */
    private DataSourceConfig datasource = new DataSourceConfig();

    /**
     * 语义缩放加载策略。
     * <p>默认启用 semanticScaleFactor；需要保留物理单位的命名空间显式加入 disabledNamespaces。</p>
     */
    private SemanticScaleConfig semanticScale = new SemanticScaleConfig();

    /**
     * Pivot engine configuration.
     */
    private PivotConfig pivot = new PivotConfig();

    @Data
    public static class RequestConfig {

        /**
         * API/MCP 调用未传 namespace 时使用的默认 namespace。
         * <p>默认空字符串，保持底层默认命名空间兼容语义。</p>
         */
        private String defaultNamespace = "";
    }

    @Data
    public static class DataSourceConfig {

        /**
         * 是否允许非空 namespace 在没有默认数据源绑定时回退到全局数据源。
         * <p>默认 false。该行为会扩大数据访问范围，仅用于显式迁移兼容。</p>
         */
        private boolean allowGlobalFallbackForNamespace = false;
    }

    @Data
    public static class SemanticScaleConfig {

        /**
         * semanticScaleFactor 默认是否启用。
         */
        private boolean defaultEnabled = true;

        /**
         * 禁用 semanticScaleFactor 的命名空间列表。
         */
        private List<String> disabledNamespaces = new ArrayList<>();
    }

    @Data
    public static class PivotConfig {

        /**
         * Outer response cache for fully-shaped Pivot responses.
         */
        private OuterCacheConfig outerCache = new OuterCacheConfig();
    }

    @Data
    public static class OuterCacheConfig {

        /**
         * Default false. E1b cache return path must be explicitly enabled.
         */
        private boolean enabled = false;

        /**
         * Conservative local TTL for cached Pivot responses.
         */
        private long ttlMillis = 300_000L;

        /**
         * Maximum entries kept by the local in-memory cache.
         */
        private int maximumSize = 256;

        /**
         * Deployment-provided model bundle fingerprint.
         * <p>Use a signed registry version, bundle digest, git SHA, or release
         * artifact hash when the host application has one. A changed value
         * forces fresh Pivot outer-cache keys without waiting for TTL expiry.</p>
         */
        private String bundleFingerprint = "";

        /**
         * Optional deployment freshness token for model files or datasource
         * snapshots.
         * <p>Use this as an operator-controlled bump token when the registry
         * cannot yet expose a signed bundle hash directly.</p>
         */
        private String modelFreshnessToken = "";

        /**
         * Enables the internal REST endpoint for manual Pivot outer-cache
         * invalidation. Default false to avoid exposing operational controls
         * unless the host application opts in.
         */
        private boolean adminEndpointEnabled = false;

        /**
         * Whether an unavailable external cache provider should fail queries.
         * <p>Default false keeps Redis/KV style providers optional: missing or
         * unreachable external cache storage degrades to cache miss/no-op and
         * must not block engine startup or query execution.</p>
         */
        private boolean failOnProviderUnavailable = false;
    }
}
