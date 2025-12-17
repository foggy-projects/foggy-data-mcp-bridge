package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelResultContext {
    PagingRequest<JdbcQueryRequestDef> request;

    PagingResultImpl pagingResult;

    /**
     * 本次查询用到的模型
     */
    JdbcQueryModel jdbcQueryModel;
    /**
     * 本次查询生成的查询对象
     */
    JdbcQuery jdbcQuery;

    /**
     * 查询类型标识
     */
    QueryType queryType = QueryType.NORMAL;

    /**
     * 扩展数据，用于filter传递自定义数据
     */
    Map<String, Object> extData = new HashMap<>();

    /**
     * 安全上下文，用于权限控制
     */
    SecurityContext securityContext;

    // ==========================================
    // 内联表达式预处理结果
    // ==========================================

    /**
     * 内联表达式预处理结果
     * <p>
     * 由 InlineExpressionPreprocessStep 填充，供后续 Step 和 QueryEngine 使用。
     * 避免多次解析相同的内联表达式。
     * </p>
     */
    ParsedInlineExpressions parsedInlineExpressions;

    /**
     * 处理后的计算字段列表
     * <p>
     * 由 QueryEngine 填充，包含编译后的计算字段 SQL 表达式。
     * </p>
     */
    List<CalculatedJdbcColumn> calculatedColumns;

    /**
     * 内联表达式预处理结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedInlineExpressions {
        /**
         * 转换后的 columns（内联表达式替换为别名）
         */
        List<String> columns;

        /**
         * 从内联表达式提取的 calculatedFields
         */
        List<CalculatedFieldDef> calculatedFields;

        /**
         * 别名 -> 解析结果映射（供 AutoGroupBy 使用）
         */
        Map<String, InlineExpressionParser.InlineExpression> aliasToExpression;

        /**
         * 是否已预处理
         */
        public boolean isProcessed() {
            return columns != null;
        }
    }

    /**
     * 查询类型枚举
     */
    public enum QueryType {
        /**
         * 普通查询
         */
        NORMAL,
        /**
         * AI/语义查询（包含$caption字段）
         */
        SEMANTIC
    }

    /**
     * 安全上下文
     *
     * <p>包含请求的授权信息，用于数据权限控制。
     * 可在 DataSetResultStep.beforeQuery() 中根据此信息添加过滤条件。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityContext {
        /**
         * 原始授权头（如 Bearer token）
         */
        private String authorization;

        /**
         * 用户ID（从 token 解析）
         */
        private String userId;

        /**
         * 用户角色列表
         */
        private List<String> roles;

        /**
         * 租户ID（多租户场景）
         */
        private String tenantId;

        /**
         * 部门ID（数据权限场景）
         */
        private String deptId;

        /**
         * 额外属性
         */
        private Map<String, Object> attributes;

        /**
         * 便捷方法：从 authorization 头创建
         */
        public static SecurityContext fromAuthorization(String authorization) {
            return SecurityContext.builder()
                    .authorization(authorization)
                    .build();
        }

        /**
         * 获取额外属性
         */
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String key) {
            if (attributes == null) {
                return null;
            }
            return (T) attributes.get(key);
        }

        /**
         * 设置额外属性
         */
        public void setAttribute(String key, Object value) {
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            attributes.put(key, value);
        }
    }

    public ModelResultContext(PagingRequest<JdbcQueryRequestDef> request, PagingResultImpl pagingResult) {
        this.request = request;
        this.pagingResult = pagingResult;
    }

    /**
     * 带安全上下文的构造函数
     */
    public ModelResultContext(PagingRequest<JdbcQueryRequestDef> request, PagingResultImpl pagingResult,
                              SecurityContext securityContext) {
        this.request = request;
        this.pagingResult = pagingResult;
        this.securityContext = securityContext;
    }
}
