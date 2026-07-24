package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelResultContext {
    PagingRequest<DbQueryRequestDef> request;

    PagingResultImpl pagingResult;

    /**
     * 本次查询用到的模型
     */
    QueryModel queryModel;

    /** Canonical model name resolved by the same catalog view as {@link #queryModel}. */
    @Setter(AccessLevel.NONE)
    String canonicalModelName;

    /** Exact immutable catalog view that supplied {@link #queryModel}. */
    CatalogIdentity catalogIdentity;

    /**
     * Canonical binding-key to generation-pinned datasource identity for the
     * resolved model.  An empty map is valid; {@code bindingIdentityComplete}
     * distinguishes a genuinely datasource-free model from an untracked
     * routing/legacy datasource whose identity cannot safely key a cache.
     */
    Map<String, DatasourceBindingIdentity> datasourceBindingIdentities = Map.of();

    boolean bindingIdentityComplete;
    /**
     * 本次查询生成的查询对象
     */
    JdbcQuery query;

    /**
     * 查询类型标识
     */
    QueryType queryType = QueryType.NORMAL;

    /**
     * 扩展数据，用于filter传递自定义数据
     */
    Map<String, Object> extData = new HashMap<>();

    public void mergeRequestExtData(Object requestExtData) {
        if (requestExtData == null) {
            return;
        }
        if (extData == null) {
            extData = new HashMap<>();
        }
        if (!(requestExtData instanceof Map<?, ?> extDataMap)) {
            extData.put("_requestExtData", requestExtData);
            return;
        }
        for (Map.Entry<?, ?> entry : extDataMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            extData.put(String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    public void setDatasourceBindingIdentities(
            Map<String, DatasourceBindingIdentity> datasourceBindingIdentities) {
        this.datasourceBindingIdentities = datasourceBindingIdentities == null
                ? Map.of()
                : Map.copyOf(datasourceBindingIdentities);
    }

    /** Validate and install one lifecycle pin as a single request-local operation. */
    public synchronized void pinCatalogResolution(
            CatalogResolution<QueryModel> resolution,
            String expectedNamespace
    ) {
        if (resolution == null) {
            throw new IllegalArgumentException("catalog resolution must not be null");
        }
        String canonicalNamespace = CatalogIdentity.canonicalNamespace(expectedNamespace);
        if (!canonicalNamespace.equals(resolution.catalogIdentity().namespace())) {
            throw new IllegalArgumentException("catalog resolution namespace mismatch");
        }
        if (catalogIdentity != null
                && (!catalogIdentity.equals(resolution.catalogIdentity())
                || queryModel != resolution.model()
                || !Objects.equals(canonicalModelName, resolution.canonicalName())
                || !datasourceBindingIdentities.equals(resolution.dependencyBindings())
                || bindingIdentityComplete != resolution.bindingIdentityComplete())) {
            throw new IllegalStateException("CONFLICTING_CATALOG_REPIN");
        }
        queryModel = resolution.model();
        canonicalModelName = resolution.canonicalName();
        catalogIdentity = resolution.catalogIdentity();
        datasourceBindingIdentities = Map.copyOf(resolution.dependencyBindings());
        bindingIdentityComplete = resolution.bindingIdentityComplete();
    }

    /** Compatibility pin for a loader that cannot supply lifecycle identity. */
    public synchronized void pinUntrackedQueryModel(QueryModel model) {
        if (catalogIdentity != null) {
            throw new IllegalStateException("CONFLICTING_CATALOG_REPIN");
        }
        queryModel = model;
        canonicalModelName = null;
        datasourceBindingIdentities = Map.of();
        bindingIdentityComplete = false;
    }

    /**
     * 安全上下文，用于权限控制
     */
    SecurityContext securityContext;

    /**
     * 命名空间（用于模型隔离）
     * <p>空字符串或null表示默认命名空间
     */
    String namespace;

    /**
     * 运行时列权限白名单（由 bridge/上层调用方传入）
     * <p>
     * null 表示不限制（所有字段可访问）；非 null 时，
     * 只有白名单内的字段名才允许出现在 columns / calculatedFields /
     * slice / orderBy / groupBy 中。
     * </p>
     *
     * @since 8.2.0
     */
    Set<String> fieldAccess;

    /**
     * 受限物理列黑名单（由 bridge/上层调用方传入）
     * <p>
     * null 表示不限制；非 null 时，JdbcQuery 中引用的物理列
     * 如果命中黑名单，查询将被拒绝（在 SQL 执行前拦截）。
     * </p>
     *
     * @since 8.2.0
     */
    List<DeniedPhysicalColumn> deniedColumns;

    /**
     * 系统注入的 slice 条件（如 Odoo ir.rule）
     * <p>
     * 绕过字段权限校验，在权限检查通过后合并到请求 slice 中。
     * 对齐 Python 引擎的 system_slice 语义。
     * </p>
     *
     * @since 8.2.0
     */
    List<SliceRequestDef> systemSlice;

    /**
     * 查询缓存配置
     */
    QueryCacheConfig cacheConfig;

    // ==========================================
    // 查询流程控制
    // ==========================================

    /**
     * 预置查询结果
     * <p>
     * 由 Step（如 L1CacheStep）在 beforeQuery 阶段设置。
     * 当此字段非空且 skipQuery=true 时，QueryFacade 将跳过实际查询。
     * </p>
     */
    DbQueryResult queryResult;

    /**
     * 是否跳过后续查询
     * <p>
     * 由 Step 设置，表示已有查询结果（如缓存命中），无需执行实际查询。
     * </p>
     */
    boolean skipQuery;

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
    List<CalculatedDbColumn> calculatedColumns;

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
         * 所有列的聚合类型映射（列名/别名 -> 聚合类型）
         * <p>
         * 聚合类型来源：
         * <ol>
         *   <li>内联表达式 AST 分析（如 sum(totalAmount) → SUM）</li>
         *   <li>内联表达式聚合推断（如 totalAmount+2 → SUM）</li>
         *   <li>QueryModel 字段定义（如 formulaDef 字段的 aggregation）</li>
         * </ol>
         * 只包含有聚合类型的列，不在此 Map 中的列视为非聚合列。
         * </p>
         */
        Map<String, String> columnAggregations;

        /**
         * 是否已预处理
         */
        public boolean isProcessed() {
            return columns != null;
        }

        /**
         * 是否存在聚合列
         */
        public boolean hasAggregation() {
            return columnAggregations != null && !columnAggregations.isEmpty();
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

    /**
     * 查询缓存配置
     *
     * <p>控制 L1（Token 级别）和 L2（SQL 级别）缓存的行为。
     * 可在查询时配置缓存策略，并在查询后获取缓存命中信息。
     *
     * @since 8.2.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryCacheConfig {
        /**
         * 是否启用 L1 缓存（Token 级别）
         * <p>
         * L1 缓存基于授权令牌和请求指纹，可完全跳过 SQL 构建。
         * 默认禁用，需显式启用。
         * </p>
         */
        @Builder.Default
        private boolean l1Enabled = false;

        /**
         * 是否启用 L2 缓存（SQL 级别）
         * <p>
         * L2 缓存基于最终 SQL（含权限条件）和参数，跳过 SQL 执行。
         * 默认启用。
         * </p>
         */
        @Builder.Default
        private boolean l2Enabled = true;

        /**
         * 是否启用预聚合查询优化
         * <p>
         * 启用后，系统会自动检测查询是否可以使用预聚合表，
         * 并在匹配时重写 SQL 以使用预聚合表查询。
         * 默认启用。
         * </p>
         *
         * @since 8.2.0
         */
        @Builder.Default
        private boolean preAggEnabled = true;

        /**
         * 是否启用混合查询（Lambda 架构）
         * <p>
         * 当增量预聚合发布 exclusive watermark 后，系统生成 UNION ALL SQL，
         * 合并物化历史和原始表 tail。默认启用以保证迟到数据完整性；只有明确接受
         * snapshot-only 语义的调用方才应显式关闭。
         * </p>
         *
         * @since 8.2.0
         */
        @Builder.Default
        private boolean hybridQueryEnabled = true;

        /**
         * L1 缓存是否命中（查询后由缓存提供者设置）
         */
        private boolean l1CacheHit;

        /**
         * L2 缓存是否命中（查询后由缓存提供者设置）
         */
        private boolean l2CacheHit;

        /**
         * 预聚合是否命中（查询后设置）
         * <p>
         * 如果查询使用了预聚合表，此字段为 true。
         * </p>
         */
        private boolean preAggHit;

        /**
         * 使用的预聚合名称（查询后设置）
         */
        private String preAggName;

        /**
         * 缓存提供者（由 QueryFacade 注入）
         * <p>
         * 运行时注入的缓存实现，供 QueryModel 使用 L2 缓存。
         * </p>
         */
        private QueryCacheProvider provider;

        /**
         * 便捷方法：创建启用 L1 缓存的配置
         */
        public static QueryCacheConfig enableL1() {
            return QueryCacheConfig.builder()
                    .l1Enabled(true)
                    .l2Enabled(true)
                    .preAggEnabled(true)
                    .build();
        }

        /**
         * 便捷方法：创建禁用所有缓存的配置
         */
        public static QueryCacheConfig disabled() {
            return QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(false)
                    .preAggEnabled(true)
                    .build();
        }

        /**
         * 便捷方法：创建仅启用 L2 缓存的配置（默认行为）
         */
        public static QueryCacheConfig defaultConfig() {
            return QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(true)
                    .preAggEnabled(true)
                    .build();
        }

        /**
         * 便捷方法：创建禁用预聚合的配置
         * <p>
         * 用于测试或需要直接查询原始表的场景。
         * </p>
         */
        public static QueryCacheConfig disablePreAgg() {
            return QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(true)
                    .preAggEnabled(false)
                    .build();
        }

        /**
         * 便捷方法：创建禁用所有优化的配置（用于测试基准）
         */
        public static QueryCacheConfig noOptimization() {
            return QueryCacheConfig.builder()
                    .l1Enabled(false)
                    .l2Enabled(false)
                    .preAggEnabled(false)
                    .build();
        }
    }

    public ModelResultContext(PagingRequest<DbQueryRequestDef> request, PagingResultImpl pagingResult) {
        this.request = request;
        this.pagingResult = pagingResult;
    }

    /**
     * 带安全上下文的构造函数
     */
    public ModelResultContext(PagingRequest<DbQueryRequestDef> request, PagingResultImpl pagingResult,
                              SecurityContext securityContext) {
        this.request = request;
        this.pagingResult = pagingResult;
        this.securityContext = securityContext;
    }

    /**
     * 获取授权令牌
     *
     * @return 授权令牌，或 null
     */
    public String getAuthorization() {

        // 从 SecurityContext 获取
        if (securityContext != null && securityContext.getAuthorization() != null) {
            return securityContext.getAuthorization();
        }
        return null;
    }
}
