package com.foggyframework.dataset.db.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.common.query.CondType;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.engine.expression.SliceExpressionProcessor;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.engine.expression.CalculateDialectCapabilities;
import com.foggyframework.dataset.db.model.engine.expression.CalculateQueryContext;
import com.foggyframework.dataset.db.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.formula.hierarchy.HierarchyOperator;
import com.foggyframework.dataset.db.model.engine.formula.hierarchy.HierarchyOperatorService;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.engine.pivot.PivotTelemetry;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainRelationRenderResult;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainRelationRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlacement;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportRefusalException;
import com.foggyframework.dataset.db.model.engine.pivot.transport.Mysql57DerivedTableDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.Mysql8ValuesDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.PostgresCteDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.SqlServerCteDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.SqliteCteDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.UnsupportedDomainRenderer;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.db.model.engine.query_model.PredefinedCalculatedFieldInjector;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.impl.DbColumnDelegate;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationOutputColumn;
import com.foggyframework.dataset.db.model.impl.model.AggregateRelationQueryObject;
import com.foggyframework.dataset.db.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.db.model.impl.utils.SqlQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.sql.Connection;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Data
public class JdbcModelQueryEngine implements QueryEngine {
    private static final String AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING_PROPERTY =
            "foggy.dataset.auto-lift-aggregate-slice-to-having";
    private static final String AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING_ENV =
            "FOGGY_DATASET_AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING";

    JdbcQueryModel jdbcQueryModel;

    JdbcQuery jdbcQuery;

    SqlFormulaService sqlFormulaService;

    /**
     * 层级操作符服务（用于父子维度）
     */
    HierarchyOperatorService hierarchyOperatorService = new HierarchyOperatorService();

    /**
     * SQL 表达式上下文（用于计算字段）
     */
    SqlExpContext sqlExpContext;

    /**
     * 处理后的计算字段列表
     */
    List<CalculatedDbColumn> calculatedColumns;
    List<SliceRequestDef> postAggregateSlice = new ArrayList<>();

    /**
     * 内联表达式解析结果（包含聚合信息）
     * 用于判断slice条件是否为聚合条件（需要放入HAVING而非WHERE）
     */
    ModelResultContext.ParsedInlineExpressions parsedInlineExpressions;

    /**
     * 是否将 slice 中的纯聚合条件自动提升到 HAVING。
     * <p>
     * 默认开启，可通过系统属性 foggy.dataset.auto-lift-aggregate-slice-to-having
     * 或环境变量 FOGGY_DATASET_AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING 关闭。
     * </p>
     */
    boolean autoLiftAggregateSliceToHaving = resolveAutoLiftAggregateSliceToHavingDefault();

    /**
     * 不含 ORDER BY 的基础SQL，用于聚合查询的子查询
     */
    String innerSqlWithoutOrder;
    String innerSql;
    String sql;
    String aggSql;

    /**
     * 聚合SQL优化结果（用于调试和测试）
     */
    AggSqlOptimizer.OptimizationResult aggSqlOptimizationResult;

    // ── CTE Wrapping structured fields (9.2.0+) ──
    // Populated by generateWithCteWrapping() so that callers can access
    // the inner CTE stage and outer SELECT separately for flat CTE assembly.

    /**
     * Whether the engine generated two-stage CTE wrapping for window CFs.
     */
    boolean cteWrapped = false;

    /**
     * Stage 1 (inner CTE) SQL — base aggregations/dimensions, no window CFs.
     * Only populated when {@link #cteWrapped} is true.
     */
    String cteStage1Sql;

    /**
     * Stage 1 bind parameters.
     */
    List<Object> cteStage1Params;

    /**
     * The CTE alias used for Stage 1 (e.g., "stage1").
     */
    String cteStage1Alias;

    /**
     * Stage 2 (outer SELECT) SQL — references stage1 by alias, adds window CFs + ORDER BY.
     * Does NOT include the {@code WITH stage1 AS (...)} prefix.
     * Only populated when {@link #cteWrapped} is true.
     */
    String cteOuterSelectSql;

    /**
     * Structured CTE stages for multi-stage result wrappers.
     */
    List<SqlGenerationResult.CteStage> cteStages;

    /**
     * Params that belong to {@link #cteOuterSelectSql} only.
     */
    List<Object> cteOuterSelectParams = List.of();

