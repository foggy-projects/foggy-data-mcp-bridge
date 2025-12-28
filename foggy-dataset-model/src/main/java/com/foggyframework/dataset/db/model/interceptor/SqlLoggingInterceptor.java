package com.foggyframework.dataset.db.model.interceptor;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SQL 日志拦截器
 * <p>当 {@code foggy.dataset.show-sql=true} 时自动启用
 *
 * <h3>功能特性：</h3>
 * <ul>
 *   <li>记录生成的 SQL 语句</li>
 *   <li>记录 SQL 参数（可选）</li>
 *   <li>记录 SQL 执行时间（可选）</li>
 *   <li>支持 SQL 格式化（可选）</li>
 *   <li>可配置日志级别（DEBUG/INFO）</li>
 * </ul>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * foggy:
 *   dataset:
 *     show-sql: true              # 必须为 true 才会启用此组件
 *     sql-format: false           # 是否格式化
 *     sql-log-level: DEBUG        # 日志级别
 *     show-sql-parameters: true   # 是否显示参数
 *     show-execution-time: true   # 是否显示执行时间
 * </pre>
 *
 * @author foggy-dataset-model
 * @since 8.0.1-beta
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "foggy.dataset", name = "show-sql", havingValue = "true")
public class SqlLoggingInterceptor {

    private final DatasetProperties properties;

    public SqlLoggingInterceptor(DatasetProperties properties) {
        this.properties = properties;
    }

    /**
     * 记录 SQL 执行
     *
     * @param sql        SQL 语句
     * @param parameters SQL 参数列表（预编译参数）
     */
    public void logSql(String sql, List<Object> parameters) {
        if (!properties.isShowSql()) {
            return;
        }

        String formattedSql = properties.isSqlFormat()
                ? formatSql(sql)
                : sql;

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n========== Foggy Dataset SQL ==========\n");
        logMessage.append(formattedSql);

        if (properties.isShowSqlParameters() && parameters != null && !parameters.isEmpty()) {
            logMessage.append("\n");
            logMessage.append("Parameters: ").append(formatParameters(parameters));
        }

        logMessage.append("\n=======================================");

        logByLevel(logMessage.toString());
    }

    /**
     * 记录 SQL 执行时间
     *
     * @param modelName   查询模型名称
     * @param durationMs  执行时间（毫秒）
     */
    public void logExecutionTime(String modelName, long durationMs) {
        if (!properties.isShowExecutionTime()) {
            return;
        }

        String message = String.format("SQL execution time [%s]: %d ms", modelName, durationMs);
        logByLevel(message);
    }

    /**
     * 记录 SQL 执行时间（带 SQL 语句）
     *
     * @param sql         SQL 语句
     * @param durationMs  执行时间（毫秒）
     */
    public void logExecutionTimeWithSql(String sql, long durationMs) {
        if (!properties.isShowExecutionTime()) {
            return;
        }

        String shortSql = sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
        String message = String.format("SQL execution time: %d ms | SQL: %s", durationMs, shortSql);
        logByLevel(message);
    }

    /**
     * 根据配置的日志级别输出日志
     */
    private void logByLevel(String message) {
        String level = properties.getSqlLogLevel();
        if ("INFO".equalsIgnoreCase(level)) {
            log.info(message);
        } else {
            log.debug(message);
        }
    }

    /**
     * 简单的 SQL 格式化
     * <p>在关键字后换行，提高可读性
     */
    private String formatSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        return sql
                // 在关键字前换行并缩进
                .replaceAll("(?i)\\s+(SELECT|FROM|WHERE|LEFT JOIN|INNER JOIN|RIGHT JOIN|GROUP BY|ORDER BY|HAVING|LIMIT|OFFSET)", "\n  $1")
                // 在 AND/OR 前换行并缩进
                .replaceAll("(?i)\\s+(AND|OR)\\s+", "\n    $1 ")
                // 压缩多余空格
                .replaceAll("\\s+", " ")
                // 清理首尾空格
                .trim();
    }

    /**
     * 格式化参数列表
     * <p>将参数值转换为易读的字符串格式
     */
    private String formatParameters(List<Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            Object param = parameters.get(i);
            if (param == null) {
                sb.append("NULL");
            } else if (param instanceof String) {
                sb.append("'").append(param).append("'");
            } else {
                sb.append(param);
            }
        }
        sb.append("]");

        return sb.toString();
    }
}
