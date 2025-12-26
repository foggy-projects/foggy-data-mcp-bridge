package com.foggyframework.dataset.db.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.common.query.CondType;
import com.foggyframework.dataset.db.model.def.query.request.*;
import com.foggyframework.dataset.db.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.db.model.engine.expression.SqlCalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.engine.expression.SqlExpContext;
import com.foggyframework.dataset.db.model.engine.formula.JdbcLink;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.db.model.engine.query_model.DbQueryModelImpl;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.dimension.DbModelParentChildDimensionImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.db.model.impl.utils.SqlQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.AggregationJdbcColumn;
import com.foggyframework.dataset.db.model.spi.support.CalculatedJdbcColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Data
public class JdbcModelQueryEngine implements QueryEngine {
    JdbcQueryModel jdbcQueryModel;

    JdbcQuery jdbcQuery;

    SqlFormulaService sqlFormulaService;

    /**
     * SQL 表达式上下文（用于计算字段）
     */
    SqlExpContext sqlExpContext;

    /**
     * 处理后的计算字段列表
     */
    List<CalculatedJdbcColumn> calculatedColumns;

    /**
     * 不含 ORDER BY 的基础SQL，用于聚合查询的子查询
     */
    String innerSqlWithoutOrder;
    String innerSql;
    String sql;
    String aggSql;

    List values;
    private static final String PATTERN = "^[a-zA-Z\\s]+$";
    private static final Pattern PATTERN_OBJECT = Pattern.compile(PATTERN);

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

        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.setQueryRequest(queryRequest);
        jdbcQuery.from(jdbcQueryModel.getQueryObject());

        //补上必要的模型
        if (jdbcQueryModel.getJdbcModelList().size() > 1) {
            for (int i = 1; i < jdbcQueryModel.getJdbcModelList().size(); i++) {
                TableModel tm = jdbcQueryModel.getJdbcModelList().get(i);
                DbQueryModelImpl.JdbcModelDx dx = tm.getDecorate(DbQueryModelImpl.JdbcModelDx.class);

                if (dx.getOnBuilder() != null) {
                    jdbcQuery.preJoin(dx.getQueryObject(), dx.getOnBuilder(), dx.getJoinType());
                } else {
                    jdbcQuery.preJoin(dx.getQueryObject(), dx.getForeignKey());
                }
            }
        }

        // 0. 预处理 columns 中的内联表达式，转换为 calculatedFields
        // 如果 context 中已有预处理结果，则跳过
        preprocessInlineExpressions(queryRequest, context);

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

        // 2.加入切片条件,注意，切片暂时不考虑or
        if (queryRequest.getSlice() != null) {
            for (SliceRequestDef sliceDef : queryRequest.getSlice()) {
                buildSlice(jdbcQueryModel, jdbcQuery, sliceDef);
            }
        }


        // 3.加权限语句
        for (DbQueryDimension queryDimension : jdbcQueryModel.getQueryDimensions()) {
            if (queryDimension.getQueryAccess() != null && queryDimension.getQueryAccess().getQueryBuilder() != null) {
                ExpEvaluator ee = DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
                ee.setVar("query", jdbcQuery);
                ee.setVar("dim", queryDimension.getDimension());
                ee.setVar("dimension", queryDimension.getDimension());
                queryDimension.getQueryAccess().getQueryBuilder().autoApply(ee);
            }
        }
        for (DbQueryProperty queryProperty : jdbcQueryModel.getQueryProperties()) {
            if (queryProperty.getQueryAccess() != null && queryProperty.getQueryAccess().getQueryBuilder() != null) {
                ExpEvaluator ee = DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
                ee.setVar("query", jdbcQuery);
                ee.setVar("property", queryProperty.getJdbcProperty());
                queryProperty.getQueryAccess().getQueryBuilder().autoApply(ee);
            }
        }



        //bug fix: 如果启用了distinct,出现在orderBy中的列，必须加入到select
        if (jdbcQuery.getSelect().isDistinct()) {
            for (DbQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                if (!jdbcQuery.containSelect(order.getSelectColumn())) {
                    jdbcQuery.getSelect().select(order.getSelectColumn());
                }
            }
        }


