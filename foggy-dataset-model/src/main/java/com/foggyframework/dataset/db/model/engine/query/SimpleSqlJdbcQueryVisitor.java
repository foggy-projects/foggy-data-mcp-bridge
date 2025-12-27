package com.foggyframework.dataset.db.model.engine.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.impl.query.DbQueryGroupColumnImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
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
    DbQueryRequestDef queryRequest;

    /**
     * 数据库方言，用于处理不同数据库的语法差异
     */
    private FDialect dialect;

    /**
     * ORDER BY 子句开始位置，用于生成不含排序的SQL
     */
    private int orderByStartIndex = -1;

    public SimpleSqlJdbcQueryVisitor(ApplicationContext appCtx, JdbcQueryModel jdbcQueryModel, DbQueryRequestDef queryRequest) {
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
        for (DbColumn column : select.getColumns()) {
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
                        .append(join.getJoinTypeString())
                        .append(join.getRight().getBody()).append(" ")
                        .append(getAlias(join.getRight())).append(" ")
                        .append(join.getRight().getForceIndex() == null ? "" : join.getRight().getForceIndex())
                        .append(" on ");

                // 优先使用预计算的 onCondition（方案 C：延迟计算后缓存）
                String onCondition = join.getOnCondition();
                if (onCondition != null) {
                    // 已有缓存，直接使用
                    sb.append(onCondition);
                } else if (join.getOnBuilder() != null) {
                    // 使用 OnBuilder 动态计算
                    ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
                    ee.setVar("jdbcFrom", from);
                    ee.setVar("join", join.getQueryObject());
                    ee.setVar("queryRequest", queryRequest);
                    ee.setVar("queryModel", jdbcQueryModel);

                    onCondition = String.valueOf(join.getOnBuilder().autoApply(ee));
                    join.setOnCondition(onCondition);  // 缓存
                    sb.append(onCondition).append("\t");
                } else {
                    // 从 left/foreignKey/right/primaryKey 构建 ON 条件并缓存
                    onCondition = getAlias(join.getLeft()) + "." + join.getForeignKey() + "="
                            + getAlias(join.getRight()) + "." + join.getRight().getPrimaryKey();
                    join.setOnCondition(onCondition);  // 缓存
                    sb.append(onCondition);
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
        for (DbQueryOrderColumnImpl orderOrder : order.getOrders()) {
            if (i != 0) {
                sb.append(",\t");
            }
            DbColumn column = orderOrder.getSelectColumn();
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
                // 使用方言转换参数值（特别是SQLite的Date类型）
                Object rawValue = ((JdbcQuery.ValueCond) cond).getValue();
                values.add(dialect.convertParameterValue(rawValue));
            } else if (cond instanceof JdbcQuery.ListValueCond) {
                sb.append(((JdbcQuery.ListValueCond) cond).getSqlFragment());
                // 对列表中的每个值应用方言转换
                List<Object> rawValues = ((JdbcQuery.ListValueCond) cond).getValue();
                for (Object rawValue : rawValues) {
                    values.add(dialect.convertParameterValue(rawValue));
                }
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
        List<DbQueryGroupColumnImpl> ll = group.getGroups().stream().filter(e -> e.getAggColumn().getGroupByName() != null).collect(Collectors.toList());
        if (ll.isEmpty()) {
            return;
        }

        sb.append("\t").append("group by ");

        int i = 0;
        for (DbQueryGroupColumnImpl orderOrder : ll) {
            if (i != 0) {
                sb.append(",\t");
            }
            AggregationDbColumn column = orderOrder.getAggColumn();
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
