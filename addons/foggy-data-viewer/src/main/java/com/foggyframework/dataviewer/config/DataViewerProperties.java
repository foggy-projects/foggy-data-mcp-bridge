package com.foggyframework.dataviewer.config;

import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据浏览器配置属性
 */
@Data
@ConfigurationProperties(prefix = "foggy.data-viewer")
public class DataViewerProperties {

    /**
     * 是否启用数据浏览器
     */
    private boolean enabled = true;

    /**
     * 浏览器链接的基础URL（不配置时自动使用 http://localhost:{server.port}/data-viewer）
     */
    private String baseUrl;

    /**
     * 缓存配置
     */
    private CacheProperties cache = new CacheProperties();

    /**
     * 阈值配置
     */
    private ThresholdProperties thresholds = new ThresholdProperties();

    /**
     * 安全配置
     */
    private SecurityProperties security = new SecurityProperties();

    /**
     * 查询范围约束配置
     */
    private ScopeConstraintProperties scopeConstraints = new ScopeConstraintProperties();

    /**
     * 自定义列表配置
     */
    private ListPresetProperties listPreset = new ListPresetProperties();

    /**
     * 表格实例默认查询 fallback 配置。
     */
    private TableDefaultProperties tableDefaults = new TableDefaultProperties();

    @Data
    public static class CacheProperties {
        /**
         * 缓存过期时间（分钟）
         */
        private int ttlMinutes = 60;

        /**
         * 清理过期条目的间隔（毫秒）
         */
        private long cleanupInterval = 300000;
    }

    @Data
    public static class ThresholdProperties {
        /**
         * 建议使用浏览器的最小行数
         */
        private int largeDatasetMin = 500;

        /**
         * MCP 查询结果自动截断阈值（单元格数量 = 行数 × 列数）
         * <p>
         * 当来自 MCP 的查询结果超过此阈值时，自动截断并返回链接。
         * 默认值 10000，约 100 行 × 100 列。
         */
        private int cellThresholdForTruncation = 10000;

        /**
         * MCP 查询结果截断后保留的行数
         * <p>
         * 截断时保留前 N 行返回给 LLM 作为样本数据。
         * 默认值 100 行。
         */
        private int truncatedRowLimit = 100;
    }

    @Data
    public static class SecurityProperties {
        /**
         * 是否要求与原始查询相同的授权
         */
        private boolean requireAuth = false;
    }

    @Data
    public static class ScopeConstraintProperties {
        /**
         * 是否启用范围约束
         */
        private boolean enabled = true;

        /**
         * 默认最大查询天数
         */
        private int defaultMaxDurationDays = 31;

        /**
         * 每个模型的范围约束配置
         */
        private Map<String, ModelScopeConstraint> models = new HashMap<>();
    }

    @Data
    public static class ModelScopeConstraint {
        /**
         * 范围限制字段（如 orderDate）
         */
        private String scopeField;

        /**
         * 最大允许的查询天数
         */
        private int maxDurationDays = 31;
    }

    @Data
    public static class ListPresetProperties {
        /**
         * 自定义列表存储策略。
         * <p>
         * AUTO: 配置了 spring.data.mongodb.uri 时使用 Mongo，否则使用文件系统。
         * MONGO: 显式使用 Mongo，运行时不可用时降级到文件系统。
         * FILE: 只使用文件系统。
         */
        private Storage storage = Storage.AUTO;

        /**
         * 文件系统降级存储根目录。
         */
        private String fileBaseDir = "data-viewer/list-presets";

        public enum Storage {
            AUTO,
            MONGO,
            FILE
        }
    }

    @Data
    public static class TableDefaultProperties {
        /**
         * 系统级默认配置，key 可使用 tableInstanceId 或 queryModel。
         */
        private Map<String, TableDefaultQueryConfig> system = new HashMap<>();

        /**
         * 租户级默认配置，第一层 key 为 tenantId，第二层 key 可使用 tableInstanceId 或 queryModel。
         */
        private Map<String, Map<String, TableDefaultQueryConfig>> tenants = new HashMap<>();

        /**
         * 角色级默认配置，第一层 key 为 roleId，第二层 key 可使用 tableInstanceId 或 queryModel。
         */
        private Map<String, Map<String, TableDefaultQueryConfig>> roles = new HashMap<>();
    }
}
