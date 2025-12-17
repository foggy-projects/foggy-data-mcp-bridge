package com.foggyframework.dataset.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 服务配置属性
 *
 * <p>MCP (Model Context Protocol) 服务的核心配置类，包含：
 * <ul>
 *   <li>数据集访问模式配置（本地/远程）</li>
 *   <li>语义模型配置（可查询的模型列表、字段级别控制）</li>
 *   <li>服务端口和分层配置</li>
 *   <li>AI Agent 配置</li>
 *   <li>外部服务连接配置</li>
 *   <li>MCP 工具配置</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /**
     * 数据集访问配置
     */
    private DatasetConfig dataset = new DatasetConfig();

    /**
     * 语义模型配置
     *
     * <p>控制 AI 可以访问的数据模型列表及返回字段的级别。
     * 通过 level 机制可以精细控制暴露给 AI 的字段数量，
     * 避免在模型字段很多时返回过多无关信息。
     */
    private SemanticConfig semantic = new SemanticConfig();

    /**
     * 服务配置
     */
    private ServiceConfig service = new ServiceConfig();

    /**
     * Agent 配置
     */
    private AgentConfig agent = new AgentConfig();

    /**
     * 外部服务配置
     */
    private ExternalConfig external = new ExternalConfig();

    /**
     * 工具配置列表
     */
    private List<ToolConfigItem> tools = new ArrayList<>();

    /**
     * 审计日志配置
     */
    private AuditConfig audit = new AuditConfig();

    /**
     * 语义模型配置
     *
     * <p>用于控制 MCP 工具暴露给 AI 的数据模型和字段范围。
     *
     * <h3>配置示例：</h3>
     * <pre>
     * mcp:
     *   semantic:
     *     model-list:
     *       - FactSalesQueryModel
     *       - FactOrderQueryModel
     *     metadata:
     *       default-levels: [1]
     *       force-levels: [9]
     *     internal:
     *       default-levels: [1]
     *       force-levels: [9]
     * </pre>
     *
     * <p>注：V2 版本服务已移除，统一使用 V3 版本。
     */
    @Data
    public static class SemanticConfig {
        /**
         * 可用的查询模型列表
         *
         * <p>定义 AI 可以查询的数据模型名称列表。
         * 这些模型名称对应 .qm 文件中定义的语义模型。
         *
         * <p>示例：
         * <pre>
         * model-list:
         *   - FactSalesQueryModel      # 销售事实模型
         *   - FactOrderQueryModel      # 订单事实模型
         *   - CustomerDimModel         # 客户维度模型
         * </pre>
         */
        private List<String> modelList = new ArrayList<>();

        /**
         * 元数据查询的字段级别配置
         *
         * <p>用于 {@code dataset.get_metadata} 工具调用，
         * 控制返回所有可用模型时包含的字段信息深度。
         *
         * <p>当用户调用 get_metadata 获取可用模型列表时，
         * 不需要返回每个字段的完整定义，只需返回基本概览。
         */
        private LevelConfig metadata = new LevelConfig();

        /**
         * 模型内部描述的字段级别配置
         *
         * <p>用于 {@code dataset.describe_model_internal} 工具调用，
         * 控制查看单个模型详情时返回的字段信息深度。
         *
         * <p>当用户需要了解某个模型的具体字段定义时，
         * 可以返回更详细的信息。
         */
        private LevelConfig internal = new LevelConfig();
    }

    /**
     * 字段级别控制配置
     *
     * <p>每个字段在定义时都可以设置 {@code ai.level} 属性（默认为1），
     * 用于控制该字段在不同场景下是否返回给 AI。
     *
     * <h3>Level 机制说明：</h3>
     * <ul>
     *   <li>level=1: 核心字段，始终返回</li>
     *   <li>level=2-8: 扩展字段，按需返回</li>
     *   <li>level=9: 所有字段，用于完整信息</li>
     * </ul>
     *
     * <h3>配置优先级：</h3>
     * <ol>
     *   <li>如果配置了 force-levels，则忽略用户请求，强制使用此配置</li>
     *   <li>如果用户请求未指定 levels，则使用 default-levels</li>
     *   <li>如果用户请求指定了 levels 且没有 force-levels，则使用用户请求的值</li>
     * </ol>
     *
     * <h3>使用场景：</h3>
     * <ul>
     *   <li>force-levels: 用于生产环境限制 AI 访问敏感字段，或统一控制返回字段范围</li>
     *   <li>default-levels: 用于设置合理的默认值，用户仍可自行调整</li>
     * </ul>
     */
    @Data
    public static class LevelConfig {
        /**
         * 强制级别列表
         *
         * <p>如果配置了此项，将忽略用户请求中的 levels 参数，
         * 强制使用此配置的级别列表。
         *
         * <p>用途：
         * <ul>
         *   <li>防止用户（或 AI）请求过多字段导致响应过大</li>
         *   <li>在生产环境中限制敏感字段的暴露</li>
         *   <li>统一控制所有请求的返回字段范围</li>
         * </ul>
         *
         * <p>示例：{@code force-levels: [9]} 表示强制返回所有级别的字段
         */
        private List<Integer> forceLevels;

        /**
         * 默认级别列表
         *
         * <p>当用户请求未指定 levels 参数时使用的默认值。
         * 如果同时配置了 force-levels，则此配置无效。
         *
         * <p>示例：{@code default-levels: [1]} 表示默认只返回 level=1 的核心字段
         */
        private List<Integer> defaultLevels = List.of(1);

        /**
         * 应用级别配置到请求
         *
         * <p>处理逻辑：
         * <ol>
         *   <li>如果配置了 forceLevels，无条件使用 forceLevels</li>
         *   <li>如果请求未指定 levels 或为空，使用 defaultLevels</li>
         *   <li>否则保留请求中的 levels 不变</li>
         * </ol>
         *
         * @param requestLevels 请求中的 levels 参数（可能为 null 或空）
         * @return 处理后应该使用的 levels
         */
        public List<Integer> apply(List<Integer> requestLevels) {
            // 优先级1：强制级别覆盖一切
            if (forceLevels != null && !forceLevels.isEmpty()) {
                return forceLevels;
            }
            // 优先级2：请求未指定时使用默认值
            if (requestLevels == null || requestLevels.isEmpty()) {
                return defaultLevels;
            }
            // 优先级3：使用请求指定的级别
            return requestLevels;
        }
    }

    @Data
    public static class DatasetConfig {
        /**
         * 数据集访问模式
         * - local: 本地直接调用（单体服务模式，直接调用 SemanticService）
         * - remote: HTTP 远程调用（微服务模式，通过 WebClient 调用）
         */
        private String accessMode = "local";

        /**
         * 是否为本地模式
         */
        public boolean isLocalMode() {
            return "local".equalsIgnoreCase(accessMode);
        }

        /**
         * 是否为远程模式
         */
        public boolean isRemoteMode() {
            return "remote".equalsIgnoreCase(accessMode);
        }
    }

    @Data
    public static class ServiceConfig {
        /**
         * M1 智能Agent接口端口
         */
        private int m1Port = 7108;

        /**
         * M2 数据分析师接口端口
         */
        private int m2Port = 7109;

        /**
         * 启用的服务层
         */
        private String enabledLayer = "both";

        /**
         * 当前服务层标识
         */
        private String currentLayer = "default";
    }

    @Data
    public static class AgentConfig {
        private M2QueryExpertConfig m2QueryExpert = new M2QueryExpertConfig();
    }

    @Data
    public static class M2QueryExpertConfig {
        /**
         * 最大迭代次数
         */
        private int maxIterations = 10;

        /**
         * 最大连续错误次数
         */
        private int maxConsecutiveErrors = 3;

        /**
         * 超时时间（秒）
         */
        private int timeoutSeconds = 120;
    }

    @Data
    public static class ExternalConfig {
        /**
         * 数据查询层服务配置
         */
        private DatasetQueryConfig datasetQuery = new DatasetQueryConfig();

        /**
         * 图表渲染服务配置
         */
        private ChartRenderConfig chartRender = new ChartRenderConfig();
    }

    @Data
    public static class DatasetQueryConfig {
        private String baseUrl = "http://localhost:8080";
        private int timeoutSeconds = 30;
    }

    @Data
    public static class ChartRenderConfig {
        private String baseUrl = "http://localhost:3000";
        private String authToken = "default-render-token";
        private int timeoutSeconds = 60;
    }

    /**
     * 工具配置项
     */
    @Data
    public static class ToolConfigItem {
        /**
         * 工具名称（唯一标识）
         */
        private String name;

        /**
         * 是否启用此工具（默认 true）
         * <p>设为 false 时工具不会注册到 MCP
         */
        private boolean enabled = true;

        /**
         * 描述文件路径（classpath资源路径）
         */
        private String descriptionFile;

        /**
         * Schema文件路径（classpath资源路径）
         */
        private String schemaFile;

        /**
         * 工具分类
         */
        private String category;
    }

    /**
     * 审计日志配置
     *
     * <p>用于记录工具调用日志到 MongoDB，支持追踪和分析 AI 工具使用情况。
     *
     * <h3>配置示例：</h3>
     * <pre>
     * mcp:
     *   audit:
     *     enabled: true
     *     mask-authorization: true
     *     tools:
     *       - dataset.query_model_v2
     *       - dataset.export_with_chart
     *     mongodb:
     *       collection: mcp_tool_audit_log
     * </pre>
     */
    @Data
    public static class AuditConfig {
        /**
         * 是否启用审计日志
         */
        private boolean enabled = false;

        /**
         * 是否对 authorization 进行脱敏处理
         * <p>默认开启，将 authorization 替换为 "***"
         */
        private boolean maskAuthorization = true;

        /**
         * 需要记录审计日志的工具列表
         * <p>如果为空，则记录所有工具调用
         */
        private List<String> tools = List.of(
                "dataset.query_model_v2",
                "dataset.export_with_chart"
        );

        /**
         * MongoDB 配置
         */
        private MongodbConfig mongodb = new MongodbConfig();

        /**
         * 检查指定工具是否需要记录审计日志
         */
        public boolean shouldAudit(String toolName) {
            if (!enabled) {
                return false;
            }
            if (tools == null || tools.isEmpty()) {
                return true; // 空列表表示记录所有
            }
            return tools.contains(toolName);
        }

        /**
         * 对 authorization 进行脱敏处理
         */
        public String maskAuthorizationValue(String authorization) {
            if (!maskAuthorization || authorization == null || authorization.isBlank()) {
                return authorization;
            }
            // 保留前缀（如 Bearer），隐藏实际 token
            if (authorization.startsWith("Bearer ") && authorization.length() > 15) {
                return "Bearer ***" + authorization.substring(authorization.length() - 4);
            }
            if (authorization.length() > 8) {
                return authorization.substring(0, 4) + "***" + authorization.substring(authorization.length() - 4);
            }
            return "***";
        }
    }

    /**
     * MongoDB 审计日志配置
     */
    @Data
    public static class MongodbConfig {
        /**
         * 集合名称
         */
        private String collection = "mcp_tool_audit_log";
    }
}
