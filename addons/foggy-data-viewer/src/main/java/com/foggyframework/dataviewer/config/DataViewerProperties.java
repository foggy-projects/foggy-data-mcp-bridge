package com.foggyframework.dataviewer.config;

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
     * 浏览器链接的基础URL
     */
    private String baseUrl = "http://localhost:8080/data-viewer";

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
}
