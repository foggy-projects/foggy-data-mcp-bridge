package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.PropertyResolver;
import com.foggyframework.dataset.db.model.engine.pivot.rollup.RollupCache;
import com.foggyframework.dataset.db.model.engine.pivot.rollup.RollupMetricPlan;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;

import java.util.*;

/**
 * Phase 2 各处理器共享的可变上下文对象。
 *
 * <p>包含结果集、轴域、rollup 计划等中间状态。
 * 处理器按顺序修改此上下文，Pipeline 只做编排。</p>
 */
@Getter
@Setter
public class PivotPhase2Context {

    // ===== 核心可变状态 =====
    private List<Map<String, Object>> resultSet;
    private Set<List<Object>> rowDomain;
    private Set<List<Object>> colDomain;
    private List<RollupMetricPlan> rollupPlans;
    private RollupCache rollupCache;

    // ===== 只读引用 =====
    private final String model;
    private final SemanticQueryRequest request;
    private final SemanticRequestContext context;
    private final PivotRequest pivot;
    private final QueryModel queryModel;
    private final List<String> rowFields;
    private final List<String> colFields;
    private final List<String> metrics;
    private final boolean sqlPushdownUsed;
    private final PivotOptions options;
    private final List<PropertyResolver.ResolvedProperty> resolvedProps;
    private final Logger logger;

    public PivotPhase2Context(
            String model,
            SemanticQueryRequest request,
            SemanticRequestContext context,
            PivotRequest pivot,
            QueryModel queryModel,
            List<String> rowFields,
            List<String> colFields,
            List<String> metrics,
            boolean sqlPushdownUsed,
            List<PropertyResolver.ResolvedProperty> resolvedProps,
            List<Map<String, Object>> resultSet,
            Logger logger) {

        this.model = model;
        this.request = request;
        this.context = context;
        this.pivot = pivot;
        this.queryModel = queryModel;
        this.rowFields = rowFields;
        this.colFields = colFields;
        this.metrics = metrics;
        this.sqlPushdownUsed = sqlPushdownUsed;
        this.resolvedProps = resolvedProps;
        this.resultSet = resultSet;
        this.logger = logger;

        this.options = pivot.getOptions() != null ? pivot.getOptions() : new PivotOptions();
        this.rollupPlans = Collections.emptyList();
        this.rollupCache = new RollupCache();
    }

    public boolean needsSubtotal() {
        return options.isRowSubtotals() || options.isColumnSubtotals() || options.isGrandTotal();
    }
}
