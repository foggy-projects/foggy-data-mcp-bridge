package com.foggyframework.dataset.jdbc.model.engine;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.DateTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.mongo.MongoQueryModel;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.impl.utils.SqlQueryObject;
import com.foggyframework.dataset.jdbc.model.spi.JdbcAggregation;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryProperty;
import com.foggyframework.dataset.jdbc.model.spi.support.AggregationJdbcColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.core.tuple.Tuple3;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Data
public class MongoModelQueryEngine {
    MongoQueryModel jdbcQueryModel;

    JdbcQuery jdbcQuery;

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

    public MongoModelQueryEngine(MongoQueryModel jdbcQueryModel) {
        this.jdbcQueryModel = jdbcQueryModel;
    }

    //    private boolean containSelect(JdbcColumn jdbcColumn) {
//        for (JdbcColumn column : select.columns) {
//            if (StringUtils.equals(column.getAlias(), jdbcColumn.getAlias())) {
//                return true;
//            }
//        }
//        return false;
//    }
    public void analysisQueryRequest(SystemBundlesContext systemBundlesContext, JdbcQueryRequestDef queryRequest) {
        RX.notNull(queryRequest, "查询请求不得为空");

        this.jdbcQuery = new JdbcQuery();
        jdbcQuery.setQueryRequest(queryRequest);
        jdbcQuery.from(jdbcQueryModel.getQueryObject());

        //1.加入需要查询的列
        List<JdbcColumn> selectColumns = null;
        if (queryRequest.getColumns() == null || queryRequest.getColumns().isEmpty()) {
            log.debug("查询请求中未定义列，我们直接从查询模型中取相关的列");

            selectColumns = jdbcQueryModel.getSelectColumns(true);

        } else {
            //前端传了查询的列名
            selectColumns = new ArrayList<>(queryRequest.getColumns().size());
            for (String columnName : queryRequest.getColumns()) {
                selectColumns.add(jdbcQueryModel.findJdbcColumnForSelectByName(columnName, true).getSelectColumn());
            }
        }
        jdbcQuery.select(selectColumns);

//        //id列是必须返回的
//        if (jdbcQueryModel.getJdbcModel().getIdColumn() != null) {
//            JdbcColumn jdbcColumn = jdbcQueryModel.findJdbcColumn(jdbcQueryModel.getJdbcModel().getIdColumn());
//            if (jdbcColumn != null) {
//                if (!jdbcQuery.containSelect(jdbcColumn)) {
//                    jdbcQuery.getSelect().select(jdbcColumn);
//                }
//            }
//
//        }

        // 2.加入切片条件,注意，切片暂时不考虑or
        if (queryRequest.getSlice() != null) {
            for (SliceRequestDef sliceDef : queryRequest.getSlice()) {
                buildSlice(jdbcQueryModel, jdbcQuery, sliceDef);
            }
        }


        // 3.加权限语句
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

                jdbcQuery.addOrder(new JdbcQueryOrderColumnImpl(jdbcQueryModel.findJdbcColumn(orderRequestDef.getField()), orderRequestDef.getOrder(), orderRequestDef.isNullLast(), orderRequestDef.isNullFirst()));

            }
        }else{
            //加默认排序
            if (jdbcQueryModel.getOrders() != null && !jdbcQueryModel.getOrders().isEmpty()) {
                jdbcQuery.addOrders(jdbcQueryModel.getOrders());
            }
        }



        //start和limit
    }

    public Tuple3<Criteria, ProjectionOperation, Sort> buildOptions() {
        //构建project

//        List<String > ll = jdbcQuery.getSelect().getColumns().stream().map(column-> column.getSqlColumnName()).collect(Collectors.toList());

        ProjectionOperation project = Aggregation.project();
        for (JdbcColumn column : jdbcQuery.getSelect().getColumns()) {
            project = project.and(column.getSqlColumnName()).as(column.getAlias());
//            project = project.and(column.getAlias()).as("$"+column.getSqlColumnName());
        }

        //构建match
        Criteria criteria = new Criteria();
        for (JdbcQuery.JdbcCond cond : jdbcQuery.getWhere().getConds()) {
            JdbcQuery.QueryTypeValueCond ql = (JdbcQuery.QueryTypeValueCond) cond;
            JdbcColumn column = jdbcQueryModel.findJdbcColumn(ql.getName());
            String name = column.getSqlColumnName();
            if (StringUtils.equals("=", ql.getQueryType())) {
                criteria.and(name).is(ql.getValue());
            } else if (StringUtils.equals("<>", ql.getQueryType())
                    || StringUtils.equals("!=", ql.getQueryType())
            ) {
                criteria.and(name).ne(ql.getValue());
            } else if (StringUtils.equals("in", ql.getQueryType())) {
                if (ql.getValue() instanceof List) {
                    if (((List) ql.getValue()).isEmpty()) {
                        continue;
                    }
                    criteria.and(name).in((List) ql.getValue());
                } else {
                    throw RX.throwAUserTip(DatasetMessages.mongoInArrayRequired());
                }
            } else if (StringUtils.equals("not in", ql.getQueryType())) {
                if (ql.getValue() instanceof List) {
                    if (((List) ql.getValue()).isEmpty()) {
                        continue;
                    }
                    criteria.and(name).nin((List) ql.getValue());
                } else {
                    throw RX.throwAUserTip(DatasetMessages.mongoInArrayRequired());
                }
            } else if (StringUtils.equals("like", ql.getQueryType())) {
//                criteria.and(name).regex(".*" + ql.getValue() + ".*");
                criteria.and(name).regex(ql.getValue() + "");
            } else if (StringUtils.equals("left_like", ql.getQueryType())) {
                criteria.and(name).regex(Pattern.compile("^.*" + ql.getValue()));
            } else if (StringUtils.equals("right_like", ql.getQueryType())) {
                criteria.and(name).regex(Pattern.compile("^" + ql.getValue() + ".*"));
            } else if (StringUtils.equals("[)", ql.getQueryType())||
                    StringUtils.equals("[]", ql.getQueryType())) {

                boolean lte = ql.getQueryType().endsWith("]");

                if (ql.getValue() instanceof List) {
                    Object v1 = ((List<?>) ql.getValue()).get(0);
                    Object v2 = ((List<?>) ql.getValue()).get(1);
                    if (StringUtils.equals(column.getType(), "DATETIME")) {
                        v1 = DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(v1);
                        v2 = DateTransFormatter.DATE_TRANSFORMATTERINSTANCE.format(v2);
                    }
                    Criteria btc = null;

                    if (v1 != null) {
                        btc = criteria.and(name).gte(v1);
                    }
                    if (v2 != null) {
                        if (btc == null) {
                            if(lte){
                                criteria.and(name).lt(v2);
                            }else{
                                criteria.and(name).lte(v2);
                            }
                        } else {
                            if(lte){
                                btc.lte(v2);
                            }else{
                                btc.lt(v2);
                            }
                        }
                    }
                }
            } else {
                throw RX.throwAUserTip(DatasetMessages.mongoQuerytypeUnsupported(ql.getQueryType()));
            }
        }
        //排序
        Sort sort = null;
        List<Sort.Order> orders = null;
        if (jdbcQuery.getOrder() != null) {
            orders = new ArrayList<>(jdbcQuery.getOrder().size());
            for (JdbcQueryOrderColumnImpl order : jdbcQuery.getOrder().getOrders()) {
                if (StringUtils.equalsIgnoreCase("desc", order.getOrder())) {
                    orders.add(new Sort.Order(Sort.Direction.DESC, order.getSelectColumn().getName()));
                } else {
                    orders.add(new Sort.Order(Sort.Direction.ASC, order.getSelectColumn().getName()));
                }
            }
            // 检查是否已包含 _id 列，如果没有则添加，确保分页结果稳定
            boolean hasIdColumn = orders.stream()
                    .anyMatch(o -> "_id".equals(o.getProperty()));
            if (!hasIdColumn) {
                orders.add(new Sort.Order(Sort.Direction.ASC, "_id"));
            }
            sort = Sort.by(orders);
        }


        return new Tuple3<Criteria, ProjectionOperation, Sort>(criteria, project, sort);
    }

    public GroupOperation buildGroupOperation(SystemBundlesContext systemBundlesContext, Map<String, GroupRequestDef> groupByMap, JdbcQueryRequestDef queryRequest) {
//        JdbcQuery aggJdbcQuery = new JdbcQuery();
        GroupOperation groupOperation = Aggregation.group();

        SqlQueryObject sqlQueryObject = new SqlQueryObject(this.sql, "tx");
        List<JdbcColumn> aggColumns = new ArrayList<>();
        for (JdbcColumn column : jdbcQuery.getSelect().getColumns()) {
            AggregationJdbcColumn aggColumn = null;
            JdbcAggregation c = column.getAggregation();
            if (c == null) {
                if (groupByMap != null) {
                    GroupRequestDef def = groupByMap.get(column.getName());
                    if (def != null) {
                        groupOperation = groupOperation.addToSet(column.getName()).as(column.getAlias());
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
                        groupOperation = groupOperation.avg(column.getName()).as(column.getAlias());
                        break;
                    case SUM:
                        groupOperation = groupOperation.sum(column.getName()).as(column.getAlias());
                        break;
                    case COUNT:
                        groupOperation = groupOperation.count().as(column.getAlias());
                        break;
                    case MAX:
                        groupOperation = groupOperation.max(column.getName()).as(column.getAlias());
                        break;
                    case MIN:
                        groupOperation = groupOperation.min(column.getName()).as(column.getAlias());
                        break;
                    case NONE:
                        //意思是不做聚合
//                        declare = "null";
//                        aggColumn = new AggregationJdbcColumn(sqlQueryObject, column.getAlias(), declare, column.getType());
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
            if (aggColumn != null) {
                aggColumns.add(aggColumn);
            }

        }
        groupOperation = groupOperation.count().as("total");
        return groupOperation;
    }


    private void buildSlice(MongoQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, SliceRequestDef sliceDef) {

        buildSlice(jdbcQueryModel, jdbcQuery, jdbcQuery.getWhere(), sliceDef);

    }

    private void buildSlice(MongoQueryModel jdbcQueryModel, JdbcQuery jdbcQuery, JdbcQuery.JdbcListCond listCond, CondRequestDef sliceDef) {
        if (sliceDef._hasChildren()) {
            //有子项~
            JdbcQuery.JdbcGroupCond gc = jdbcQuery.getWhere().newGroupCond("");
            for (CondRequestDef child : sliceDef.getChildren()) {
                buildSlice(jdbcQueryModel, jdbcQuery, gc, child);
            }

            listCond.addCond(gc);
        } else {
            JdbcColumn jdbcColumn = jdbcQueryModel.findJdbcColumnForCond(sliceDef.getField(), true);
            if (jdbcColumn == null) {
                throw RX.throwAUserTip(DatasetMessages.queryColumnNotfound(sliceDef.getField(), jdbcQueryModel.findDimension(sliceDef.getField())));
            }
            jdbcQuery.andQueryTypeValueCond(jdbcColumn.getName(), sliceDef.getOp(), sliceDef.getValue());

        }

    }
}
