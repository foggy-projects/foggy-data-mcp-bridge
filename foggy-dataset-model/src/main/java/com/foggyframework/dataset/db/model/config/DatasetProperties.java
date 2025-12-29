package com.foggyframework.dataset.db.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * @since 8.0.1-beta
 */
@Data
@ConfigurationProperties(prefix = "foggy.dataset")
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
}