        if (queryRequest.hasGroupBy()) {
            //当有分组时，我们直接在jdbcQuery加入groupBy
            int idx=0;
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (column instanceof CalculatedJdbcColumn c) {
                    // hasAggregate=true: 表达式本身已包含聚合函数（如 SUM(totalAmount)），跳过
                    if (!c.hasAggregate()) {
                        // aggregationType!=null: 推断的聚合类型（如 totalAmount+2 推断为 SUM），用聚合函数包裹
                        // aggregationType==null: 无聚合，加入 groupBy
                        DbAggregation agg = c.getAggregationType() != null
                                ? DbAggregation.valueOf(c.getAggregationType())
                                : column.getAggregation();
                        AggregationJdbcColumn aggColumn = buildAggColumn1(column.getQueryObject(), column.getDeclare(), column, agg);
                        if (c.getAggregationType() == null) {
                            jdbcQuery.addGroupBy(aggColumn, column);
                        }
                        jdbcQuery.getSelect().getColumns().set(idx, aggColumn);
                    }
                } else {
                    String declare = column.getDeclare(systemBundlesContext.getApplicationContext(), jdbcQueryModel.getAlias(column.getQueryObject()));
                    AggregationJdbcColumn aggColumn = buildAggColumn1(column.getQueryObject(), declare, column, column.getAggregation());
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

                    validate(orderRequestDef.getOrder());
                    DbColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(orderRequestDef.getField(), true);
                    jdbcQuery.addOrder(new DbQueryOrderColumnImpl(jdbcColumn, orderRequestDef.getOrder(), orderRequestDef.isNullLast(), orderRequestDef.isNullFirst()));

                }
            }