    List values;
    private static final String PATTERN = "^[a-zA-Z\\s]+$";
    private static final Pattern PATTERN_OBJECT = Pattern.compile(PATTERN);
    private static final Pattern SAFE_INTERNAL_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern RATIO_TO_TOTAL_SUGAR_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:ratio_to_total|ratioToTotal)\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$");

    public static void validate(String v) {
        if (StringUtils.isEmpty(v)) {
            return;
        }
        Matcher matcher = PATTERN_OBJECT.matcher(v);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid . Only letters and spaces are allowed.");
        }
    }

    public JdbcModelQueryEngine(JdbcQueryModel jdbcQueryModel, SqlFormulaService sqlFormulaService) {
        this.jdbcQueryModel = jdbcQueryModel;
        this.sqlFormulaService = sqlFormulaService;
    }

    private static boolean resolveAutoLiftAggregateSliceToHavingDefault() {
        String value = System.getProperty(AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(AUTO_LIFT_AGGREGATE_SLICE_TO_HAVING_ENV);
        }
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }

    /**
     * 分析查询请求（兼容旧版本调用）
     *
     * @param systemBundlesContext 系统上下文
     * @param queryRequest         查询请求
     * @deprecated 建议使用 {@link #analysisQueryRequest(SystemBundlesContext, ModelResultContext)} 方法
     */
    @Deprecated
    public void analysisQueryRequest(SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest) {
        // 创建临时 Context 以兼容旧调用
        ModelResultContext context = new ModelResultContext();
        context.setRequest(new com.foggyframework.dataset.client.domain.PagingRequest<>());
        context.getRequest().setParam(queryRequest);
        analysisQueryRequest(systemBundlesContext, context);
    }

    /**
     * 分析查询请求（新版本，接受 ModelResultContext）
     * <p>
     * 如果 context 中已有预处理结果（parsedInlineExpressions），则跳过重复解析。
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询生命周期上下文
     */
    public void analysisQueryRequest(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        DbQueryRequestDef queryRequest = context.getRequest().getParam();
        RX.notNull(queryRequest, "查询请求不得为空");
        clearAggregateRelationPushdowns(jdbcQueryModel);

        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.setQueryRequest(queryRequest);
        jdbcQuery.setQueryModel(jdbcQueryModel);

        // 使用 QueryModel 缓存的 JoinGraph
        JoinGraph joinGraph = jdbcQueryModel.getMergedJoinGraph();
        jdbcQuery.from(jdbcQueryModel.getQueryObject(), joinGraph);

        // 0. 预处理 columns 中的内联表达式，转换为 calculatedFields
        // 如果 context 中已有预处理结果，则跳过
        preprocessInlineExpressions(queryRequest, context);

        // 0.05 将聚合后计算字段与同层 calculatedFields 分离。
        normalizePostAggregateCalculations(queryRequest);
        Set<String> postAggregateNames = postAggregateNames(queryRequest);

        // 0.1 处理动态计算字段
        processCalculatedFields(systemBundlesContext, queryRequest, context);

        //1.加入需要查询的列
        List<DbColumn> selectColumns = null;
        if (queryRequest.getColumns() == null || queryRequest.getColumns().isEmpty()) {
            log.debug("查询请求中未定义列，我们直接从查询模型中取相关的列");

            selectColumns = jdbcQueryModel.getSelectColumns(true);

        } else {
            //前端传了查询的列名
            selectColumns = new ArrayList<>(queryRequest.getColumns().size());
            for (String columnName : queryRequest.getColumns()) {
                if (postAggregateNames.contains(columnName)) {
                    continue;
                }
                // 先查找计算字段
                DbColumn calcColumn = findCalculatedColumn(columnName);
                if (calcColumn != null) {
                    selectColumns.add(calcColumn);
                } else {
                    selectColumns.add(jdbcQueryModel.findJdbcColumnForSelectByName(columnName, true));
                }
            }
        }

        if (queryRequest.getExColumns() != null) {
            for (String columnName : queryRequest.getExColumns()) {
//                selectColumns.f
                DbQueryColumn qc = jdbcQueryModel.findJdbcColumnForSelectByName(columnName, false);
                if (qc != null) {
//                    JdbcColumn c = qc.getSelectColumn();
                    selectColumns.remove(qc);
                }
            }
        }

        jdbcQuery.select(selectColumns);

        // DISTINCT 支持：仅在非聚合查询时生效（聚合查询本身通过 GROUP BY 去重）
        if (queryRequest.isDistinct() && !queryRequest.hasGroupBy()) {
            jdbcQuery.getSelect().setDistinct(true);
        }

        rejectWindowCalculatedFieldSlice(queryRequest);

        // 2. 加入切片条件。纯聚合 slice 视为聚合后过滤，自动写入 HAVING。
        boolean hasLiftedAggregateSlice = false;
        List<SliceRequestDef> innerSlice = new ArrayList<>();
        this.postAggregateSlice = new ArrayList<>();
        if (queryRequest.getSlice() != null) {
            splitPostAggregateSlice(queryRequest.getSlice(), postAggregateNames, innerSlice, this.postAggregateSlice);
        }
        if (!innerSlice.isEmpty()) {
            for (SliceRequestDef sliceDef : innerSlice) {
                if (autoLiftAggregateSliceToHaving) {
                    SliceConditionPhase phase = classifySliceConditionPhase(sliceDef);
                    if (phase == SliceConditionPhase.AGGREGATE) {
                        hasLiftedAggregateSlice = true;
                        buildHaving(jdbcQueryModel, jdbcQuery, sliceDef);
                    } else {
                        buildSlice(jdbcQueryModel, jdbcQuery, sliceDef);
                    }
                } else {
                    rejectAggregateConditionInSlice(sliceDef);
                    buildSlice(jdbcQueryModel, jdbcQuery, sliceDef);
                }
            }
        }
        if (hasLiftedAggregateSlice && !queryRequest.hasGroupBy()) {
            throw RX.throwAUserTip("HAVING_REQUIRES_GROUP_BY: aggregate slice filters are only supported for grouped aggregate queries. Add groupBy/aggregate columns or move row-level filters to slice.");
        }
        if (queryRequest.getHaving() != null && !queryRequest.getHaving().isEmpty()) {
            if (!queryRequest.hasGroupBy()) {
                throw RX.throwAUserTip("HAVING_REQUIRES_GROUP_BY: request.having is only supported for grouped aggregate queries. Add groupBy/aggregate columns or move row-level filters to slice.");
            }
            for (SliceRequestDef havingDef : queryRequest.getHaving()) {
                buildHaving(jdbcQueryModel, jdbcQuery, havingDef);
            }
        }


        // 3.加权限语句
        // 将 queryModel 和 jdbcQuery 存入 context，供脚本访问
        context.setQueryModel(jdbcQueryModel);
        context.setQuery(jdbcQuery);

        for (FsscriptFunction accessBuilder : jdbcQueryModel.getAccessBuilders()) {
            ExpEvaluator ee = DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
            ee.setVar("context", context);
            accessBuilder.autoApply(ee);
        }



        // DISTINCT + ORDER BY 冲突处理移至 ORDER BY 全部添加完成之后（见下方 else 分支末尾）


        if (queryRequest.hasGroupBy()) {
            //当有分组时，我们直接在jdbcQuery加入groupBy
            int idx=0;
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (column instanceof CalculatedDbColumn c) {
                    // hasWindow=true: 窗口函数，不参与 GROUP BY
                    if (c.hasWindow()) {
                        AggregationDbColumn aggColumn = buildAggColumn1(column.getQueryObject(), column.getDeclare(), column, DbAggregation.WINDOW);
                        jdbcQuery.getSelect().getColumns().set(idx, aggColumn);
                    }
                    // hasAggregate=true: 表达式本身已包含聚合函数（如 SUM(totalAmount)），跳过
                    else if (!c.hasAggregate()) {
                        // aggregationType!=null: 推断的聚合类型（如 totalAmount+2 推断为 SUM），用聚合函数包裹
                        // aggregationType==null: 无聚合，加入 groupBy
                        DbAggregation agg = c.getAggregationType() != null
                                ? DbAggregation.valueOf(c.getAggregationType())
                                : column.getAggregation();
                        AggregationDbColumn aggColumn = buildAggColumn1(column.getQueryObject(), column.getDeclare(), column, agg);
                        if (c.getAggregationType() == null) {
                            jdbcQuery.addGroupBy(aggColumn, column);
                        }
                        jdbcQuery.getSelect().getColumns().set(idx, aggColumn);
                    }
                } else {
                    String declare = column.getDeclare(systemBundlesContext.getApplicationContext(), jdbcQueryModel.getAlias(column.getQueryObject()));
                    AggregationDbColumn aggColumn = buildAggColumn1(column.getQueryObject(), declare, column, column.getAggregation());
                    jdbcQuery.addGroupBy(aggColumn, column);

                    //需要覆盖select列，确保会自动补上聚合函数
                    jdbcQuery.getSelect().getColumns().set(idx, aggColumn);
                }

                idx++;
            }

            // 存在分组时，处理排序：只保留在 SELECT 中的排序字段
            addOrderByForGroupBy(jdbcQuery, jdbcQueryModel, queryRequest);

        }else{
            //没有分组，正常进行排序
            if (queryRequest.getOrderBy() != null) {
                for (OrderRequestDef orderRequestDef : queryRequest.getOrderBy()) {

                    validate(orderRequestDef.getDir());
                    DbColumn jdbcColumn = findCalculatedColumn(orderRequestDef.getField());
                    if (jdbcColumn != null) {
                        joinReferencedColumns(jdbcQuery, jdbcColumn);
                    } else {
                        jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(orderRequestDef.getField(), true);
                    }
                    jdbcQuery.addOrder(new DbQueryOrderColumnImpl(jdbcColumn, orderRequestDef.getDir(), orderRequestDef.isNullLast(), orderRequestDef.isNullFirst()));

                }
            }

            //加模QM型默认排序
            if (jdbcQueryModel.getOrders() != null && !jdbcQueryModel.getOrders().isEmpty()
                    && !hasAggregateSelect(jdbcQuery)) {
                jdbcQuery.addOrders(jdbcQueryModel.getOrders());
                for (DbQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                    if (jdbcQuery.containSelect(order.getSelectColumn())) {
                        continue;
                    }
                    // 为 ORDER BY 中的计算字段触发 JOIN
                    DbColumn selectColumn = order.getSelectColumn();
                    if (selectColumn.isCalculatedField()) {
                        joinReferencedColumns(jdbcQuery, selectColumn);
                    } else {
                        jdbcQuery.join(selectColumn.getQueryObject());
                    }
                }
            }

            // DISTINCT 查询：移除 ORDER BY 中不在 SELECT 列表中的列
            // （PostgreSQL 等数据库要求 SELECT DISTINCT 的 ORDER BY 列必须出现在 SELECT 中，
            //   不能反过来把 ORDER BY 列加入 SELECT，否则会破坏 DISTINCT 语义）
            if (jdbcQuery.getSelect().isDistinct() && jdbcQuery.getOrder() != null) {
                jdbcQuery.getOrder().getOrders().removeIf(order ->
                        !jdbcQuery.containSelect(order.getSelectColumn()));
                if (jdbcQuery.getOrder().getOrders().isEmpty()) {
                    jdbcQuery.setOrder(null);
                }
            }
        }


        // 4.生成明细查询语句
        this.jdbcQuery = jdbcQuery;

        DomainTransportSqlInjection domainTransportInjection = applyDomainTransportPlans(context, jdbcQuery);

        // ── CTE Wrapping: detect window CFs and split into two stages ──
        // Window calculated fields (e.g., RANK() OVER(...), ROW_NUMBER() OVER(...))
        // cannot safely reference aliases defined at the same SELECT level in standard SQL.
        // When detected, we generate a two-stage SQL:
        //   Stage 1 (CTE): base aggregations + dimensions (no window CFs, no ORDER BY)
        //   Stage 2 (outer): SELECT stage1.*, windowCF1, windowCF2 FROM stage1 ORDER BY ... LIMIT ...
        boolean hasWindowCf = hasWindowCalculatedFields(queryRequest);
        boolean hasPostAggregateCalculations = hasPostAggregateCalculations(queryRequest);
        boolean hasPostSlice = hasPostSlice(queryRequest);

        if (hasPostSlice && !hasPostAggregateCalculations && !hasWindowCf) {
            throw RX.throwAUserTip("POST_SLICE_REQUIRES_RESULT_STAGE: postSlice requires a result-stage query such as window calculatedFields or postAggregateCalculations.");
        }

        if (hasPostAggregateCalculations) {
            if (hasWindowCf) {
                throw RX.throwAUserTip("POST_AGGREGATE_WINDOW_MIX_UNSUPPORTED: postAggregateCalculations and window calculatedFields cannot be planned together in v1.6.");
            }
            generateWithPostAggregateWrapping(systemBundlesContext, queryRequest, jdbcQuery, domainTransportInjection);
        } else if (hasWindowCf) {
            generateWithCteWrapping(systemBundlesContext, queryRequest, jdbcQuery, domainTransportInjection);
        } else {
            generateSinglePass(systemBundlesContext, queryRequest, jdbcQuery, domainTransportInjection);
        }

        if (log.isDebugEnabled()) {
            log.debug("生成查询SQL");
            log.debug(this.sql);
            log.debug("聚合SQL");
            log.debug(this.aggSql);
            log.debug("参数");
            log.debug(values == null ? "无" : values.toString());
        }
        clearAggregateRelationPushdowns(jdbcQueryModel);

    }

    private boolean hasAggregateSelect(JdbcQuery jdbcQuery) {
        if (jdbcQuery == null || jdbcQuery.getSelect() == null || jdbcQuery.getSelect().getColumns() == null) {
            return false;
        }
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
            if (column instanceof AggregationDbColumn) {
                return true;
            }
            if (column instanceof CalculatedDbColumn calcColumn && calcColumn.hasAggregate()) {
                return true;
            }
        }
        return false;
    }

    private void clearAggregateRelationPushdowns(JdbcQueryModel jdbcQueryModel) {
        for (AggregateRelationQueryObject queryObject : collectAggregateRelationQueryObjects(jdbcQueryModel)) {
            queryObject.clearAggregateRelationPushdowns();
        }
    }

    private Set<AggregateRelationQueryObject> collectAggregateRelationQueryObjects(JdbcQueryModel jdbcQueryModel) {
        Set<AggregateRelationQueryObject> queryObjects = new LinkedHashSet<>();
        if (jdbcQueryModel == null || jdbcQueryModel.getJdbcModelList() == null) {
            return queryObjects;
        }
        for (TableModel tableModel : jdbcQueryModel.getJdbcModelList()) {
            collectAggregateRelationQueryObject(tableModel == null ? null : tableModel.getQueryObject(), queryObjects);
            if (tableModel == null || tableModel.getVisibleSelectColumns() == null) {
                continue;
            }
            for (DbColumn column : tableModel.getVisibleSelectColumns()) {
                collectAggregateRelationQueryObject(column == null ? null : column.getQueryObject(), queryObjects);
            }
        }
        return queryObjects;
    }

    private void collectAggregateRelationQueryObject(QueryObject queryObject, Set<AggregateRelationQueryObject> queryObjects) {
        if (queryObject instanceof AggregateRelationQueryObject aggregateRelationQueryObject) {
            queryObjects.add(aggregateRelationQueryObject);
        }
    }

    // ------------------------------------------------------------------
    // CTE Wrapping: Two-Stage SQL generation for Window Calculated Fields
    // ------------------------------------------------------------------

    private void normalizePostAggregateCalculations(DbQueryRequestDef queryRequest) {
        List<PostAggregateCalculationDef> normalized = queryRequest.getPostAggregateCalculations() == null
                ? new ArrayList<>()
                : new ArrayList<>(queryRequest.getPostAggregateCalculations());
        Set<String> seen = normalized.stream()
                .map(PostAggregateCalculationDef::getName)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (queryRequest.getCalculatedFields() != null && !queryRequest.getCalculatedFields().isEmpty()) {
            List<CalculatedFieldDef> remaining = new ArrayList<>();
            for (CalculatedFieldDef cf : queryRequest.getCalculatedFields()) {
                Matcher matcher = RATIO_TO_TOTAL_SUGAR_PATTERN.matcher(cf.getExpression() == null ? "" : cf.getExpression());
                if (!matcher.matches()) {
                    remaining.add(cf);
                    continue;
                }
                String alias = cf.getName();
                if (!seen.add(alias)) {
                    throw RX.throwAUserTip("POST_AGGREGATE_CALCULATION_DUPLICATE: duplicate postAggregateCalculations name '" + alias + "'.");
                }
                normalized.add(new PostAggregateCalculationDef(
                        alias,
                        "ratioToTotal",
                        matcher.group(1),
                        "grandTotal",
                        "ratio"));
            }
            queryRequest.setCalculatedFields(remaining);
        }

        validatePostAggregateCalculations(queryRequest, normalized);
        queryRequest.setPostAggregateCalculations(normalized);
    }

    private void validatePostAggregateCalculations(
            DbQueryRequestDef queryRequest,
            List<PostAggregateCalculationDef> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Set<String> aggregateAliases = selectedAggregateAliases(queryRequest);
        for (PostAggregateCalculationDef item : items) {
            if (StringUtils.isEmpty(item.getName())) {
                throw RX.throwAUserTip("POST_AGGREGATE_CALCULATION_INVALID: postAggregateCalculations entries require a non-empty name.");
            }
            if (!"ratioToTotal".equals(item.getKind())) {
                throw RX.throwAUserTip("POST_AGGREGATE_CALCULATION_UNSUPPORTED: only kind='ratioToTotal' is supported in v1.6; got '" + item.getKind() + "' for '" + item.getName() + "'.");
            }
            String scope = StringUtils.isEmpty(item.getScope()) ? "grandTotal" : item.getScope();
            if (!"grandTotal".equals(scope)) {
                throw RX.throwAUserTip("POST_AGGREGATE_CALCULATION_UNSUPPORTED: only scope='grandTotal' is supported in v1.6; got '" + scope + "' for '" + item.getName() + "'.");
            }
            String format = StringUtils.isEmpty(item.getFormat()) ? "ratio" : item.getFormat();
            if (!"ratio".equals(format) && !"percent".equals(format)) {
                throw RX.throwAUserTip("POST_AGGREGATE_CALCULATION_UNSUPPORTED: format must be 'ratio' or 'percent'; got '" + format + "' for '" + item.getName() + "'.");
            }
            if (!aggregateAliases.contains(item.getMeasure())) {
                throw RX.throwAUserTip("POST_AGGREGATE_MEASURE_NOT_FOUND: ratioToTotal '" + item.getName()
                        + "' measure '" + item.getMeasure() + "' must reference a selected aggregate alias from columns[].");
            }
        }
    }

    private Set<String> selectedAggregateAliases(DbQueryRequestDef queryRequest) {
        Set<String> aliases = new LinkedHashSet<>();
        List<String> columns = queryRequest == null ? null : queryRequest.getColumns();
        if (columns == null) {
            columns = List.of();
        }
        Pattern pattern = Pattern.compile(
                "(?i)\\b(?:sum|avg|count|countd|count_distinct|min|max|stddev_pop|stddev_samp|var_pop|var_samp)\\s*\\([^)]*\\)\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\b");
        Pattern aggregateExpression = Pattern.compile(
                "(?i)\\b(?:sum|avg|count|countd|count_distinct|min|max|stddev_pop|stddev_samp|var_pop|var_samp)\\s*\\(");
        for (String column : columns) {
            if (column == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(column);
            if (matcher.find()) {
                aliases.add(matcher.group(1));
            }
        }
        if (queryRequest != null && queryRequest.getCalculatedFields() != null) {
            for (CalculatedFieldDef field : queryRequest.getCalculatedFields()) {
                if (field == null || StringUtils.isEmpty(field.getName()) || StringUtils.isEmpty(field.getExpression())) {
                    continue;
                }
                if (aggregateExpression.matcher(field.getExpression()).find()) {
                    aliases.add(field.getName());
                }
            }
        }
        return aliases;
    }

    private boolean hasPostAggregateCalculations(DbQueryRequestDef queryRequest) {
        return queryRequest.getPostAggregateCalculations() != null
                && !queryRequest.getPostAggregateCalculations().isEmpty();
    }

    private boolean hasPostSlice(DbQueryRequestDef queryRequest) {
        return queryRequest != null
                && queryRequest.getPostSlice() != null
                && !queryRequest.getPostSlice().isEmpty();
    }

    private Set<String> postAggregateNames(DbQueryRequestDef queryRequest) {
        if (!hasPostAggregateCalculations(queryRequest)) {
            return Set.of();
        }
        return queryRequest.getPostAggregateCalculations().stream()
                .map(PostAggregateCalculationDef::getName)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Check whether the current SELECT list contains any window calculated fields.
     * <p>
     * Window CFs are identified by the {@code DbAggregation.WINDOW} type on their
     * wrapping {@link AggregationDbColumn}, or by the {@code hasWindow()} flag on
     * a raw {@link CalculatedDbColumn}.
     * </p>
     */
    private boolean hasWindowCalculatedFields(DbQueryRequestDef queryRequest) {
        if (calculatedColumns == null || calculatedColumns.isEmpty()) {
            return false;
        }
        // CTE wrapping is only needed for window CFs that were processed through
        // wrapWithWindowClause (i.e., have explicit partitionBy/windowOrderBy).
        // CALCULATE-generated windows (SUM(SUM(x)) OVER(...)) are self-contained
        // and work in single-pass mode — they are NOT flagged for CTE wrapping.
        for (CalculatedDbColumn col : calculatedColumns) {
            if (col.isNeedsCteWrapping()) {
                return true;
            }
        }
        return false;
    }

    private void rejectWindowCalculatedFieldSlice(DbQueryRequestDef queryRequest) {
        if (queryRequest.getSlice() == null || queryRequest.getSlice().isEmpty()
                || calculatedColumns == null || calculatedColumns.isEmpty()) {
            return;
        }

        Set<String> windowAliases = calculatedColumns.stream()
                .filter(col -> col.hasWindow() || col.isNeedsCteWrapping())
                .map(CalculatedDbColumn::getAlias)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (windowAliases.isEmpty()) {
            return;
        }

        Set<String> sliceFields = new LinkedHashSet<>();
        for (SliceRequestDef slice : queryRequest.getSlice()) {
            collectSliceFields(slice, sliceFields);
        }

        List<String> matched = sliceFields.stream()
                .filter(windowAliases::contains)
                .toList();
        if (matched.isEmpty()) {
            return;
        }

        throw RX.throwAUserTip(
                "WINDOW_CALCULATED_FIELD_SLICE_NOT_SUPPORTED: query_model slice cannot reference window calculated field alias "
                        + matched
                        + " from the same request. Return the window field and filter result rows, or use compose_script with a base dsl(...) window calculatedFields query followed by a derived .query({slice:[...]}) stage.");
    }

    /**
     * Legacy single-pass SQL generation (original path, no CTE wrapping).
     * <p>
     * Used when no window calculated fields are present in the query.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void generateSinglePass(SystemBundlesContext systemBundlesContext,
                                     DbQueryRequestDef queryRequest,
                                     JdbcQuery jdbcQuery,
                                     DomainTransportSqlInjection domainTransportInjection) {
        SimpleSqlJdbcQueryVisitor v = new SimpleSqlJdbcQueryVisitor(
                systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);
        jdbcQuery.accept(v);
        List visitorValues = v.getValues();
        if (domainTransportInjection.hasCte()) {
            String ctePrefix = "with " + String.join(",\n", domainTransportInjection.cteFragments()) + "\n";
            this.innerSql = ctePrefix + v.getSql();
            this.innerSqlWithoutOrder = ctePrefix + v.getSqlWithoutOrder();
            List<Object> mergedValues = new ArrayList<>();
            mergedValues.addAll(domainTransportInjection.cteParams());
            mergedValues.addAll(visitorValues);
            values = mergedValues;
        } else {
            values = visitorValues;
            this.innerSql = v.getSql();
            this.innerSqlWithoutOrder = v.getSqlWithoutOrder();
        }
        this.sql = this.innerSql;

        // 构建聚合SQL（支持优化）
        boolean countToSum = queryRequest.hasGroupBy();
        if (queryRequest.isOptimizeAggSqlEnabled()) {
            AggSqlOptimizer optimizer = new AggSqlOptimizer(jdbcQueryModel, jdbcQuery, systemBundlesContext, queryRequest);
            this.aggSqlOptimizationResult = optimizer.buildOptimizedAggSql(this.innerSqlWithoutOrder, countToSum);
            this.aggSql = this.aggSqlOptimizationResult.getOptimizedSql();
            if (log.isDebugEnabled() && this.aggSqlOptimizationResult.isOptimizationApplied()) {
                log.debug("聚合SQL优化: {}", this.aggSqlOptimizationResult.getSummary());
            }
        } else {
            this.aggSql = buildAggSql(systemBundlesContext, null, null, false, countToSum);
            this.aggSqlOptimizationResult = null;
        }
    }

    /**
     * Two-stage CTE wrapping SQL generation for queries with Window Calculated Fields.
     * <p>
     * Architecture (per OPT-compose-cte-wrapping-design.md):
     * <pre>
     * WITH stage1 AS (
     *   SELECT dimension_cols, agg_cols, base_calc_cols
     *   FROM table t
     *   WHERE ...
     *   GROUP BY ...
     *   HAVING ...
     * )
     * SELECT stage1.*, window_cf_1 AS alias1, window_cf_2 AS alias2
     * FROM stage1
     * ORDER BY ...
     * LIMIT ... OFFSET ...
     * </pre>
     * </p>
     * <p>
     * Stage 1 contains all non-window columns (dimensions, measures, base CFs) plus
     * WHERE/GROUP BY/HAVING. Stage 2 references Stage 1 columns by alias and appends
     * window CF expressions (whose OVER clauses reference aliases, not raw expressions)
     * plus the global ORDER BY and LIMIT/OFFSET.
     * </p>
     */
    public static String getCteProjectionAlias(DbColumn col) {
        if (col instanceof DbQueryColumn && StringUtils.isNotEmpty(col.getName())) {
            return col.getName();
        }
        if (StringUtils.isNotEmpty(col.getAlias())) {
            return col.getAlias();
        }
        return col.getName();
    }

    private static class CteProjectedColumn extends DbColumnDelegate {
        public CteProjectedColumn(DbColumn delegate) {
            super(delegate);
        }
        @Override
        public String getAlias() {
            return getCteProjectionAlias(delegate);
        }
        @Override
        public AiObject getAi() {
            return delegate.getAi();
        }
        @Override
        public Object getExtData() {
            return delegate.getExtData();
        }
    }

    private static boolean containsCteProjectionAlias(List<DbColumn> columns, DbColumn candidate) {
        String candidateAlias = getCteProjectionAlias(candidate);
        for (DbColumn column : columns) {
            if (candidateAlias.equals(getCteProjectionAlias(column))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void generateWithCteWrapping(SystemBundlesContext systemBundlesContext,
                                          DbQueryRequestDef queryRequest,
                                          JdbcQuery jdbcQuery,
                                          DomainTransportSqlInjection domainTransportInjection) {
        FDialect dialect = jdbcQueryModel != null ? jdbcQueryModel.getDialect() : FDialect.MYSQL_DIALECT;

        // ── Identify window CF columns in the SELECT list ──
        // Snapshot columns and indices before mutation
        List<DbColumn> originalSelectCols = new ArrayList<>(jdbcQuery.getSelect().getColumns());
        List<WindowColumnInfo> windowColumns = new ArrayList<>();
        List<DbColumn> stage1SelectCols = new ArrayList<>();
        List<DbColumn> outerStage1OutputCols = new ArrayList<>();

        for (int i = 0; i < originalSelectCols.size(); i++) {
            DbColumn col = originalSelectCols.get(i);
            boolean isWindowCf = false;
            if (col instanceof AggregationDbColumn agg && agg.getAggregation() == DbAggregation.WINDOW) {
                isWindowCf = true;
            } else if (col instanceof CalculatedDbColumn calc && calc.hasWindow()) {
                isWindowCf = true;
            }
            if (isWindowCf) {
                windowColumns.add(new WindowColumnInfo(i, col));
            } else {
                stage1SelectCols.add(col);
                outerStage1OutputCols.add(col);
            }
        }

        if (windowColumns.isEmpty()) {
            // Fallback: no window CFs found (shouldn't happen since hasWindowCF was true)
            generateSinglePass(systemBundlesContext, queryRequest, jdbcQuery, domainTransportInjection);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("CTE Wrapping: {} window CFs detected, generating two-stage SQL", windowColumns.size());
            for (WindowColumnInfo wci : windowColumns) {
                log.debug("  Window CF: alias={}, declare={}", wci.column.getAlias(), wci.column.getDeclare());
            }
        }

        for (WindowColumnInfo wci : windowColumns) {
            if (wci.column instanceof CalculatedDbColumn calc && calc.getReferencedColumns() != null) {
                for (DbQueryColumn ref : calc.getReferencedColumns()) {
                    if (!containsCteProjectionAlias(stage1SelectCols, ref)) {
                        stage1SelectCols.add(ref);
                    }
                }
            }
        }

        // ── Stage 1: Generate inner SQL WITHOUT window CFs and WITHOUT ORDER BY ──
        // Temporarily replace SELECT columns with stage1-only columns wrapped to ensure correct alias
        List<DbColumn> stage1WrappedCols = new ArrayList<>();
        for (DbColumn col : stage1SelectCols) {
            stage1WrappedCols.add(new CteProjectedColumn(col));
        }
        jdbcQuery.getSelect().setColumns(stage1WrappedCols);

        // Temporarily clear ORDER BY for Stage 1 (will be elevated to Stage 2)
        JdbcQuery.JdbcOrder savedOrder = jdbcQuery.getOrder();
        jdbcQuery.setOrder(null);

        SimpleSqlJdbcQueryVisitor v1 = new SimpleSqlJdbcQueryVisitor(
                systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);
        jdbcQuery.accept(v1);
        String stage1Sql = v1.getSql();
        List<Object> stage1Params = new ArrayList<>(v1.getValues());

        // Restore original select columns and order (for downstream consumers like aggSql)
        jdbcQuery.getSelect().setColumns(new ArrayList<>(originalSelectCols));
        jdbcQuery.setOrder(savedOrder);

        // ── Apply domain transport CTE prefix if present ──
        if (domainTransportInjection.hasCte()) {
            stage1Sql = "with " + String.join(",\n", domainTransportInjection.cteFragments()) + "\n" + stage1Sql;
            List<Object> mergedParams = new ArrayList<>();
            mergedParams.addAll(domainTransportInjection.cteParams());
            mergedParams.addAll(stage1Params);
            stage1Params = mergedParams;
        }

        // ── Stage 2: Build outer SELECT referencing Stage 1 CTE ──
        String cteAlias = "stage1";
        StringBuilder outerSelect = new StringBuilder();

        // Outer SELECT: reference all Stage 1 columns by alias, then add window CFs
        outerSelect.append("SELECT ");
        List<String> outerColumnExprs = new ArrayList<>();

        // Stage 1 columns: reference by unified CTE alias
        for (DbColumn col : outerStage1OutputCols) {
            String colAlias = getCteProjectionAlias(col);
            String quotedAlias = dialect.quoteIdentifier(colAlias);
            outerColumnExprs.add(cteAlias + "." + quotedAlias);
        }

        // Window CF columns: use the full window expression (which now references aliases)
        for (WindowColumnInfo wci : windowColumns) {
            String windowExpr = wci.column.getDeclare();
            String windowAlias = dialect.quoteIdentifier(wci.column.getAlias());
            outerColumnExprs.add(windowExpr + " " + windowAlias);
        }

        outerSelect.append(String.join(",\n\t", outerColumnExprs));
        outerSelect.append("\nFROM ").append(cteAlias);

        // ── Elevate ORDER BY to Stage 2 ──
        String outerSelectWithoutOrder = outerSelect.toString();
        Set<String> finalAliases = new LinkedHashSet<>();
        for (DbColumn col : outerStage1OutputCols) {
            finalAliases.add(getCteProjectionAlias(col));
        }
        for (WindowColumnInfo wci : windowColumns) {
            finalAliases.add(wci.column.getAlias());
        }

        List<String> unqualifiedOrderExprs = buildWindowResultOrderExprs(savedOrder, windowColumns, dialect, null);
        List<String> cteQualifiedOrderExprs = buildWindowResultOrderExprs(savedOrder, windowColumns, dialect, cteAlias);

        List<Object> finalParams = new ArrayList<>();
        String finalSelectWithoutOrder;
        String finalSelectSql;
        String ctePrefix;

        if (hasPostSlice(queryRequest)) {
            String postResultAlias = "__POST_RESULT_STAGE__";
            StringBuilder finalSelect = new StringBuilder();
            finalSelect.append("SELECT ");
            finalSelect.append(finalAliases.stream()
                    .map(dialect::quoteIdentifier)
                    .collect(Collectors.joining(",\n\t")));
            finalSelect.append("\nFROM ").append(postResultAlias);

            List<String> filters = new ArrayList<>();
            for (SliceRequestDef slice : queryRequest.getPostSlice()) {
                filters.add(buildPostAggregateFilterSql(slice, dialect, finalAliases, finalParams));
            }
            finalSelect.append("\nWHERE ").append(String.join(" AND ", filters));
            finalSelectWithoutOrder = finalSelect.toString();
            if (!unqualifiedOrderExprs.isEmpty()) {
                finalSelect.append("\nORDER BY ").append(String.join(", ", unqualifiedOrderExprs));
            }
            finalSelectSql = finalSelect.toString();

            this.cteStages = List.of(
                    new SqlGenerationResult.CteStage(cteAlias, stage1Sql, stage1Params),
                    new SqlGenerationResult.CteStage(postResultAlias, outerSelectWithoutOrder, List.of())
            );
            ctePrefix = "WITH " + cteAlias + " AS (\n" + stage1Sql + "\n),\n"
                    + postResultAlias + " AS (\n" + outerSelectWithoutOrder + "\n)\n";
        } else {
            if (!cteQualifiedOrderExprs.isEmpty()) {
                outerSelect.append("\nORDER BY ").append(String.join(", ", cteQualifiedOrderExprs));
            }
            finalSelectWithoutOrder = outerSelectWithoutOrder;
            finalSelectSql = outerSelect.toString();
            this.cteStages = List.of(new SqlGenerationResult.CteStage(cteAlias, stage1Sql, stage1Params));
            ctePrefix = "WITH " + cteAlias + " AS (\n" + stage1Sql + "\n)\n";
        }

        // Note: LIMIT/OFFSET is handled by the upper-layer pagination framework
        // (Spring JDBC PagingQuery), not in the engine's SQL string generation.

        // ── Populate structured CTE fields for ComposePlanner flattening ──
        this.cteWrapped = true;
        this.cteStage1Alias = cteAlias;
        this.cteStage1Sql = stage1Sql;
        this.cteStage1Params = stage1Params;
        this.cteOuterSelectSql = finalSelectSql;
        this.cteOuterSelectParams = finalParams;

        // ── Set engine SQL fields (assembled for direct execution) ──
        this.innerSql = ctePrefix + finalSelectSql;
        this.innerSqlWithoutOrder = ctePrefix + finalSelectWithoutOrder;
        this.sql = this.innerSql;
        List<Object> mergedValues = new ArrayList<>();
        mergedValues.addAll(stage1Params);
        mergedValues.addAll(finalParams);
        this.values = mergedValues;

        // 构建聚合SQL（基于 Stage 1，不含窗口函数和排序）
        boolean countToSum = queryRequest.hasGroupBy();
        if (queryRequest.isOptimizeAggSqlEnabled()) {
            AggSqlOptimizer optimizer = new AggSqlOptimizer(jdbcQueryModel, jdbcQuery, systemBundlesContext, queryRequest);
            this.aggSqlOptimizationResult = optimizer.buildOptimizedAggSql(ctePrefix + outerSelectWithoutOrder, countToSum);
            this.aggSql = this.aggSqlOptimizationResult.getOptimizedSql();
            if (log.isDebugEnabled() && this.aggSqlOptimizationResult.isOptimizationApplied()) {
                log.debug("聚合SQL优化 (CTE wrapped): {}", this.aggSqlOptimizationResult.getSummary());
            }
        } else {
            this.aggSql = buildAggSql(systemBundlesContext, null, null, false, countToSum);
            this.aggSqlOptimizationResult = null;
        }
    }

    private List<String> buildWindowResultOrderExprs(
            JdbcQuery.JdbcOrder savedOrder,
            List<WindowColumnInfo> windowColumns,
            FDialect dialect,
            String nonWindowQualifier) {
        if (savedOrder == null || savedOrder.getOrders().isEmpty()) {
            return List.of();
        }
        List<String> orderExprs = new ArrayList<>();
        for (DbQueryOrderColumnImpl order : savedOrder.getOrders()) {
            DbColumn orderCol = order.getSelectColumn();
            String orderColAlias = dialect.quoteIdentifier(getCteProjectionAlias(orderCol));

            boolean isWindowCol = false;
            for (WindowColumnInfo wci : windowColumns) {
                if (wci.column.getAlias().equals(orderCol.getAlias())) {
                    isWindowCol = true;
                    break;
                }
            }

            String orderRef = orderColAlias;
            if (!isWindowCol && StringUtils.isNotEmpty(nonWindowQualifier)) {
                orderRef = nonWindowQualifier + "." + orderColAlias;
            }

            if (order.isNullLast() || order.isNullFirst()) {
                orderRef = dialect.buildNullOrderClause(orderRef, order.isNullFirst());
            }

            if (StringUtils.isNotEmpty(order.getOrder())) {
                orderRef += " " + order.getOrder();
            }
            orderExprs.add(orderRef);
        }
        return orderExprs;
    }

    @SuppressWarnings("unchecked")
    private void generateWithPostAggregateWrapping(SystemBundlesContext systemBundlesContext,
                                                   DbQueryRequestDef queryRequest,
                                                   JdbcQuery jdbcQuery,
                                                   DomainTransportSqlInjection domainTransportInjection) {
        FDialect dialect = jdbcQueryModel != null ? jdbcQueryModel.getDialect() : FDialect.MYSQL_DIALECT;

        List<DbColumn> originalSelectCols = new ArrayList<>(jdbcQuery.getSelect().getColumns());
        JdbcQuery.JdbcOrder savedOrder = jdbcQuery.getOrder();
        jdbcQuery.setOrder(null);

        SimpleSqlJdbcQueryVisitor v1 = new SimpleSqlJdbcQueryVisitor(
                systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);
        jdbcQuery.accept(v1);
        String stage1Sql = v1.getSql();
        List<Object> stage1Params = new ArrayList<>(v1.getValues());

        jdbcQuery.getSelect().setColumns(new ArrayList<>(originalSelectCols));
        jdbcQuery.setOrder(savedOrder);

        if (domainTransportInjection.hasCte()) {
            stage1Sql = "with " + String.join(",\n", domainTransportInjection.cteFragments()) + "\n" + stage1Sql;
            List<Object> mergedParams = new ArrayList<>();
            mergedParams.addAll(domainTransportInjection.cteParams());
            mergedParams.addAll(stage1Params);
            stage1Params = mergedParams;
        }

        String stage1Alias = "stage1";
        String postAlias = "post_stage";
        List<String> postSelects = new ArrayList<>();
        Set<String> finalAliases = new LinkedHashSet<>();

        for (DbColumn col : originalSelectCols) {
            String alias = col.getAlias();
            finalAliases.add(alias);
            String quoted = dialect.quoteIdentifier(alias);
            postSelects.add(stage1Alias + "." + quoted);
        }

        for (PostAggregateCalculationDef calc : queryRequest.getPostAggregateCalculations()) {
            String measureRef = stage1Alias + "." + dialect.quoteIdentifier(calc.getMeasure());
            String expr = measureRef + " / NULLIF(SUM(" + measureRef + ") OVER (), 0)";
            if ("percent".equals(calc.getFormat())) {
                expr = "(" + expr + ") * 100";
            }
            postSelects.add(expr + " AS " + dialect.quoteIdentifier(calc.getName()));
            finalAliases.add(calc.getName());
        }

        String postStageSql = "SELECT " + String.join(",\n\t", postSelects)
                + "\nFROM " + stage1Alias;

        StringBuilder finalSelect = new StringBuilder();
        finalSelect.append("SELECT ");
        List<String> finalSelects = finalAliases.stream()
                .map(dialect::quoteIdentifier)
                .collect(Collectors.toList());
        finalSelect.append(String.join(",\n\t", finalSelects));
        finalSelect.append("\nFROM ").append(postAlias);

        List<Object> finalParams = new ArrayList<>();
        List<SliceRequestDef> resultStageSlice = new ArrayList<>();
        if (postAggregateSlice != null && !postAggregateSlice.isEmpty()) {
            resultStageSlice.addAll(postAggregateSlice);
        }
        if (queryRequest.getPostSlice() != null && !queryRequest.getPostSlice().isEmpty()) {
            resultStageSlice.addAll(queryRequest.getPostSlice());
        }
        if (!resultStageSlice.isEmpty()) {
            List<String> filters = new ArrayList<>();
            for (SliceRequestDef slice : resultStageSlice) {
                filters.add(buildPostAggregateFilterSql(slice, dialect, finalAliases, finalParams));
            }
            finalSelect.append("\nWHERE ").append(String.join(" AND ", filters));
        }

        String finalSelectWithoutOrder = finalSelect.toString();
        if (queryRequest.getOrderBy() != null && !queryRequest.getOrderBy().isEmpty()) {
            List<String> orderExprs = new ArrayList<>();
            for (OrderRequestDef order : queryRequest.getOrderBy()) {
                if (!finalAliases.contains(order.getField())) {
                    continue;
                }
                String orderRef = dialect.quoteIdentifier(order.getField());
                if (StringUtils.isNotEmpty(order.getDir())) {
                    orderRef += " " + order.getDir().toUpperCase();
                }
                orderExprs.add(orderRef);
            }
            if (!orderExprs.isEmpty()) {
                finalSelect.append("\nORDER BY ").append(String.join(", ", orderExprs));
            }
        }

        this.cteWrapped = true;
        this.cteStage1Alias = stage1Alias;
        this.cteStage1Sql = stage1Sql;
        this.cteStage1Params = stage1Params;
        this.cteOuterSelectSql = finalSelect.toString();
        this.cteOuterSelectParams = finalParams;
        this.cteStages = List.of(
                new SqlGenerationResult.CteStage(stage1Alias, stage1Sql, stage1Params),
                new SqlGenerationResult.CteStage(postAlias, postStageSql, List.of())
        );

        this.innerSql = "WITH " + stage1Alias + " AS (\n" + stage1Sql + "\n),\n"
                + postAlias + " AS (\n" + postStageSql + "\n)\n"
                + finalSelect;
        this.innerSqlWithoutOrder = "WITH " + stage1Alias + " AS (\n" + stage1Sql + "\n),\n"
                + postAlias + " AS (\n" + postStageSql + "\n)\n"
                + finalSelectWithoutOrder;
        this.sql = this.innerSql;
        List<Object> mergedValues = new ArrayList<>();
        mergedValues.addAll(stage1Params);
        mergedValues.addAll(finalParams);
        this.values = mergedValues;

        boolean countToSum = queryRequest.hasGroupBy();
        if (queryRequest.isOptimizeAggSqlEnabled()) {
            AggSqlOptimizer optimizer = new AggSqlOptimizer(jdbcQueryModel, jdbcQuery, systemBundlesContext, queryRequest);
            this.aggSqlOptimizationResult = optimizer.buildOptimizedAggSql(this.innerSqlWithoutOrder, countToSum);
            this.aggSql = this.aggSqlOptimizationResult.getOptimizedSql();
        } else {
            this.aggSql = buildAggSql(systemBundlesContext, null, null, false, countToSum);
            this.aggSqlOptimizationResult = null;
        }
    }

    private String buildPostAggregateFilterSql(
            CondRequestDef slice,
            FDialect dialect,
            Set<String> availableAliases,
            List<Object> params) {
        if (slice._isLogicalGroup()) {
            List<String> parts = new ArrayList<>();
            for (CondRequestDef child : slice._getGroupChildren()) {
                parts.add(buildPostAggregateFilterSql(child, dialect, availableAliases, params));
            }
            String link = " " + slice._getGroupLink() + " ";
            return "(" + String.join(link, parts) + ")";
        }
        String field = slice.getField();
        if (!availableAliases.contains(field)) {
            throw RX.throwAUserTip("POST_AGGREGATE_SLICE_FIELD_NOT_SELECTED: slice field '" + field + "' is not available in the post-aggregate stage.");
        }
        params.add(slice.getValue());
        return dialect.quoteIdentifier(field) + " " + normalizeOperator(slice.getOp()) + " ?";
    }

    /**
     * Holds a window CF column and its original index in the SELECT list.
     * Used during CTE wrapping to preserve column ordering.
     */
    private record WindowColumnInfo(int originalIndex, DbColumn column) {}

    @SuppressWarnings("unchecked")
    private DomainTransportSqlInjection applyDomainTransportPlans(ModelResultContext context, JdbcQuery jdbcQuery) {
        if (context == null || context.getExtData() == null) {
            return DomainTransportSqlInjection.empty();
        }
        Object rawPlans = context.getExtData().get(DomainTransportPlan.EXT_DATA_KEY);
        if (!(rawPlans instanceof List<?> rawList) || rawList.isEmpty()) {
            return DomainTransportSqlInjection.empty();
        }

        List<DomainTransportPlan> plans = rawList.stream()
                .filter(DomainTransportPlan.class::isInstance)
                .map(DomainTransportPlan.class::cast)
                .collect(Collectors.toList());
        if (plans.isEmpty()) {
            return DomainTransportSqlInjection.empty();
        }

        FDialect dialect = jdbcQueryModel.getDialect();
        String databaseVersion = null;
        if (jdbcQueryModel.getDataSource() != null) {
            databaseVersion = getDatabaseProductVersion();
        }
            DomainRelationRenderer renderer = selectDomainRelationRenderer(dialect, databaseVersion);
        String modelName = jdbcQueryModel != null ? jdbcQueryModel.getName() : null;

        List<String> cteFragments = new ArrayList<>();
        List<Object> cteParams = new ArrayList<>();
        for (DomainTransportPlan plan : plans) {
            try {
                validateInternalRelationName(plan.getRelationName());
                DomainRelationRenderResult rendered = renderer.render(dialect, databaseVersion, plan);
                String predicate = buildDomainTransportPredicate(jdbcQuery, plan, dialect);
                if (rendered.getPlacement() == DomainTransportPlacement.CTE) {
                    cteFragments.add(rendered.getSqlFragment());
                    for (Object param : rendered.getParams()) {
                        cteParams.add(dialect.convertParameterValue(param));
                    }
                    jdbcQuery.getWhere().addRawSql("AND",
                            "exists (select 1 from " + plan.getRelationName() + " _d where " + predicate + ")");
                } else if (rendered.getPlacement() == DomainTransportPlacement.DERIVED_TABLE) {
                    jdbcQuery.getWhere().andList(
                            "exists (select 1 from " + rendered.getSqlFragment() + " _d where " + predicate + ")",
                            rendered.getParams());
                } else {
                    throw new DomainTransportRefusalException("Unsupported domain transport placement: " + rendered.getPlacement());
                }
                PivotTelemetry.domainTransportApplied(log, modelName, plan.getRelationName(),
                        dialect.getProductName(), rendered.getPlacement().name(), plan.getFields().size(),
                        plan.getTuples().size(), plan.parameterCount());
            } catch (DomainTransportRefusalException e) {
                PivotTelemetry.domainTransportRefused(log, modelName, plan.getRelationName(),
                        dialect.getProductName(), e);
                throw e;
            }
        }

        return new DomainTransportSqlInjection(cteFragments, cteParams);
    }

    private DomainRelationRenderer selectDomainRelationRenderer(FDialect dialect, String databaseVersion) {
        if (dialect == null) {
            return new UnsupportedDomainRenderer();
        }
        if (dialect == FDialect.POSTGRES_DIALECT || "PostgreSQL".equalsIgnoreCase(dialect.getProductName())) {
            return new PostgresCteDomainRenderer();
        }
        if (dialect == FDialect.SQLITE_DIALECT || "SQLite".equalsIgnoreCase(dialect.getProductName())) {
            return new SqliteCteDomainRenderer();
        }
        if (dialect == FDialect.SQLSERVER_DIALECT || dialect.getDbType() == DbType.SQLSERVER
                || "SQLSERVER".equalsIgnoreCase(dialect.getProductName())) {
            return new SqlServerCteDomainRenderer();
        }
        if (dialect.getClass().getSimpleName().contains("Mysql8")
                || ("MySQL".equalsIgnoreCase(dialect.getProductName()) && supportsMysqlValuesRow(databaseVersion))) {
            return new Mysql8ValuesDomainRenderer();
        }
        if (dialect == FDialect.MYSQL_DIALECT || "MySQL".equalsIgnoreCase(dialect.getProductName())) {
            return new Mysql57DerivedTableDomainRenderer();
        }
        return new UnsupportedDomainRenderer();
    }

    private boolean supportsMysqlValuesRow(String databaseVersion) {
        if (databaseVersion == null || databaseVersion.isEmpty()) {
            return false;
        }
        String[] parts = databaseVersion.split("\\.");
        if (parts.length < 3) {
            return false;
        }
        try {
            int major = parseLeadingInt(parts[0]);
            int minor = parseLeadingInt(parts[1]);
            int patch = parseLeadingInt(parts[2]);
            return major > 8 || (major == 8 && (minor > 0 || (minor == 0 && patch >= 19)));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseLeadingInt(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        if (end == 0) {
            throw new NumberFormatException(value);
        }
        return Integer.parseInt(value.substring(0, end));
    }

    private String buildDomainTransportPredicate(JdbcQuery jdbcQuery, DomainTransportPlan plan, FDialect dialect) {
        List<String> predicates = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            DbColumn jdbcColumn = resolveDomainTransportColumn(field.getName());
            if (jdbcColumn.isCalculatedField()) {
                if (jdbcColumn instanceof CalculatedDbColumn calcColumn && calcColumn.hasAggregate()) {
                    throw new DomainTransportRefusalException(
                            "Domain transport does not support aggregate calculated field: " + field.getName());
                }
                joinReferencedColumns(jdbcQuery, jdbcColumn);
            } else if (jdbcColumn.getQueryObject() != null
                    && !jdbcQuery.getFrom().getFromObject().isRootEqual(jdbcColumn.getQueryObject())) {
                jdbcQuery.join(jdbcColumn.getQueryObject());
            }

            String alias = jdbcColumn.getQueryObject() != null ? jdbcQueryModel.getAlias(jdbcColumn.getQueryObject()) : null;
            String baseSql = buildColumnSql(jdbcColumn, alias);
            String domainSql = "_d." + dialect.quoteIdentifier(field.getName());
            predicates.add(buildNullSafeEquality(baseSql, domainSql, dialect));
        }
        return String.join(" AND ", predicates);
    }

    private DbColumn resolveDomainTransportColumn(String fieldName) {
        DbColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(fieldName, false, true);
        if (jdbcColumn == null) {
            jdbcColumn = findCalculatedColumn(fieldName);
        }
        if (jdbcColumn == null) {
            throw new DomainTransportRefusalException("Domain transport field not found: " + fieldName);
        }
        return jdbcColumn;
    }

    private String buildNullSafeEquality(String leftSql, String rightSql, FDialect dialect) {
        String productName = dialect != null ? dialect.getProductName() : "";
        if ("PostgreSQL".equalsIgnoreCase(productName)) {
            return leftSql + " IS NOT DISTINCT FROM " + rightSql;
        }
        if ("SQLite".equalsIgnoreCase(productName)) {
            return leftSql + " IS " + rightSql;
        }
        if ("MySQL".equalsIgnoreCase(productName) || dialect == FDialect.MYSQL_DIALECT
                || dialect.getClass().getSimpleName().contains("Mysql8")) {
            return leftSql + " <=> " + rightSql;
        }
        if ("SQLSERVER".equalsIgnoreCase(productName) || dialect == FDialect.SQLSERVER_DIALECT
                || dialect.getDbType() == DbType.SQLSERVER) {
            return "(" + leftSql + " = " + rightSql + " OR (" + leftSql + " IS NULL AND " + rightSql + " IS NULL))";
        }
        throw new DomainTransportRefusalException("Null-safe domain transport predicate unsupported for dialect: " + productName);
    }

    private String getDatabaseProductVersion() {
        try (Connection connection = jdbcQueryModel.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            throw new DomainTransportRefusalException("Failed to detect database version for domain transport", e);
        }
    }

    private void validateInternalRelationName(String relationName) {
        if (relationName == null || !SAFE_INTERNAL_IDENTIFIER.matcher(relationName).matches()) {
            throw new DomainTransportRefusalException("Unsafe domain transport relation name");
        }
    }

    private record DomainTransportSqlInjection(List<String> cteFragments, List<Object> cteParams) {
        private static DomainTransportSqlInjection empty() {
            return new DomainTransportSqlInjection(List.of(), List.of());
        }

        private boolean hasCte() {
            return cteFragments != null && !cteFragments.isEmpty();
        }
    }

    private String buildGroupBy(SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest) {
        String groupBySql = buildAggSql(systemBundlesContext, queryRequest.getGroupBy().stream().collect(Collectors.toMap(GroupRequestDef::getField, e -> e)), queryRequest, true, false);
        return groupBySql;
    }

    private AggregationDbColumn buildAggColumn(QueryObject sqlQueryObject, DbColumn column, DbAggregation agg) {
        String declare = sqlQueryObject.getAlias() + "." + column.getAlias();
        return buildAggColumn1(sqlQueryObject, declare, column, agg);
    }

    private AggregationDbColumn buildAggColumn1(QueryObject sqlQueryObject, String declare, DbColumn column, DbAggregation agg) {

        if (agg == null) {
            agg = DbAggregation.NONE;
        }

        AggregationDbColumn aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), agg);

        switch (agg) {
            case GROUP_CONCAT:
                aggColumn.setDeclare("GROUP_CONCAT(" + declare + " SEPARATOR ',')");
                break;
            case MAX:
                aggColumn.setDeclare("MAX(" + declare + ")");
                break;
            case MIN:
                aggColumn.setDeclare("MIN(" + declare + ")");
                break;
            case PK:
                aggColumn.setDeclare("MAX(" + declare + ")");
                aggColumn.setAggregation(DbAggregation.MAX); // PK 实际使用 MAX 聚合
                break;
            case COUNT:
                aggColumn.setDeclare("COUNT(*)");
                break;
            case SUM:
                aggColumn.setDeclare("SUM(" + declare + ")");
                break;
            case AVG:
                aggColumn.setDeclare("AVG(" + declare + ")");
                break;
            case COUNT_DISTINCT:
                aggColumn.setDeclare("COUNT(DISTINCT " + declare + ")");
                break;
            case STDDEV_POP:
                aggColumn.setDeclare(jdbcQueryModel.getDialect().buildStatFunction("STDDEV_POP", declare));
                break;
            case STDDEV_SAMP:
                aggColumn.setDeclare(jdbcQueryModel.getDialect().buildStatFunction("STDDEV_SAMP", declare));
                break;
            case VAR_POP:
                aggColumn.setDeclare(jdbcQueryModel.getDialect().buildStatFunction("VAR_POP", declare));
                break;
            case VAR_SAMP:
                aggColumn.setDeclare(jdbcQueryModel.getDialect().buildStatFunction("VAR_SAMP", declare));
                break;
            case WINDOW:
                // 窗口函数：直接透传 declare，不包装聚合、不加入 GROUP BY
                aggColumn.setDeclare(declare);
                break;
            case CUSTOM:
                String aggregationFormula = column.getAggregationFormula();
                RX.hasText(aggregationFormula, "传了groupBy为CUSTOM , 但没有定义aggregationFormula，列:" + column.getName());
                aggColumn.setDeclare(aggregationFormula);
                break;
            case NONE:
            default:
                if (column.getType() == DbColumnType.DATETIME) {
                    // 使用方言提供的日期格式化函数，支持多数据库
                    aggColumn.setDeclare(jdbcQueryModel.getDialect().buildDateFormatFunction(declare));
                }
                break;
        }

        return aggColumn;
    }


    private String buildAggSql(SystemBundlesContext systemBundlesContext, Map<String, GroupRequestDef> groupByMap, DbQueryRequestDef queryRequest, boolean addOrder, boolean countToSum) {
        FDialect dialect = jdbcQueryModel != null ? jdbcQueryModel.getDialect() : FDialect.MYSQL_DIALECT;
        JdbcQuery aggJdbcQuery = new JdbcQuery();
        // 使用不含 ORDER BY 的SQL作为子查询，避免生成无意义的排序语句
        SqlQueryObject sqlQueryObject = new SqlQueryObject(this.innerSqlWithoutOrder, "tx");
        List<DbColumn> aggColumns = new ArrayList<>();
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
//            jdbcQueryModel.get
            AggregationDbColumn aggColumn = null;
            DbAggregation c = column.getAggregation();
            String qAlias = dialect.quoteIdentifier(column.getAlias());
            String colRef = jdbcQueryModel.getAlias(sqlQueryObject) + "." + qAlias;

            if (groupByMap != null) {
                GroupRequestDef def = groupByMap.get(column.getName());
                if (def != null && StringUtils.isNotEmpty(def.getAgg())) {
                    //调用者传了自定义的聚合 方式，我们使用它来处理
                    c = DbAggregation.valueOf(def.getAgg());
                }
            }
            if (c == null) {
                c = DbAggregation.NONE;
            }
            switch (c) {
                case AVG:
//                    aggJdbcQuery.getSelect().select()
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(),
                            "avg(" + colRef + ")",
                            column.getType(), DbAggregation.AVG);
                    break;
                case SUM:
                    String declare = "";
                    // 注意: AggregationDbColumn.getSqlColumn() 返回 null，需要检查
                    if (column.getSqlColumn() != null) {
                        switch (column.getSqlColumn().getJdbcType()) {
                            case Types.DOUBLE:
                            case Types.FLOAT:
                                //需要格式化,不再格式化,会引起外部聚合时的问题,这个格式化交给前端处理好了
//                                declare = "format(sum(" + colRef + "),2)";
//                                break;
                            default:
                                declare = "sum(" + colRef + ")";
                        }
                    } else {
                        // 没有 SqlColumn 时（如 AggregationDbColumn），使用默认逻辑
                        declare = "sum(" + colRef + ")";
                    }
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.SUM);
                    break;
                case COUNT:
                    if (countToSum) {
                        //解决前端聚合维度或属性时的BUG
                        declare = "sum(" + colRef + ")";
                        aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.SUM);
                    } else {
                        aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), "count(*)", null, DbAggregation.COUNT);
                    }

                    break;
                case MAX:
                    declare = "max(" + colRef + ")";
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.MAX);
                    break;
                case MIN:
                    declare = "min(" + colRef + ")";
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.MIN);
                    break;
                case COUNT_DISTINCT:
                    if (countToSum) {
                        declare = "sum(" + colRef + ")";
                        aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.SUM);
                    } else {
                        declare = "count(distinct " + colRef + ")";
                        aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.COUNT_DISTINCT);
                    }
                    break;
                case STDDEV_POP:
                case STDDEV_SAMP:
                case VAR_POP:
                case VAR_SAMP:
                case WINDOW:
                    // 统计/窗口函数不可在外层再聚合，返回 null
                    declare = "null";
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.NONE);
                    break;
                case NONE:
                    //意思是不做聚合
                    declare = "null";
                    aggColumn = new AggregationDbColumn(sqlQueryObject, column.getAlias(), declare, column.getType(), DbAggregation.NONE);
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            aggColumns.add(aggColumn);

        }
        aggColumns.add(new AggregationDbColumn(sqlQueryObject, "total", "count(*)", null, DbAggregation.COUNT));
        sqlQueryObject.setColumns(aggColumns);

        aggJdbcQuery.from(sqlQueryObject);
        aggJdbcQuery.select(aggColumns);

        if (addOrder && this.jdbcQuery.getOrder() != null) {
            //group by之后，需要重新搞下排序
            for (DbQueryOrderColumnImpl orderRequestDef : jdbcQuery.getOrder().getOrders()) {

                for (DbColumn aggColumn : aggColumns) {
                    //需要检查传入的列是否在聚合查询中
                    if (StringUtils.equals(aggColumn.getAlias(), orderRequestDef.getSelectColumn().getAlias())) {
                        aggJdbcQuery.addOrder(new DbQueryOrderColumnImpl(aggColumn, orderRequestDef.getOrder(), false, false));
                        break;
                    }
                }

            }
        }

        SimpleSqlJdbcQueryVisitor v = new SimpleSqlJdbcQueryVisitor(systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);
        aggJdbcQuery.accept(v);
