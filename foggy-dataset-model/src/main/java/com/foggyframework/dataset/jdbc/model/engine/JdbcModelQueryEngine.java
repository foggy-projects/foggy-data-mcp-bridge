package com.foggyframework.dataset.jdbc.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.common.query.CondType;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.jdbc.model.engine.expression.InlineExpressionParser;
import com.foggyframework.dataset.jdbc.model.engine.formula.JdbcLink;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.engine.query.SimpleSqlJdbcQueryVisitor;
import com.foggyframework.dataset.jdbc.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcModelParentChildDimensionImpl;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.impl.utils.SqlQueryObject;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.support.AggregationJdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.support.CalculatedJdbcColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Data
public class JdbcModelQueryEngine {
    JdbcQueryModel jdbcQueryModel;

    JdbcQuery jdbcQuery;

    SqlFormulaService sqlFormulaService;

    /**
     * 计算字段服务
     */
    CalculatedFieldService calculatedFieldService;

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

    public void analysisQueryRequest(SystemBundlesContext systemBundlesContext, JdbcQueryRequestDef queryRequest) {
        RX.notNull(queryRequest, "查询请求不得为空");

        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.setQueryRequest(queryRequest);
        jdbcQuery.from(jdbcQueryModel.getQueryObject());

        //补上必要的模型
        if (jdbcQueryModel.getJdbcModelList().size() > 1) {
            for (int i = 1; i < jdbcQueryModel.getJdbcModelList().size(); i++) {
                JdbcModel jm = jdbcQueryModel.getJdbcModelList().get(i);
                JdbcQueryModelImpl.JdbcModelDx dx = jm.getDecorate(JdbcQueryModelImpl.JdbcModelDx.class);

                if (dx.getOnBuilder() != null) {
                    jdbcQuery.preJoin(dx.getQueryObject(), dx.getOnBuilder(), dx.getJoinType());
                } else {
                    jdbcQuery.preJoin(dx.getQueryObject(), dx.getForeignKey());
                }
            }
        }

        // 0. 预处理 columns 中的内联表达式，转换为 calculatedFields
        preprocessInlineExpressions(queryRequest);

        // 0.1 处理动态计算字段
        processCalculatedFields(systemBundlesContext, queryRequest);

        //1.加入需要查询的列
        List<JdbcColumn> selectColumns = null;
        if (queryRequest.getColumns() == null || queryRequest.getColumns().isEmpty()) {
            log.debug("查询请求中未定义列，我们直接从查询模型中取相关的列");

            selectColumns = jdbcQueryModel.getSelectColumns(true);

        } else {
            //前端传了查询的列名
            selectColumns = new ArrayList<>(queryRequest.getColumns().size());
            for (String columnName : queryRequest.getColumns()) {
                // 先查找计算字段
                JdbcColumn calcColumn = findCalculatedColumn(columnName);
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
                JdbcQueryColumn qc = jdbcQueryModel.findJdbcColumnForSelectByName(columnName, false);
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
        for (JdbcQueryDimension queryDimension : jdbcQueryModel.getQueryDimensions()) {
            if (queryDimension.getQueryAccess() != null && queryDimension.getQueryAccess().getQueryBuilder() != null) {
                ExpEvaluator ee = DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
                ee.setVar("query", jdbcQuery);
                ee.setVar("dim", queryDimension.getDimension());
                ee.setVar("dimension", queryDimension.getDimension());
                queryDimension.getQueryAccess().getQueryBuilder().autoApply(ee);
            }
        }
        for (JdbcQueryProperty queryProperty : jdbcQueryModel.getQueryProperties()) {
            if (queryProperty.getQueryAccess() != null && queryProperty.getQueryAccess().getQueryBuilder() != null) {
                ExpEvaluator ee = DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext());
                ee.setVar("query", jdbcQuery);
                ee.setVar("property", queryProperty.getJdbcProperty());
                queryProperty.getQueryAccess().getQueryBuilder().autoApply(ee);
            }
        }

        if (queryRequest.getOrderBy() != null) {
            for (OrderRequestDef orderRequestDef : queryRequest.getOrderBy()) {

                validate(orderRequestDef.getOrder());
                JdbcColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(orderRequestDef.getField(),true);
                jdbcQuery.addOrder(new JdbcQueryOrderColumnImpl(jdbcColumn, orderRequestDef.getOrder(), orderRequestDef.isNullLast(), orderRequestDef.isNullFirst()));

            }
        }

        //加排序
        if (jdbcQueryModel.getOrders() != null && !jdbcQueryModel.getOrders().isEmpty()) {
            jdbcQuery.addOrders(jdbcQueryModel.getOrders());
            for (JdbcQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                if (jdbcQuery.containSelect(order.getSelectColumn())) {
                    continue;
                }
                jdbcQuery.join(order.getSelectColumn().getQueryObject());
            }
        }

        //bug fix: 如果启用了distinct,出现在orderBy中的列，必须加入到select
        if (jdbcQuery.getSelect().isDistinct()) {
            for (JdbcQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                if (!jdbcQuery.containSelect(order.getSelectColumn())) {
                    jdbcQuery.getSelect().select(order.getSelectColumn());
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
            this.sql = buildGroupBy(systemBundlesContext, queryRequest);
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

    private String buildGroupBy(SystemBundlesContext systemBundlesContext, JdbcQueryRequestDef queryRequest) {
        String groupBySql = buildAggSql(systemBundlesContext, queryRequest.getGroupBy().stream().collect(Collectors.toMap(GroupRequestDef::getField, e -> e)), queryRequest, true, false);
        return groupBySql;
    }

    private AggregationJdbcColumn buildAggColumn(SqlQueryObject sqlQueryObject, JdbcColumn column, GroupRequestDef def) {
        String declare = sqlQueryObject.getAlias() + "." + column.getAlias();
        AggregationJdbcColumn aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
        if (StringUtils.equals("GROUP_CONCAT", def.getAgg())) {
            aggColumn.setDeclare("GROUP_CONCAT(" + declare + " SEPARATOR ',')");
            aggColumn.setGroupByName(null);
        } else if (StringUtils.equals("MAX", def.getAgg())) {
            aggColumn.setDeclare("MAX(" + declare + ")");
            aggColumn.setGroupByName(null);
        } else if (StringUtils.equals("PK", def.getAgg())) {
            aggColumn.setDeclare("MAX(" + declare + ")");
        } else if (column.getType() == JdbcColumnType.DATETIME) {
            // 使用方言提供的日期格式化函数，支持多数据库
            aggColumn.setDeclare(jdbcQueryModel.getDialect().buildDateFormatFunction(declare));
        } else if (StringUtils.equals("CUSTOM", def.getAgg())) {
            String aggregationFormula = column.getAggregationFormula();
            RX.hasText(aggregationFormula, "传了groupBy为CUSTOM , 但没有定义aggregationFormula，列:" + column.getName());
            aggColumn.setDeclare(aggregationFormula);
            //不用放到group by了
            aggColumn.setGroupByName(null);
        } else if (StringUtils.equals("COUNT", def.getAgg())) {
            aggColumn.setDeclare("COUNT(*)");
            aggColumn.setGroupByName(null);
        } else if (StringUtils.equals("SUM", def.getAgg())) {
            aggColumn.setDeclare("SUM(" + declare + ")");
            aggColumn.setGroupByName(null);
        }

        return aggColumn;

    }

    private String buildAggSql(SystemBundlesContext systemBundlesContext, Map<String, GroupRequestDef> groupByMap, JdbcQueryRequestDef queryRequest, boolean addOrder, boolean countToSum) {
        JdbcQuery aggJdbcQuery = new JdbcQuery();
        // 使用不含 ORDER BY 的SQL作为子查询，避免生成无意义的排序语句
        SqlQueryObject sqlQueryObject = new SqlQueryObject(this.innerSqlWithoutOrder, "tx");
        List<JdbcColumn> aggColumns = new ArrayList<>();
        for (JdbcColumn column : jdbcQuery.getSelect().getColumns()) {
//            jdbcQueryModel.get
            AggregationJdbcColumn aggColumn = null;
            JdbcAggregation c = column.getAggregation();
            if (c == null) {
                if (groupByMap != null) {
                    GroupRequestDef def = groupByMap.get(column.getName());
                    if (def == null) {
                        //修复esSettledTeam.esSettledTeamCaption提交为esSettledTeamCaption的问题
                        def = groupByMap.get(column.getAlias());
                    }
                    if (def != null) {
                        aggColumn = buildAggColumn(sqlQueryObject, column, def);
                        aggJdbcQuery.addGroupBy(aggColumn, column);
                    }
                }
            } else {
                if (groupByMap != null) {
                    GroupRequestDef def = groupByMap.get(column.getName());
                    if (def != null && StringUtils.isNotEmpty(def.getAgg())) {
                        c = JdbcAggregation.valueOf(def.getAgg());
                    }
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
            }
            if (aggColumn != null) {
                aggColumns.add(aggColumn);
            }

        }
        aggColumns.add(new AggregationJdbcColumn(sqlQueryObject, "total", "count(*)"));
        sqlQueryObject.setColumns(aggColumns);

        aggJdbcQuery.from(sqlQueryObject);
        aggJdbcQuery.select(aggColumns);

        if (addOrder && this.jdbcQuery.getOrder() != null) {
            //group by之后，需要重新搞下排序
            for (JdbcQueryOrderColumnImpl orderRequestDef : jdbcQuery.getOrder().getOrders()) {

                for (JdbcColumn aggColumn : aggColumns) {
                    //需要检查传入的列是否在聚合查询中
                    if (StringUtils.equals(aggColumn.getAlias(), orderRequestDef.getSelectColumn().getAlias())) {
                        aggJdbcQuery.addOrder(new JdbcQueryOrderColumnImpl(aggColumn, orderRequestDef.getOrder(), false, false));
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
            JdbcColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(sliceDef.getField(), false, true);

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
                JdbcModelParentChildDimensionImpl pp = jdbcColumn.getDecorate(JdbcDimensionColumn.class).getJdbcDimension().getDecorate(JdbcModelParentChildDimensionImpl.class);
                if (pp != null) {
                    //这是一个parentChild维~条件要重写，转成closure表
                    jdbcQuery.join(pp.getClosureQueryObject(), pp.getForeignKey());
                    alias = jdbcQueryModel.getAlias(pp.getClosureQueryObject());
                    //查询列换成closure表的parentId
                    jdbcColumn = pp.getParentKeyJdbcColumn();
                }
            }
            if (jdbcColumn.isProperty() && jdbcColumn.getDecorate(JdbcPropertyColumn.class).getJdbcProperty().isBit()) {
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
     *
     * @param queryRequest 查询请求
     */
    private void preprocessInlineExpressions(JdbcQueryRequestDef queryRequest) {
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
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param queryRequest         查询请求
     */
    private void processCalculatedFields(SystemBundlesContext systemBundlesContext, JdbcQueryRequestDef queryRequest) {
        if (queryRequest.getCalculatedFields() == null || queryRequest.getCalculatedFields().isEmpty()) {
            this.calculatedColumns = new ArrayList<>();
            return;
        }

        // 创建计算字段服务
        this.calculatedFieldService = new CalculatedFieldService(
                jdbcQueryModel,
                jdbcQueryModel.getDialect(),
                systemBundlesContext.getApplicationContext()
        );

        // 处理所有计算字段
        this.calculatedColumns = calculatedFieldService.processCalculatedFields(queryRequest.getCalculatedFields());

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
}
