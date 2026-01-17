package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 预聚合拦截器
 * <p>
 * 在查询执行前检查是否可以使用预聚合表，并进行查询重写。
 * </p>
 * <p>
 * 支持两种查询模式：
 * <ul>
 *   <li>完全预聚合模式：仅从预聚合表查询</li>
 *   <li>混合查询模式（Lambda 架构）：预聚合表 UNION 原始表</li>
 * </ul>
 * </p>
 * <p>
 * 使用方式：
 * <pre>
 * PreAggregationInterceptor interceptor = new PreAggregationInterceptor(appContext);
 * PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, context);
 * if (result.isApplied()) {
 *     // 使用重写后的 SQL
 *     String sql = result.getSql();
 *     if (result.isHybridQuery()) {
 *         // 混合查询模式
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggregationInterceptor {

    private final ApplicationContext applicationContext;
    private final PreAggregationMatcher matcher;
    private final PreAggQueryRequirementBuilder requirementBuilder;

    /**
     * 是否启用混合查询
     */
    private boolean hybridQueryEnabled = true;

    public PreAggregationInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.matcher = new PreAggregationMatcher();
        this.requirementBuilder = new PreAggQueryRequirementBuilder();
    }

    /**
     * 设置是否启用混合查询
     *
     * @param enabled true 启用混合查询
     */
    public void setHybridQueryEnabled(boolean enabled) {
        this.hybridQueryEnabled = enabled;
        this.matcher.setHybridQueryEnabled(enabled);
    }

    /**
     * 尝试使用预聚合重写查询
     *
     * @param queryEngine 查询引擎（已完成 analysisQueryRequest）
     * @param queryModel  查询模型
     * @param queryRequest 查询请求
     * @return 重写结果
     */
    public PreAggRewriteResult tryRewrite(JdbcModelQueryEngine queryEngine,
                                           JdbcQueryModel queryModel,
                                           DbQueryRequestDef queryRequest) {
        // 1. 获取可用的预聚合列表
        List<PreAggregation> preAggregations = getPreAggregations(queryModel);
        if (preAggregations == null || preAggregations.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No pre-aggregations configured for model: {}", queryModel.getName());
            }
            return PreAggRewriteResult.notApplied();
        }

        // 2. 从查询中提取需求
        JdbcQuery jdbcQuery = queryEngine.getJdbcQuery();
        PreAggQueryRequirement requirement = requirementBuilder.build(queryRequest, jdbcQuery, queryModel);

        if (log.isDebugEnabled()) {
            log.debug("Query requirement: {}", requirement);
        }

        // 3. 匹配最佳预聚合
        PreAggregationMatchResult matchResult = matcher.findBestMatch(requirement, preAggregations);

        if (!matchResult.isMatched()) {
            if (log.isDebugEnabled()) {
                log.debug("No pre-aggregation matched: {}", matchResult.getReason());
            }
            return PreAggRewriteResult.notApplied();
        }

        // 4. 记录匹配结果
        if (matchResult.isHybridQuery()) {
            log.info("Using hybrid query mode for pre-aggregation '{}', watermark={}",
                    matchResult.getPreAggName(), matchResult.getWatermark());
        } else {
            log.info("Using full pre-aggregation query for '{}'", matchResult.getPreAggName());
        }

        // 5. 重写查询
        PreAggQueryRewriter rewriter = new PreAggQueryRewriter(queryModel, applicationContext);
        return rewriter.rewrite(matchResult, jdbcQuery, queryRequest, queryEngine);
    }

    /**
     * 获取模型的预聚合列表
     */
    private List<PreAggregation> getPreAggregations(JdbcQueryModel queryModel) {
        // 从 TableModel 获取预聚合配置
        TableModel tableModel = queryModel.getJdbcModel();
        if (tableModel == null) {
            return null;
        }

        return tableModel.getPreAggregations();
    }
}