//        this.aggSql = v.getSql();
        return v.getSql();
    }


    private void buildSlice(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, SliceRequestDef sliceDef) {
        buildSlice(jdbcQueryModel, jdbcQuery, jdbcQuery.getWhere(), sliceDef, 0);
    }

    private void buildHaving(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, SliceRequestDef havingDef) {
        buildHaving(jdbcQueryModel, jdbcQuery, jdbcQuery.getHaving(), havingDef, 0, "AND");
    }

    private void buildSlice(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef, int level) {
        buildSlice(jdbcQueryModel, jdbcQuery, listCond, sliceDef, level, "AND");
    }

    private void buildSlice(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef, int level, String parentLink) {
        // 处理 $expr 表达式条件
        if (sliceDef._isExpressionCondition()) {
            buildExpressionCondition(jdbcQueryModel, jdbcQuery, listCond, sliceDef, level, parentLink);
            return;
        }

        // 处理 $field 字段引用
        if (sliceDef._isFieldReference()) {
            buildFieldReferenceCondition(jdbcQueryModel, jdbcQuery, listCond, sliceDef, level, parentLink);
            return;
        }

        if (sliceDef._isLogicalGroup()) {
            // 这是一个逻辑组合条件（$or 或 $and）
            String groupLink = sliceDef._getGroupLink();
            List<CondRequestDef> children = sliceDef._getGroupChildren();

            // 校验：如果是OR连接，不能混合聚合字段和普通字段
            if ("OR".equalsIgnoreCase(groupLink)) {
                validateOrConditionGroup(sliceDef);
            }

            // 第一层不加连接符，全部用 AND 连接到父条件
            JdbcQuery.JdbcGroupCond gc = listCond.newGroupCond(level > 0 ? parentLink : "");

            for (CondRequestDef child : children) {
                // 递归时传递当前组的连接类型
                buildSlice(jdbcQueryModel, jdbcQuery, gc, child, level + 1, groupLink);
            }

            listCond.addCond(gc);
        } else {
            // 这是一个普通条件
            buildSingleCondition(jdbcQueryModel, jdbcQuery, listCond, sliceDef, level, parentLink, false);
        }
    }

    private void buildHaving(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond,
                             CondRequestDef havingDef, int level, String parentLink) {
        if (havingDef._isExpressionCondition()) {
            throw RX.throwAUserTip("UNSUPPORTED_HAVING_CONDITION: request.having supports field/op/value and $and/$or groups over aggregate fields; move row-level expressions to slice.");
        }

        // 处理 $field 字段引用
        if (havingDef._isFieldReference()) {
            if (!isAggregateCondition(havingDef.getField())) {
                throw RX.throwAUserTip("HAVING_REQUIRES_AGGREGATE_FIELD: request.having field '" + havingDef.getField()
                        + "' is not an aggregate measure. Use slice for row-level filters.");
            }
            if (!isAggregateCondition(havingDef._getReferencedField())) {
                throw RX.throwAUserTip("HAVING_REQUIRES_AGGREGATE_FIELD: request.having $field reference '" + havingDef._getReferencedField()
                        + "' is not an aggregate measure.");
            }
            buildFieldReferenceCondition(jdbcQueryModel, jdbcQuery, listCond, havingDef, level, parentLink);
            return;
        }

        if (havingDef._isLogicalGroup()) {
            String groupLink = havingDef._getGroupLink();
            List<CondRequestDef> children = havingDef._getGroupChildren();
            JdbcQuery.JdbcGroupCond gc = listCond.newGroupCond(level > 0 ? parentLink : "");
            for (CondRequestDef child : children) {
                buildHaving(jdbcQueryModel, jdbcQuery, gc, child, level + 1, groupLink);
            }
            listCond.addCond(gc);
            return;
        }

        if (!isAggregateCondition(havingDef.getField())) {
            throw RX.throwAUserTip("HAVING_REQUIRES_AGGREGATE_FIELD: request.having field '" + havingDef.getField()
                    + "' is not an aggregate measure. Use slice for row-level filters.");
        }
        buildSingleCondition(jdbcQueryModel, jdbcQuery, listCond, havingDef, level, parentLink, true);
    }

    /**
     * 构建单个条件（非逻辑组合）
     */
    private void buildSingleCondition(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef, int level, String parentLink) {
        buildSingleCondition(jdbcQueryModel, jdbcQuery, listCond, sliceDef, level, parentLink, false);
    }

    private void buildSingleCondition(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond,
                                      CondRequestDef sliceDef, int level, String parentLink,
                                      boolean forceCurrentListCondForAggregate) {
        DbColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(sliceDef.getField(), false, true);

        // 如果在模型中找不到，尝试从计算字段中查找
        if (jdbcColumn == null) {
            jdbcColumn = findCalculatedColumn(sliceDef.getField());
        }

        if (jdbcColumn == null) {
            throw RX.throwAUserTip(DatasetMessages.queryColumnNotfound(sliceDef.getField(), jdbcQueryModel.findDimension(sliceDef.getField())));
        }

        // 判断是否为聚合条件
        boolean isAggregateCondition = isAggregateCondition(sliceDef.getField());

        // 计算字段需要遍历其引用的列来触发 JOIN
        if (jdbcColumn.isCalculatedField()) {
            joinReferencedColumns(jdbcQuery, jdbcColumn);
            // 聚合条件需要添加到HAVING，否则添加到WHERE
            if (isAggregateCondition) {
                JdbcQuery.JdbcListCond target = forceCurrentListCondForAggregate ? listCond : jdbcQuery.getHaving();
                sqlFormulaService.buildAndAddToJdbcCond(target, sliceDef.getOp(), jdbcColumn, null, sliceDef.getValue(), parentLink);
            } else {
                sqlFormulaService.buildAndAddToJdbcCond(listCond, sliceDef.getOp(), jdbcColumn, null, sliceDef.getValue(), parentLink);
            }
            return;
        }

        if (jdbcColumn.getQueryObject() != null && !(jdbcQuery.getFrom().getFromObject().isRootEqual(jdbcColumn.getQueryObject()))) {
            //需要加入left join
            jdbcQuery.join(jdbcColumn.getQueryObject());
        }
        String alias = jdbcQueryModel.getAlias(jdbcColumn.getQueryObject());

        if (jdbcColumn.isDimension()) {
            DbModelParentChildDimensionImpl pp = jdbcColumn.getDecorate(DbDimensionColumn.class).getDimension().getDecorate(DbModelParentChildDimensionImpl.class);
            // 只有 hierarchy 视角的列（team$hierarchy$id）或层级操作符才使用闭包表
            // 默认视角（team$id）按普通维度处理，精确匹配
            boolean isHierarchyColumn = sliceDef.getField() != null && sliceDef.getField().contains("$hierarchy$");
            String op = sliceDef.getOp();
            HierarchyOperator hierarchyOp = hierarchyOperatorService.get(op);

            if (pp != null && (isHierarchyColumn || hierarchyOp != null)) {
                //这是一个parentChild维的层级查询，条件重写为使用closure表
                boolean isAncestorDirection = hierarchyOp != null && hierarchyOp.isAncestorDirection();

                if (isAncestorDirection && pp.getAncestorClosureQueryObject() != null) {
                    // 祖先方向: JOIN closure ON fact.FK = closure.parent_id, WHERE closure.child_id = value
                    jdbcQuery.join(pp.getAncestorClosureQueryObject(), pp.getForeignKey());
                    alias = jdbcQueryModel.getAlias(pp.getAncestorClosureQueryObject());
                    jdbcColumn = pp.getChildKeyJdbcColumn();
                } else {
                    // 后代方向（默认）: JOIN closure ON fact.FK = closure.child_id, WHERE closure.parent_id = value
                    jdbcQuery.join(pp.getClosureQueryObject(), pp.getForeignKey());
                    alias = jdbcQueryModel.getAlias(pp.getClosureQueryObject());
                    jdbcColumn = pp.getParentKeyJdbcColumn();
                }

                // 处理层级操作符的 distance 条件
                if (hierarchyOp != null) {
                    // 当闭包操作符在 $or 内时，distance 条件和值条件必须封装在子组内，
                    // 否则 SQL 运算优先级会导致 distance 条件与错误的分支结合。
                    // 例如 $or [IS NULL, descendantsOf(1)] 应生成:
                    //   (company_id IS NULL OR (closure.distance > 0 AND closure.parent_id = 1))
                    // 而不是:
                    //   (company_id IS NULL AND closure.distance > 0 OR closure.parent_id = 1)
                    if ("OR".equalsIgnoreCase(parentLink)) {
                        JdbcQuery.JdbcGroupCond subGroup = jdbcQuery.getWhere().newGroupCond(parentLink);
                        hierarchyOp.buildDistanceCondition(subGroup, alias, sliceDef.getMaxDepth());
                        sliceDef.setOp(sliceDef.getValue() instanceof List ? "in" : "=");
                        sqlFormulaService.buildAndAddToJdbcCond(subGroup, sliceDef.getOp(), jdbcColumn, alias, sliceDef.getValue(), "AND");
                        listCond.addCond(subGroup);
                        return;
                    }
                    hierarchyOp.buildDistanceCondition(listCond, alias, sliceDef.getMaxDepth());
                    // 将 op 转换为标准操作符（in 或 =）
                    sliceDef.setOp(sliceDef.getValue() instanceof List ? "in" : "=");
                }
            }
        }
        if (jdbcColumn.isProperty() && jdbcColumn.getDecorate(DbPropertyColumn.class).getProperty().isBit()) {
            //是位图列,重写为bitIn
            sliceDef.setOp(CondType.BIT_IN.getCode());
        }

        pushAggregateRelationFilterIfSafe(jdbcQueryModel, jdbcColumn, sliceDef, parentLink);

        // 聚合条件需要添加到HAVING，否则添加到WHERE
        if (isAggregateCondition) {
            JdbcQuery.JdbcListCond target = forceCurrentListCondForAggregate ? listCond : jdbcQuery.getHaving();
            sqlFormulaService.buildAndAddToJdbcCond(target, sliceDef.getOp(), jdbcColumn, alias, sliceDef.getValue(), parentLink);
        } else {
            sqlFormulaService.buildAndAddToJdbcCond(listCond, sliceDef.getOp(), jdbcColumn, alias, sliceDef.getValue(), parentLink);
        }
    }

    private void pushAggregateRelationFilterIfSafe(JdbcQueryModel jdbcQueryModel, DbColumn jdbcColumn,
                                                   CondRequestDef sliceDef, String parentLink) {
        if (!isConjunctiveCondition(parentLink) || sliceDef == null || jdbcColumn == null) {
            return;
        }
        if (jdbcColumn instanceof AggregateRelationOutputColumn aggregateRelationColumn) {
            aggregateRelationColumn.pushAggregateRelationCondition(sliceDef.getOp(), sliceDef.getValue());
            return;
        }
        pushAggregateRelationJoinKeyFilters(jdbcQueryModel, sliceDef.getField(), sliceDef.getOp(), sliceDef.getValue());
    }

    private void pushAggregateRelationJoinKeyFilters(JdbcQueryModel jdbcQueryModel, String fieldName, String op, Object value) {
        if (fieldName == null || fieldName.isBlank()) {
            return;
        }
        for (AggregateRelationQueryObject queryObject : collectAggregateRelationQueryObjects(jdbcQueryModel)) {
            queryObject.pushAggregateRelationJoinKeyCondition(fieldName, op, value);
        }
    }

    private boolean isConjunctiveCondition(String parentLink) {
        return parentLink == null || parentLink.isBlank() || "AND".equalsIgnoreCase(parentLink);
    }

    private void rejectAggregateConditionInSlice(CondRequestDef sliceDef) {
        if (sliceDef == null) {
            return;
        }
        if (sliceDef._isLogicalGroup()) {
            List<CondRequestDef> children = sliceDef._getGroupChildren();
            if (children != null) {
                for (CondRequestDef child : children) {
                    rejectAggregateConditionInSlice(child);
                }
            }
            return;
        }
        String field = sliceDef.getField();
        if (field != null && isAggregateCondition(field)) {
            throw RX.throwAUserTip("AGGREGATE_MEASURE_IN_SLICE: field '" + field
                    + "' is an aggregate measure. Move this condition from slice to request.having, for example having: [{field:'"
                    + field + "', op:'" + sliceDef.getOp() + "', value:" + sliceDef.getValue() + "}].");
        }
    }

    private enum SliceConditionPhase {
        ROW,
        AGGREGATE
    }

    private enum PostAggregateSlicePhase {
        INNER,
        POST
    }

    private void splitPostAggregateSlice(
            List<SliceRequestDef> sliceItems,
            Set<String> postAggregateNames,
            List<SliceRequestDef> inner,
            List<SliceRequestDef> post) {
        if (sliceItems == null || sliceItems.isEmpty()) {
            return;
        }
        for (SliceRequestDef item : sliceItems) {
            PostAggregateSlicePhase phase = classifyPostAggregateSlicePhase(item, postAggregateNames);
            if (phase == PostAggregateSlicePhase.POST) {
                post.add(item);
            } else {
                inner.add(item);
            }
        }
    }

    private PostAggregateSlicePhase classifyPostAggregateSlicePhase(
            CondRequestDef sliceDef,
            Set<String> postAggregateNames) {
        if (sliceDef == null || postAggregateNames == null || postAggregateNames.isEmpty()) {
            return PostAggregateSlicePhase.INNER;
        }
        if (!sliceDef._isLogicalGroup()) {
            return postAggregateNames.contains(sliceDef.getField())
                    ? PostAggregateSlicePhase.POST
                    : PostAggregateSlicePhase.INNER;
        }
        List<CondRequestDef> children = sliceDef._getGroupChildren();
        if (children == null || children.isEmpty()) {
            return PostAggregateSlicePhase.INNER;
        }
        PostAggregateSlicePhase phase = null;
        for (CondRequestDef child : children) {
            PostAggregateSlicePhase childPhase = classifyPostAggregateSlicePhase(child, postAggregateNames);
            if (phase == null) {
                phase = childPhase;
                continue;
            }
            if (phase != childPhase) {
                throw RX.throwAUserTip("MIXED_INNER_AND_POST_AGGREGATE_SLICE: a single logical slice group cannot mix base/aggregate fields and postAggregateCalculations aliases because it cannot be safely split across query stages.");
            }
        }
        return phase == null ? PostAggregateSlicePhase.INNER : phase;
    }

    private SliceConditionPhase classifySliceConditionPhase(CondRequestDef sliceDef) {
        if (sliceDef == null) {
            return SliceConditionPhase.ROW;
        }
        if (sliceDef._isExpressionCondition() || sliceDef._isFieldReference()) {
            return SliceConditionPhase.ROW;
        }
        if (!sliceDef._isLogicalGroup()) {
            String field = sliceDef.getField();
            return field != null && isAggregateCondition(field)
                    ? SliceConditionPhase.AGGREGATE
                    : SliceConditionPhase.ROW;
        }

        List<CondRequestDef> children = sliceDef._getGroupChildren();
        if (children == null || children.isEmpty()) {
            return SliceConditionPhase.ROW;
        }

        SliceConditionPhase phase = null;
        for (CondRequestDef child : children) {
            SliceConditionPhase childPhase = classifySliceConditionPhase(child);
            if (phase == null) {
                phase = childPhase;
                continue;
            }
            if (phase != childPhase) {
                List<String> aggregateFields = new ArrayList<>();
                List<String> normalFields = new ArrayList<>();
                collectFieldsByType(sliceDef, aggregateFields, normalFields);
                throw RX.throwAUserTip("MIXED_ROW_AND_AGGREGATE_SLICE: a single logical slice group cannot mix row-level fields ("
                        + String.join(", ", normalFields) + ") and aggregate measures ("
                        + String.join(", ", aggregateFields)
                        + ") because it cannot be safely split between WHERE and HAVING. Keep row-level filters in slice and aggregate filters in having or separate top-level slice entries.");
            }
        }
        return phase == null ? SliceConditionPhase.ROW : phase;
    }

    /**
     * 构建 $expr 表达式条件
     * <p>
     * 使用 {@link SliceExpressionProcessor} 将表达式编译为 SQL 条件。
     * </p>
     *
     * @param jdbcQueryModel 查询模型
     * @param jdbcQuery      JDBC 查询对象
     * @param listCond       条件列表
     * @param sliceDef       条件定义
     * @param level          嵌套层级
     * @param parentLink     父级连接类型（AND/OR）
     * @since 8.3.0
     */
    private void buildExpressionCondition(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery,
                                          JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef,
                                          int level, String parentLink) {
        String expression = sliceDef.getExpr();

        // 使用表达式处理器
        SliceExpressionProcessor processor = new SliceExpressionProcessor(
                jdbcQueryModel,
                jdbcQueryModel.getDialect(),
                null  // ApplicationContext 可选
        );

        String sql = processor.processExpression(expression);

        // 添加到条件列表（使用 parentLink 与其他条件保持一致）
        listCond.addRawSql(parentLink, sql);

        if (log.isDebugEnabled()) {
            log.debug("Added $expr condition: {} -> SQL: {}", expression, sql);
        }
    }

    /**
     * 构建 $field 字段引用条件
     * <p>
     * 将 value 中的字段引用转换为字段间比较条件。
     * </p>
     *
     * @param jdbcQueryModel 查询模型
     * @param jdbcQuery      JDBC 查询对象
     * @param listCond       条件列表
     * @param sliceDef       条件定义
     * @param level          嵌套层级
     * @param parentLink     父级连接类型（AND/OR）
     * @since 8.3.0
     */
    private void buildFieldReferenceCondition(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery,
                                               JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef,
                                               int level, String parentLink) {
        String leftFieldName = sliceDef.getField();
        String rightFieldName = sliceDef._getReferencedField();
        String op = sliceDef.getOp();

        // 解析左侧字段
        DbColumn leftColumn = jdbcQueryModel.findJdbcColumnForCond(leftFieldName, false, true);
        if (leftColumn == null) {
            leftColumn = findCalculatedColumn(leftFieldName);
        }
        if (leftColumn == null) {
            throw RX.throwAUserTip(DatasetMessages.queryColumnNotfound(leftFieldName, jdbcQueryModel.findDimension(leftFieldName)));
        }

        // 解析右侧字段
        DbColumn rightColumn = jdbcQueryModel.findJdbcColumnForCond(rightFieldName, false, true);
        if (rightColumn == null) {
            rightColumn = findCalculatedColumn(rightFieldName);
        }
        if (rightColumn == null) {
            throw RX.throwAUserTip(DatasetMessages.queryColumnNotfound(rightFieldName, jdbcQueryModel.findDimension(rightFieldName)));
        }

        // 确保需要的表已 JOIN
        if (leftColumn.isCalculatedField()) {
            joinReferencedColumns(jdbcQuery, leftColumn);
        } else if (leftColumn.getQueryObject() != null && !jdbcQuery.getFrom().getFromObject().isRootEqual(leftColumn.getQueryObject())) {
            jdbcQuery.join(leftColumn.getQueryObject());
        }
        if (rightColumn.isCalculatedField()) {
            joinReferencedColumns(jdbcQuery, rightColumn);
        } else if (rightColumn.getQueryObject() != null && !jdbcQuery.getFrom().getFromObject().isRootEqual(rightColumn.getQueryObject())) {
            jdbcQuery.join(rightColumn.getQueryObject());
        }

        // 获取别名
        String leftAlias = jdbcQueryModel.getAlias(leftColumn.getQueryObject());
        String rightAlias = jdbcQueryModel.getAlias(rightColumn.getQueryObject());

        // 构建 SQL 表达式
        String leftSql = buildColumnSql(leftColumn, leftAlias);
        String rightSql = buildColumnSql(rightColumn, rightAlias);
        String normalizedOp = normalizeOperator(op);

        String sql = leftSql + " " + normalizedOp + " " + rightSql;

        // 添加到条件列表（使用 parentLink 与其他条件保持一致）
        listCond.addRawSql(parentLink, sql);

        if (log.isDebugEnabled()) {
            log.debug("Added $field condition: {} {} ${} -> SQL: {}",
                    leftFieldName, op, rightFieldName, sql);
        }
    }

    /**
     * 为计算字段引用的所有列触发 JOIN
     * <p>
     * 计算字段（如 count(student$caption)）本身没有 queryObject，
     * 但其引用的列（如 student$caption）可能需要 JOIN。
     * </p>
     *
     * @param jdbcQuery JDBC 查询对象
     * @param jdbcColumn 计算字段列
     */
    private void joinReferencedColumns(JdbcQuery jdbcQuery, DbColumn jdbcColumn) {
        if (jdbcColumn instanceof com.foggyframework.dataset.db.model.spi.support.CalculatedDbColumn calcColumn) {
            java.util.Set<com.foggyframework.dataset.db.model.spi.DbQueryColumn> refs = calcColumn.getReferencedColumns();
            if (refs != null) {
                for (com.foggyframework.dataset.db.model.spi.DbQueryColumn ref : refs) {
                    QueryObject refQueryObject = ref.getQueryObject();
                    if (refQueryObject != null && !jdbcQuery.getFrom().getFromObject().isRootEqual(refQueryObject)) {
                        jdbcQuery.join(refQueryObject);
                    }
                }
            }
        }
    }

    /**
     * 构建列的 SQL 表达式
     */
    private String buildColumnSql(DbColumn column, String alias) {
        if (column.isCalculatedField()) {
            // 计算字段直接返回其 declare
            return column.getDeclare();
        }

        // 普通列带别名
        String columnName = column.getSqlColumn() != null ? column.getSqlColumn().getName() : column.getAlias();
        if (alias != null && !alias.isEmpty()) {
            return alias + "." + columnName;
        }
        return columnName;
    }

    /**
     * 规范化操作符
     */
    private String normalizeOperator(String op) {
        if (op == null) {
            return "=";
        }
        switch (op.toLowerCase()) {
            case "eq":
                return "=";
            case "ne":
            case "<>":
                return "!=";
            case "gt":
                return ">";
            case "gte":
                return ">=";
            case "lt":
                return "<";
            case "lte":
                return "<=";
            default:
                return op;
        }
    }

    /**
     * 判断指定字段是否为聚合条件
     * <p>
     * 聚合条件指的是对聚合字段（如SUM、AVG等）的过滤，这类条件应该放在HAVING子句中。
     * 判断依据：检查字段名是否在 parsedInlineExpressions 的聚合列映射中。
     * </p>
     *
     * @param fieldName 字段名
     * @return true 如果是聚合条件，需要放入HAVING；false 如果是普通条件，放入WHERE
     */
    private boolean isAggregateCondition(String fieldName) {
        if (parsedInlineExpressions == null || parsedInlineExpressions.getColumnAggregations() == null) {
            return false;
        }
        return parsedInlineExpressions.getColumnAggregations().containsKey(fieldName);
    }

    /**
     * 校验 OR 连接的条件组
     * <p>
     * OR 连接的条件组中不能同时包含聚合字段和普通字段，因为：
     * <ul>
     *   <li>聚合字段的条件必须放在 HAVING 子句</li>
     *   <li>普通字段的条件必须放在 WHERE 子句</li>
     *   <li>WHERE 和 HAVING 子句不能用 OR 连接</li>
     * </ul>
     * 例如：{@code (customer_type='VIP' OR totalAmount>1000)} 在 SQL 中无法表达，
     * 因为无法写成 {@code WHERE customer_type='VIP' OR HAVING SUM(amount)>1000}
     * </p>
     *
     * @param condGroup OR 连接的条件组
     * @throws IllegalArgumentException 如果检测到混合使用聚合字段和普通字段
     */
    private void validateOrConditionGroup(CondRequestDef condGroup) {
        List<CondRequestDef> children = condGroup._getGroupChildren();
        if (children == null || children.isEmpty()) {
            return;
        }

        List<String> aggregateFields = new ArrayList<>();
        List<String> normalFields = new ArrayList<>();

        // 递归收集所有叶子字段
        collectFieldsByType(condGroup, aggregateFields, normalFields);

        // 如果同时存在聚合字段和普通字段，抛出错误
        if (!aggregateFields.isEmpty() && !normalFields.isEmpty()) {
            String link = condGroup._getGroupLink();
            throw RX.throwAUserTip(DatasetMessages.queryMixedConditionNotAllowed(
                    link,
                    String.join(", ", aggregateFields),
                    String.join(", ", normalFields)
            ));
        }
    }

    /**
     * 递归收集条件组中的字段，按类型分类
     *
     * @param cond            条件定义（可能是组合条件或叶子条件）
     * @param aggregateFields 聚合字段列表（输出参数）
     * @param normalFields    普通字段列表（输出参数）
     */
    private void collectFieldsByType(CondRequestDef cond, List<String> aggregateFields, List<String> normalFields) {
        if (cond._isLogicalGroup()) {
            // 递归处理子条件
            for (CondRequestDef child : cond._getGroupChildren()) {
                collectFieldsByType(child, aggregateFields, normalFields);
            }
        } else {
            // 叶子条件，检查字段类型
            String fieldName = cond.getField();
            if (fieldName != null && !fieldName.isEmpty()) {
                if (isAggregateCondition(fieldName)) {
                    aggregateFields.add(fieldName);
                } else {
                    normalFields.add(fieldName);
                }
            }
        }
    }

    /**
     * 预处理 columns 中的内联表达式
     * <p>
     * 检测 columns 中的内联表达式（如 "YEAR(orderdate) AS orderYear"），
     * 将其转换为 calculatedFields 定义，并将 columns 中的项替换为别名。
     * </p>
     * <p>
     * 如果 context 中已有预处理结果（parsedInlineExpressions），则跳过重复解析。
     * </p>
     *
     * @param queryRequest 查询请求
     * @param context      查询生命周期上下文（可选）
     */
    private void preprocessInlineExpressions(DbQueryRequestDef queryRequest, ModelResultContext context) {
        // 检查是否已在 InlineExpressionPreprocessStep 中预处理
        ModelResultContext.ParsedInlineExpressions parsed =
                context != null ? context.getParsedInlineExpressions() : null;

        if (parsed != null && parsed.isProcessed()) {
            // 已预处理，直接使用结果并保存到成员变量
            this.parsedInlineExpressions = parsed;
            if (log.isDebugEnabled()) {
                log.debug("Using preprocessed inline expressions from context, skipping redundant parsing");
            }
            // columns 和 calculatedFields 已经在 InlineExpressionPreprocessStep 中更新到 queryRequest
            return;
        }

        // 未预处理，执行原有逻辑

        // 注入 QM 预定义的 calculatedFields（与 InlineExpressionPreprocessStep 相同逻辑）
        injectPredefinedCalculatedFields(queryRequest, context);

        List<String> columns = queryRequest.getColumns();
        if (columns == null || columns.isEmpty()) {
            return;
        }

        // 确保 calculatedFields 列表存在
        List<CalculatedFieldDef> calculatedFields = queryRequest.getCalculatedFields();
        if (calculatedFields == null) {
            calculatedFields = new ArrayList<>();
            queryRequest.setCalculatedFields(calculatedFields);
        }

        // 用于生成自动别名的计数器
        int autoAliasCounter = 1;

        // 遍历 columns，检测内联表达式
        List<String> newColumns = new ArrayList<>(columns.size());
        for (String columnDef : columns) {
            InlineExpressionParser.InlineExpression inlineExp = InlineExpressionParser.parse(columnDef);

            if (inlineExp != null) {
                // 这是一个内联表达式
                String alias = inlineExp.getAlias();
                if (alias == null) {
                    // 自动生成别名
                    alias = "expr_" + autoAliasCounter++;
                }

                // 创建 CalculatedFieldDef
                CalculatedFieldDef calcFieldDef = new CalculatedFieldDef();
                calcFieldDef.setName(alias);
                calcFieldDef.setExpression(inlineExp.getExpression());
                calculatedFields.add(calcFieldDef);

                if (log.isDebugEnabled()) {
                    log.debug("Converted inline expression '{}' to calculated field: name='{}', expression='{}'",
                            columnDef, alias, inlineExp.getExpression());
                }

                // 将 columns 中的项替换为别名
                newColumns.add(alias);
            } else {
                // 保持原样
                newColumns.add(columnDef);
            }
        }

        // 更新 columns
        queryRequest.setColumns(newColumns);

        if (log.isDebugEnabled() && !calculatedFields.isEmpty()) {
            log.debug("After preprocessing: {} calculated fields", calculatedFields.size());
        }
    }

    /**
     * 注入 QM 预定义的 calculatedFields
     * <p>
     * 仅注入查询 columns 中引用到且未被 DSL 覆盖的预定义字段。
     * 当直接调用 analysisQueryRequest（跳过 Step 流水线）时由此方法注入。
     * </p>
     */
    private void injectPredefinedCalculatedFields(DbQueryRequestDef queryRequest, ModelResultContext context) {
        PredefinedCalculatedFieldInjector.inject(queryRequest, jdbcQueryModel, context, log);
    }

    /**
     * 处理动态计算字段
     * <p>
     * 编译 calculatedFields 中的表达式，生成 CalculatedJdbcColumn 对象。
     * 结果同时存储在 engine 实例和 ModelResultContext 中。
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param queryRequest         查询请求
     * @param context              查询生命周期上下文（可选）
     */
    private void processCalculatedFields(SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest, ModelResultContext context) {
        if (queryRequest.getCalculatedFields() == null || queryRequest.getCalculatedFields().isEmpty()) {
            this.calculatedColumns = new ArrayList<>();
            if (context != null) {
                context.setCalculatedColumns(this.calculatedColumns);
            }
            return;
        }

        // 使用 QueryModel 提供的计算字段处理器
        ApplicationContext appCtx = systemBundlesContext.getApplicationContext();
        CalculatedFieldProcessor processor = jdbcQueryModel.getCalculatedFieldProcessor();

        if (processor == null) {
            log.warn("QueryModel does not support calculated fields: {}", jdbcQueryModel.getName());
            this.calculatedColumns = new ArrayList<>();
            if (context != null) {
                context.setCalculatedColumns(this.calculatedColumns);
            }
            return;
        }

        if (processor instanceof SqlCalculatedFieldProcessor sqlProcessor) {
            sqlProcessor.setCalculateQueryContext(buildCalculateQueryContext(queryRequest, context));
            sqlProcessor.setGroupedQuery(queryRequest.hasGroupBy());
        }

        // 处理所有计算字段
        this.calculatedColumns = processor.processCalculatedFields(
                queryRequest.getCalculatedFields(),
                appCtx
        );

        // 获取 SQL 表达式上下文（用于后续列解析）
        if (processor instanceof SqlCalculatedFieldProcessor) {
            this.sqlExpContext = ((SqlCalculatedFieldProcessor) processor).getContext();
        }

        // 将结果存入 ModelResultContext
        if (context != null) {
            context.setCalculatedColumns(this.calculatedColumns);
        }

        if (log.isDebugEnabled()) {
            log.debug("Processed {} calculated fields", calculatedColumns.size());
            for (CalculatedDbColumn column : calculatedColumns) {
                log.debug("  {} = {}", column.getName(), column.getDeclare());
            }
        }
    }

    private CalculateQueryContext buildCalculateQueryContext(DbQueryRequestDef queryRequest, ModelResultContext context) {
        List<String> groupByFields = queryRequest.getGroupBy() == null
                ? List.of()
                : queryRequest.getGroupBy().stream()
                .map(GroupRequestDef::getField)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toList());

        Set<String> systemSliceFields = new LinkedHashSet<>();
        if (context != null && context.getSystemSlice() != null) {
            for (SliceRequestDef slice : context.getSystemSlice()) {
                collectSliceFields(slice, systemSliceFields);
            }
        }

        return new CalculateQueryContext(
                groupByFields,
                queryRequest.getColumns() == null ? List.of() : queryRequest.getColumns(),
                systemSliceFields,
                supportsGroupedAggregateWindow(),
                isTimeWindowPostCalculatedFields(context)
        );
    }

    private void collectSliceFields(CondRequestDef cond, Set<String> fields) {
        if (cond == null) {
            return;
        }
        if (StringUtils.isNotEmpty(cond.getField())) {
            fields.add(cond.getField());
        }
        if (cond.getOr() != null) {
            for (CondRequestDef item : cond.getOr()) {
                collectSliceFields(item, fields);
            }
        }
        if (cond.getAnd() != null) {
            for (CondRequestDef item : cond.getAnd()) {
                collectSliceFields(item, fields);
            }
        }
    }

    private boolean supportsGroupedAggregateWindow() {
        FDialect dialect = jdbcQueryModel != null ? jdbcQueryModel.getDialect() : null;
        return CalculateDialectCapabilities.supportsGroupedAggregateWindow(
                dialect,
                jdbcQueryModel != null ? jdbcQueryModel.getDataSource() : null
        );
    }

    private boolean isTimeWindowPostCalculatedFields(ModelResultContext context) {
        if (context == null || context.getExtData() == null) {
            return false;
        }
        return Boolean.TRUE.equals(context.getExtData().get("timeWindowPostCalculatedFields"));
    }

    /**
     * 根据名称查找计算字段
     *
     * @param columnName 列名
     * @return 计算字段列，如果不存在返回 null
     */
    private CalculatedDbColumn findCalculatedColumn(String columnName) {
        if (calculatedColumns == null || calculatedColumns.isEmpty()) {
            return null;
        }
        for (CalculatedDbColumn column : calculatedColumns) {
            if (StringUtils.equals(column.getName(), columnName)) {
                return column;
            }
        }
        return null;
    }

    /**
     * 获取处理后的计算字段列表
     *
     * @return 计算字段列列表
     */
    public List<CalculatedDbColumn> getCalculatedColumns() {
        return calculatedColumns;
    }

    /**
     * 存在分组时处理排序
     * <p>
     * 存在 GROUP BY 时，ORDER BY 字段必须在 SELECT 中，否则会导致 SQL 错误。
     * 此方法作为 Engine 层的最后一道防线，确保最终 SQL 的正确性。
     * </p>
     * <p>
     * 处理顺序：
     * <ol>
     *   <li>先处理用户请求的 orderBy（queryRequest.getOrderBy()）</li>
     *   <li>再处理 QueryModel 默认排序（jdbcQueryModel.getOrders()）</li>
     * </ol>
     * 对于不在 SELECT 中的字段，记录警告并跳过。
     * </p>
     *
     * @param jdbcQuery      查询对象
     * @param jdbcQueryModel 查询模型
     * @param queryRequest   查询请求
     */
    private void addOrderByForGroupBy(JdbcQuery jdbcQuery, JdbcQueryModel jdbcQueryModel, DbQueryRequestDef queryRequest) {
        // 构建业务名 -> SELECT 列的映射
        // queryRequest.getColumns() 中的名称与 jdbcQuery.getSelect().getColumns() 一一对应
        List<DbColumn> selectColumns = jdbcQuery.getSelect().getColumns();
        List<String> requestColumns = queryRequest.getColumns();
        Map<String, DbColumn> columnNameMap = new java.util.HashMap<>();
        Set<String> postAggregateNames = postAggregateNames(queryRequest);

        if (requestColumns != null && requestColumns.size() == selectColumns.size()) {
            for (int i = 0; i < requestColumns.size(); i++) {
                columnNameMap.put(requestColumns.get(i), selectColumns.get(i));
            }
        } else {
            // 回退：使用 alias 作为 key
            for (DbColumn col : selectColumns) {
                if (col.getAlias() != null) {
                    columnNameMap.put(col.getAlias(), col);
                }
            }
        }

        List<String> skippedFields = new ArrayList<>();

        // 1. 处理用户请求的 orderBy
        if (queryRequest.getOrderBy() != null) {
            for (OrderRequestDef orderRequestDef : queryRequest.getOrderBy()) {
                String fieldName = orderRequestDef.getField();
                if (postAggregateNames.contains(fieldName)) {
                    continue;
                }

                // 查找匹配的 SELECT 列
                DbColumn selectColumn = columnNameMap.get(fieldName);

                if (selectColumn != null) {
                    // 字段在 SELECT 中，添加排序
                    validate(orderRequestDef.getDir());
                    jdbcQuery.addOrder(new DbQueryOrderColumnImpl(
                            selectColumn,
                            orderRequestDef.getDir(),
                            orderRequestDef.isNullLast(),
                            orderRequestDef.isNullFirst()
                    ));
                } else {
                    // 字段不在 SELECT 中，记录并跳过
                    skippedFields.add(fieldName);
                }
            }
        }

        // 2. 处理 QueryModel 默认排序
        // 注意：默认排序使用的是模型定义的字段名/alias，需要匹配
        List<DbQueryOrderColumnImpl> modelOrders = jdbcQueryModel.getOrders();
        if (modelOrders != null && !modelOrders.isEmpty()) {
            for (DbQueryOrderColumnImpl modelOrder : modelOrders) {
                DbColumn orderColumn = modelOrder.getSelectColumn();
                String fieldName = orderColumn.getName();
                String fieldAlias = orderColumn.getAlias();

                // 尝试用 name 和 alias 查找
                DbColumn selectColumn = columnNameMap.get(fieldName);
                if (selectColumn == null && fieldAlias != null) {
                    selectColumn = columnNameMap.get(fieldAlias);
                }

                if (selectColumn != null) {
                    // 检查是否已添加（避免重复）
                    final DbColumn finalSelectColumn = selectColumn;
                    boolean alreadyAdded = false;
                    if (jdbcQuery.getOrder() != null && jdbcQuery.getOrder().getOrders() != null) {
                        alreadyAdded = jdbcQuery.getOrder().getOrders().stream()
                                .anyMatch(o -> o.getSelectColumn() == finalSelectColumn);
                    }

                    if (!alreadyAdded) {
                        jdbcQuery.addOrder(new DbQueryOrderColumnImpl(
                                selectColumn,
                                modelOrder.getOrder(),
                                modelOrder.isNullLast(),
                                modelOrder.isNullFirst()
                        ));
                    }
                }
            }
        }

        // 记录警告日志
        if (!skippedFields.isEmpty()) {
//            log.warn("GroupBy 模式下忽略了不在 SELECT 中的 orderBy 字段: {}", skippedFields);
            throw RX.throwA("GroupBy 模式下 orderBy 字段 必须在columns存在，但: " + skippedFields+" 没有在columns中出现，请根据需求调整groupBy中的字段，或加入field");
        }
    }
}