            //加排序
            if (jdbcQueryModel.getOrders() != null && !jdbcQueryModel.getOrders().isEmpty()) {
                jdbcQuery.addOrders(jdbcQueryModel.getOrders());
                for (DbQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                    if (jdbcQuery.containSelect(order.getSelectColumn())) {
                        continue;
                    }
                    jdbcQuery.join(order.getSelectColumn().getQueryObject());
                }
            }
        }


        // 4.生成明细查询语句
        this.jdbcQuery = jdbcQuery;

        SimpleSqlJdbcQueryVisitor v = new SimpleSqlJdbcQueryVisitor(systemBundlesContext.getApplicationContext(), jdbcQueryModel, queryRequest);


        jdbcQuery.accept(v);
        values = v.getValues();
        this.innerSql = v.getSql();
        this.innerSqlWithoutOrder = v.getSqlWithoutOrder();
        this.sql = this.innerSql;
        if (queryRequest.hasGroupBy()) {
            // 基于明细查询语句，生成聚合查询语句
            this.aggSql = buildAggSql(systemBundlesContext, null, null, false, true);
        } else {
            // 基于明细查询语句，生成聚合查询语句
            this.aggSql = buildAggSql(systemBundlesContext, null, null, false, false);
        }

        if (log.isDebugEnabled()) {
            log.debug("生成查询SQL");
            log.debug(this.sql);
            log.debug("聚合SQL");
            log.debug(this.aggSql);
            log.debug("参数");
            log.debug(values == null ? "无" : values.toString());
        }

    }

    private String buildGroupBy(SystemBundlesContext systemBundlesContext, DbQueryRequestDef queryRequest) {
        String groupBySql = buildAggSql(systemBundlesContext, queryRequest.getGroupBy().stream().collect(Collectors.toMap(GroupRequestDef::getField, e -> e)), queryRequest, true, false);
        return groupBySql;
    }

    private AggregationJdbcColumn buildAggColumn(QueryObject sqlQueryObject, DbColumn column, DbAggregation agg) {
        String declare = sqlQueryObject.getAlias() + "." + column.getAlias();
        return buildAggColumn1(sqlQueryObject, declare, column, agg);
    }

    private AggregationJdbcColumn buildAggColumn1(QueryObject sqlQueryObject, String declare, DbColumn column, DbAggregation agg) {

        AggregationJdbcColumn aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
        if (agg == null) {
            agg = DbAggregation.NONE;
        }
        switch (agg) {
            case GROUP_CONCAT:
                aggColumn.setDeclare("GROUP_CONCAT(" + declare + " SEPARATOR ',')");
                aggColumn.setGroupByName(null);
                break;
            case MAX:
                aggColumn.setDeclare("MAX(" + declare + ")");
                aggColumn.setGroupByName(null);
                break;
            case MIN:
                aggColumn.setDeclare("MIN(" + declare + ")");
                aggColumn.setGroupByName(null);
                break;
            case PK:
                aggColumn.setDeclare("MAX(" + declare + ")");
                break;
            case COUNT:
                aggColumn.setDeclare("COUNT(*)");
                aggColumn.setGroupByName(null);
                break;
            case SUM:
                aggColumn.setDeclare("SUM(" + declare + ")");
                aggColumn.setGroupByName(null);
                break;
            case AVG:
                aggColumn.setDeclare("AVG(" + declare + ")");
                aggColumn.setGroupByName(null);
                break;
            case CUSTOM:
                String aggregationFormula = column.getAggregationFormula();
                RX.hasText(aggregationFormula, "传了groupBy为CUSTOM , 但没有定义aggregationFormula，列:" + column.getName());
                aggColumn.setDeclare(aggregationFormula);
                aggColumn.setGroupByName(null);
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
        JdbcQuery aggJdbcQuery = new JdbcQuery();
        // 使用不含 ORDER BY 的SQL作为子查询，避免生成无意义的排序语句
        SqlQueryObject sqlQueryObject = new SqlQueryObject(this.innerSqlWithoutOrder, "tx");
        List<DbColumn> aggColumns = new ArrayList<>();
        for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
//            jdbcQueryModel.get
            AggregationJdbcColumn aggColumn = null;
            DbAggregation c = column.getAggregation();

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
                    aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), "avg" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + ")", column.getType());
                    break;
                case SUM:
                    String declare = "";
                    switch (column.getSqlColumn().getJdbcType()) {
                        case Types.DOUBLE:
                        case Types.FLOAT:
                            //需要格式化,不再格式化,会引起外部聚合时的问题,这个格式化交给前端处理好了
//                                declare = "format(sum" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + "),2)";
//                                break;
                        default:
                            declare = "sum" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + ")";
                    }
                    aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                    break;
                case COUNT:
                    if (countToSum) {
                        //解决前端聚合维度或属性时的BUG
                        declare = "sum" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + ")";
                        aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                    } else {
                        aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), "count" + "(*)");
                    }

                    break;
                case MAX:
                    declare = "max" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + ")";
                    aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                    break;
                case MIN:
                    declare = "min" + "(" + jdbcQueryModel.getAlias(sqlQueryObject) + "." + column.getAlias() + ")";
                    aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                    break;
                case NONE:
                    //意思是不做聚合
                    declare = "null";
                    aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            aggColumns.add(aggColumn);

        }
        aggColumns.add(new AggregationJdbcColumn(sqlQueryObject, "total", "count(*)"));
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
//        JdbcColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(sliceDef.getName(), true);
//        if (jdbcColumn == null) {
//            throw RX.throwAUserTip(String.format("未能找到列[%s]，切片%s", sliceDef.getName(), sliceDef));
//        }
//
//        if (jdbcColumn.getQueryObject() != null && (jdbcQuery.getFrom().getFromObject() != jdbcColumn.getQueryObject())) {
//            //需要加入left join
//            jdbcQuery.join(jdbcColumn.getQueryObject());
//        }
////        jdbcColumn.gets
//        String alias = jdbcQueryModel.getAlias(jdbcColumn.getQueryObject());
//
//        if (jdbcColumn.isDimension()) {
//            JdbcModelParentChildDimensionImpl pp = jdbcColumn.getDecorate(JdbcDimensionColumn.class).getJdbcDimension().getDecorate(JdbcModelParentChildDimensionImpl.class);
//            if (pp != null) {
//                //这是一个parentChild维~条件要重写，转成closure表
//                jdbcQuery.join(pp.getClosureQueryObject(), pp.getForeignKey());
//                alias = jdbcQueryModel.getAlias(pp.getClosureQueryObject());
//                //查询列换成closure表的parentId
//                jdbcColumn = pp.getParentKeyJdbcColumn();
//            }
//        }
//
//        if (sliceDef._hasChildren()) {
//            //有子项~
//            JdbcQuery.JdbcGroupCond gc = jdbcQuery.getWhere().newGroupCond();
//
//
//        } else {
//            sqlFormulaService.buildAndAddToJdbcCond(jdbcQuery.getWhere(), sliceDef.getType(), jdbcColumn, alias, sliceDef.getValue());
//        }

        buildSlice(jdbcQueryModel, jdbcQuery, jdbcQuery.getWhere(), sliceDef, 0);

    }

    private void buildSlice(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef, int level) {
        buildSlice(jdbcQueryModel, jdbcQuery, listCond, sliceDef, 0, level);
    }

    private void buildSlice(JdbcQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef, int idx, int level) {
        if (sliceDef._hasChildren()) {
            //有子项~
            int i = 0;
            //第一层不加,全部用and
            JdbcQuery.JdbcGroupCond gc = jdbcQuery.getWhere().newGroupCond(level > 0 ? JdbcLink.getLinkStr(sliceDef.getLink()) : "");
            for (CondRequestDef child : sliceDef.getChildren()) {
                buildSlice(jdbcQueryModel, jdbcQuery, gc, child, i, level + 1);
                i++;
            }

            listCond.addCond(gc);
        } else {
            DbColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(sliceDef.getField(), false, true);

            // 如果在模型中找不到，尝试从计算字段中查找
            if (jdbcColumn == null) {
                jdbcColumn = findCalculatedColumn(sliceDef.getField());
            }

            if (jdbcColumn == null) {
                throw RX.throwAUserTip(DatasetMessages.queryColumnNotfound(sliceDef.getField(), jdbcQueryModel.findDimension(sliceDef.getField())));
            }

            // 计算字段直接使用 SQL 表达式，不需要 JOIN 和特殊处理
            if (jdbcColumn.isCalculatedField()) {
                sqlFormulaService.buildAndAddToJdbcCond(listCond, sliceDef.getOp(), jdbcColumn, null, sliceDef.getValue(), sliceDef.getLink());
                return;
            }

            if (jdbcColumn.getQueryObject() != null && !(jdbcQuery.getFrom().getFromObject().isRootEqual(jdbcColumn.getQueryObject()))) {
                //需要加入left join
                jdbcQuery.join(jdbcColumn.getQueryObject());
            }
            String alias = jdbcQueryModel.getAlias(jdbcColumn.getQueryObject());

            if (jdbcColumn.isDimension()) {
                DbModelParentChildDimensionImpl pp = jdbcColumn.getDecorate(DbDimensionColumn.class).getDimension().getDecorate(DbModelParentChildDimensionImpl.class);
                if (pp != null) {
                    //这是一个parentChild维~条件要重写，转成closure表
                    jdbcQuery.join(pp.getClosureQueryObject(), pp.getForeignKey());
                    alias = jdbcQueryModel.getAlias(pp.getClosureQueryObject());
                    //查询列换成closure表的parentId
                    jdbcColumn = pp.getParentKeyJdbcColumn();
                }
            }
            if (jdbcColumn.isProperty() && jdbcColumn.getDecorate(DbPropertyColumn.class).getProperty().isBit()) {
                //是位图列,重写为bitIn
                sliceDef.setOp(CondType.BIT_IN.getCode());
            }
            sqlFormulaService.buildAndAddToJdbcCond(listCond, sliceDef.getOp(), jdbcColumn, alias, sliceDef.getValue(), sliceDef.getLink());
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
            // 已预处理，直接使用结果
            if (log.isDebugEnabled()) {
                log.debug("Using preprocessed inline expressions from context, skipping redundant parsing");
            }
            // columns 和 calculatedFields 已经在 InlineExpressionPreprocessStep 中更新到 queryRequest
            return;
        }

        // 未预处理，执行原有逻辑
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
            for (CalculatedJdbcColumn column : calculatedColumns) {
                log.debug("  {} = {}", column.getName(), column.getDeclare());
            }
        }
    }

    /**
     * 根据名称查找计算字段
     *
     * @param columnName 列名
     * @return 计算字段列，如果不存在返回 null
     */
    private CalculatedJdbcColumn findCalculatedColumn(String columnName) {
        if (calculatedColumns == null || calculatedColumns.isEmpty()) {
            return null;
        }
        for (CalculatedJdbcColumn column : calculatedColumns) {
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
    public List<CalculatedJdbcColumn> getCalculatedColumns() {
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

                // 查找匹配的 SELECT 列
                DbColumn selectColumn = columnNameMap.get(fieldName);

                if (selectColumn != null) {
                    // 字段在 SELECT 中，添加排序
                    validate(orderRequestDef.getOrder());
                    jdbcQuery.addOrder(new DbQueryOrderColumnImpl(
                            selectColumn,
                            orderRequestDef.getOrder(),
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
                } else {
                    // 字段不在 SELECT 中，记录并跳过
                    String displayName = fieldName != null ? fieldName : fieldAlias;
                    if (displayName != null && !skippedFields.contains(displayName)) {
                        skippedFields.add(displayName);
                    }
                }
            }
        }

        // 记录警告日志
        if (!skippedFields.isEmpty()) {
            log.warn("GroupBy 模式下忽略了不在 SELECT 中的 orderBy 字段: {}", skippedFields);
        }
    }
}
