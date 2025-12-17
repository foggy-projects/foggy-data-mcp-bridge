package com.foggyframework.dataset.jdbc.model.engine.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryGroupColumnImpl;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import com.foggyframework.dataset.jdbc.model.spi.support.AggregationJdbcColumn;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class SimpleSqlJdbcQueryVisitor implements JdbcQueryVisitor {

    ApplicationContext appCtx;

    StringBuilder sb = new StringBuilder();
    List<Object> values = new ArrayList<>();

    JdbcQueryModel jdbcQueryModel;
    JdbcQueryRequestDef queryRequest;

    /**
     * 数据库方言，用于处理不同数据库的语法差异
     */
    private FDialect dialect;

    /**
     * ORDER BY 子句开始位置，用于生成不含排序的SQL
     */
    private int orderByStartIndex = -1;

    public SimpleSqlJdbcQueryVisitor(ApplicationContext appCtx, JdbcQueryModel jdbcQueryModel, JdbcQueryRequestDef queryRequest) {
        this.appCtx = appCtx;
        this.jdbcQueryModel = jdbcQueryModel;
        this.queryRequest = queryRequest;
        // 从 jdbcQueryModel 获取方言
        this.dialect = jdbcQueryModel != null ? jdbcQueryModel.getDialect() : FDialect.MYSQL_DIALECT;
    }

    @Deprecated
    public SimpleSqlJdbcQueryVisitor() {
        // 默认使用 MySQL 方言
        this.dialect = FDialect.MYSQL_DIALECT;
    }

    @Override
    public void acceptSelect(JdbcQuery.JdbcSelect select) {
        RX.notNull(select, "select不得为空");
        sb.append("select\t");
        if (select.distinct) {
            sb.append(" distinct \t");
        }
        int i = 0;
        for (JdbcColumn column : select.getColumns()) {
            if (i != 0) {
                sb.append(",\t");
            }
            if (column.isCountColumn()) {
                // 使用 dialect.quoteIdentifier() 替代硬编码反引号
                sb.append("1 ").append(dialect.quoteIdentifier(column.getAlias()));
            } else {
                sb.append(column.getDeclare(appCtx, getAlias(column.getQueryObject())))
                  .append(" ").append(dialect.quoteIdentifier(column.getAlias()));
            }

            i++;
        }
        sb.append("\t");
    }

    private String getAlias(QueryObject queryObject) {
        if (queryObject == null) {
            return null;
        }
        return jdbcQueryModel == null ? queryObject.getAlias() : jdbcQueryModel.getAlias(queryObject);
    }

    @Override
    public void acceptFrom(JdbcQuery.JdbcFrom from) {

        sb.append("from ").append(from.getFromObject().getBody()).append(" ").append(getAlias(from.getFromObject())).append("\t");
        if (from.getJoins() != null) {

            for (JdbcQuery.JdbcFrom.JdbcJoin join : from.getJoins()) {
                sb
//                        .append(" left join ")

                        .append(join.getJoinTypeString())
                        .append(join.getRight().getBody()).append(" ")
                        .append(getAlias(join.getRight())).append(" ")
                        .append(join.getRight().getForceIndex() == null ? "" : join.getRight().getForceIndex())
                        .append(" on ");

                if (join.getOnBuilder() != null) {
                    ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
                    ee.setVar("jdbcFrom", from);
                    ee.setVar("join", join.getQueryObject());
                    ee.setVar("queryRequest", queryRequest);
                    ee.setVar("queryModel", jdbcQueryModel);

                    sb.append(join.getOnBuilder().autoApply(ee)).append("\t");
                } else {
                    sb.append(getAlias(join.getLeft())).append(".")
                            .append(join.getForeignKey()).append("=").append(getAlias(join.getRight()))
                            .append(".").append(join.getRight().getPrimaryKey());
                }

            }
        }

    }

    @Override
    public void acceptWhere(JdbcQuery.JdbcWhere where) {
        if (where.isEmpty()) {
            return;
        }
        sb.append(" where ");
        acceptListCond(where);
    }

    @Override
    public void acceptOrder(JdbcQuery.JdbcOrder order) {
        if (order == null || order.getOrders().isEmpty()) {
            return;
        }

        // 记录 ORDER BY 子句开始位置
        this.orderByStartIndex = sb.length();

        sb.append(" order by ");

        int i = 0;
        for (JdbcQueryOrderColumnImpl orderOrder : order.getOrders()) {
            if (i != 0) {
                sb.append(",\t");
            }
            JdbcColumn column = orderOrder.getSelectColumn();
            String cn = column.getDeclareOrder(appCtx, getAlias(column.getQueryObject()));

            // 使用 dialect.buildNullOrderClause() 处理 NULL 排序
            if (orderOrder.isNullLast() || orderOrder.isNullFirst()) {
                sb.append(dialect.buildNullOrderClause(cn, orderOrder.isNullFirst()));
            } else {
                sb.append(cn);
            }

            if (StringUtils.isNotEmpty(orderOrder.getOrder())) {
                sb.append(" ").append(orderOrder.getOrder());
            }
            i++;
        }
    }

    private void acceptListCond(JdbcQuery.JdbcListCond listCond) {
        if (!listCond.getConds().isEmpty() && StringUtils.equalsIgnoreCase(listCond.getConds().get(0).getLink(), "OR")) {
            sb.append(" 1=0 ");
        } else {
            sb.append(" 1=1 ");
        }

        for (JdbcQuery.JdbcCond cond : listCond.getConds()) {
            sb.append(" ").append(cond.getLink()).append(" \t");

            if (cond instanceof JdbcQuery.JdbcListCond) {
                if (StringUtils.isEmpty(cond.getLink())) {
                    sb.append("and ");
                }
                sb.append(" (");
                acceptListCond((JdbcQuery.JdbcListCond) cond);
                sb.append(")");
            } else if (cond instanceof JdbcQuery.ValueCond) {
                sb.append(((JdbcQuery.ValueCond) cond).getSqlFragment());
                values.add(((JdbcQuery.ValueCond) cond).getValue());
            } else if (cond instanceof JdbcQuery.ListValueCond) {
                sb.append(((JdbcQuery.ListValueCond) cond).getSqlFragment());
                values.addAll(((JdbcQuery.ListValueCond) cond).getValue());
            } else if (cond instanceof JdbcQuery.SqlFragmentCond) {
                sb.append(((JdbcQuery.SqlFragmentCond) cond).getSqlFragment());
            } else {
                throw new UnsupportedOperationException(cond.toString());
            }
        }

    }

    @Override
    public void acceptGroup(JdbcQuery.JdbcGroupBy group) {
        if (group == null || group.isEmpty()) {
            return;
        }
        List<JdbcQueryGroupColumnImpl> ll = group.getGroups().stream().filter(e -> e.getAggColumn().getGroupByName() != null).collect(Collectors.toList());
        if (ll.isEmpty()) {
            return;
        }

        sb.append("\t").append("group by ");

        int i = 0;
        for (JdbcQueryGroupColumnImpl orderOrder : ll) {
            if (i != 0) {
                sb.append(",\t");
            }
            AggregationJdbcColumn column = orderOrder.getAggColumn();
            sb.append(column.getGroupByName());
            i++;
        }
    }

    public String getSql() {
        return sb.toString();
    }

    /**
     * 获取不含 ORDER BY 子句的SQL
     * <p>用于聚合查询的子查询，避免生成无意义的排序语句</p>
     *
     * @return 不含排序的SQL
     */
    public String getSqlWithoutOrder() {
        if (orderByStartIndex < 0) {
            // 没有 ORDER BY，直接返回完整SQL
            return sb.toString();
        }
        return sb.substring(0, orderByStartIndex);
    }
}
