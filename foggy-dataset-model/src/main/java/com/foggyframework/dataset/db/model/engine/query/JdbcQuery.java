package com.foggyframework.dataset.db.model.engine.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.engine.formula.JdbcLink;
import com.foggyframework.dataset.db.model.engine.join.JoinEdge;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.query.DbQueryGroupColumnImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.DbQueryRequest;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import com.foggyframework.dataset.db.model.spi.support.SimpleQueryObject;
import com.foggyframework.dataset.db.model.spi.support.SimpleSqlJdbcColumn;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import jakarta.persistence.criteria.JoinType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class JdbcQuery {
    JdbcSelect select;
    JdbcFrom from;

    JdbcWhere where = new JdbcWhere();

    JdbcOrder order;

    JdbcGroupBy group;

    DbQueryRequest queryRequest;

    /**
     * JOIN 依赖图（可选）
     * <p>如果设置，join() 方法将使用图查询路径，否则使用传统的搜索逻辑</p>
     */
    JoinGraph joinGraph;

    public void accept(JdbcQueryVisitor visitor) {
        visitor.acceptSelect(select);
        visitor.acceptFrom(from);
        visitor.acceptWhere(where);
        visitor.acceptGroup(group);
        visitor.acceptOrder(order);
    }

    public JdbcQuery join(QueryObject queryObject) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.join(queryObject);
        return this;
    }

    public JdbcQuery join(QueryObject queryObject, String foreignKey) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.join(queryObject, foreignKey);
        return this;
    }

    public JdbcQuery join(QueryObject queryObject, FsscriptFunction onBuilder) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.join(queryObject, onBuilder, JoinType.LEFT);
        return this;
    }

    public JdbcQuery join(QueryObject queryObject, FsscriptFunction onBuilder, JoinType joinType) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.join(queryObject, onBuilder, joinType);
        return this;
    }

    public JdbcQuery customJoin(String tableName, String alias, String schema, FsscriptFunction onBuilder) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.customJoin(tableName, alias, schema, onBuilder);
        return this;
    }

    public JdbcQuery and(String sqlFragment, Object value) {
        getWhere().and(sqlFragment, value);
        return this;
    }

    public JdbcQuery andQueryTypeValueCond(String name, String queryType, Object value) {
        getWhere().andQueryTypeValueCond(name, queryType, value);
        return this;
    }

    public JdbcQuery and(String sqlFragment) {
        getWhere().and(sqlFragment);
        return this;
    }

    public JdbcQuery andList(String sqlFragment, List<Object> value) {
        getWhere().andList(sqlFragment, value);
        return this;
    }

    public JdbcQuery from(QueryObject queryObject) {
        return from(queryObject, null);
    }

    /**
     * 设置 FROM 子句并关联 JoinGraph
     *
     * @param queryObject 主表
     * @param joinGraph   JOIN 依赖图（可选，如果提供则 join() 使用图查询）
     * @return this
     */
    public JdbcQuery from(QueryObject queryObject, JoinGraph joinGraph) {
        if (from != null) {
            throw RX.throwAUserTip(DatasetMessages.queryFromDuplicate());
        }

        this.joinGraph = joinGraph;
        from = new JdbcFrom(queryObject);
        return this;
    }

    public JdbcQuery select(List<DbColumn> selectColumns) {
        RX.notNull(selectColumns, "参数selectColumns不得为空");
        for (DbColumn selectColumn : selectColumns) {
            select(selectColumn);
        }
        return this;
    }


    public JdbcQuery select(DbColumn selectColumn) {
        if (select == null) {
            select = new JdbcSelect();
        }

        select.select(selectColumn);


        return this;
    }

    public void select(String columName, String alias) {

        SqlColumn sqlColumn = getFrom().getFromObject().getSqlColumn(columName, true);

        select(new SimpleSqlJdbcColumn(getFrom().getFromObject(), sqlColumn, alias, sqlColumn.getName(), sqlColumn.getName()));
    }

    public void addOrders(List<DbQueryOrderColumnImpl> orders) {
        if (order == null) {
            order = new JdbcOrder(orders);
        } else {
            order.getOrders().addAll(orders);
        }

    }

    public void addOrder(DbQueryOrderColumnImpl column) {
        if (order == null) {
            order = new JdbcOrder(column);
        } else {
            order.getOrders().add(column);
        }

    }

    public boolean containSelect(DbColumn jdbcColumn) {
        for (DbColumn column : select.columns) {
            if (StringUtils.equals(column.getAlias(), jdbcColumn.getAlias())) {
                return true;
            }
        }
        return false;
    }

    public void addGroupBy(AggregationDbColumn aggColumn, DbColumn column) {
        if (group == null) {
            group = new JdbcGroupBy(1);
        }
        group.groups.add(new DbQueryGroupColumnImpl(aggColumn));
    }

    @Data
    public class JdbcSelect {

        List<DbColumn> columns;

        boolean distinct;

        public JdbcSelect select(DbColumn selectColumn) {

            RX.notNull(from, "调用select之前，需要先调用from");

            if (columns == null) {
                columns = new ArrayList<>();
            }
            for (DbColumn column : columns) {
                if (column == selectColumn) {
//                    throw RX.throwAUserTip("列[" + column + "]已经存在，请不要重复添加", "系统异常");
                    return this;
                }
                if (StringUtils.equals(column.getAlias(), selectColumn.getAlias())) {
                    throw RX.throwAUserTip(DatasetMessages.queryColumnAliasDuplicate(column.toString(), selectColumn.toString()), DatasetMessages.systemException());
                }
            }
            columns.add(selectColumn);

            // 计算字段没有 queryObject，不需要 join
            QueryObject selectQueryObject = selectColumn.getQueryObject();
            if (selectQueryObject != null && !from.getFromObject().isRootEqual(selectQueryObject)) {
                //需要加入left join
                from.join(selectQueryObject);
            }
            return this;
        }
    }

    @Data
    public class JdbcFrom {
        QueryObject fromObject;

        List<JdbcJoin> joins;

        public JdbcFrom(QueryObject fromObject) {
            this.fromObject = fromObject;
        }

        private void addJoin1(JdbcJoin join) {
            if (join.getQueryObject().getLinkQueryObject() != null) {
                join(join.getQueryObject().getLinkQueryObject(), (JoinType) null);
            }
            joins.add(join);

        }

        public JdbcFrom join(QueryObject queryObject) {
            return join(queryObject, (JoinType) null);
        }


        public JdbcFrom join(QueryObject queryObject, JoinType joinType) {
            if (queryObject.isRootEqual(this.fromObject)) {
                return this;
            }

            if (joins == null) {
                joins = new ArrayList<>();
            }

            // 检查是否已加入
            for (JdbcJoin join : joins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }

            // 必须使用 JoinGraph
            if (joinGraph == null) {
                throw RX.throwAUserTip("JoinGraph 未设置，无法执行 JOIN: " + queryObject.getAlias());
            }

            return joinWithGraph(queryObject, joinType);
        }

        /**
         * 使用 JoinGraph 进行 JOIN
         * <p>从图中查找路径，按拓扑顺序添加所有需要的边</p>
         */
        private JdbcFrom joinWithGraph(QueryObject queryObject, JoinType joinType) {
            // 收集需要到达的目标
            Set<QueryObject> targets = new HashSet<>();
            targets.add(queryObject);

            // 从图中获取路径（如果目标不在图中会抛出异常）
            List<JoinEdge> path = joinGraph.getPath(targets);

            // 按拓扑顺序添加所有边
            for (JoinEdge edge : path) {
                // 检查是否已存在
                boolean exists = false;
                for (JdbcJoin existingJoin : joins) {
                    if (existingJoin.contain(edge.getTo())) {
                        exists = true;
                        break;
                    }
                }
                if (exists) {
                    continue;
                }

                // 创建新的 JdbcJoin
                JdbcJoin jdbcJoin;
                if (edge.hasOnBuilder()) {
                    jdbcJoin = new JdbcJoin(edge.getTo(), edge.getOnBuilder(),
                            joinType != null ? joinType : edge.getJoinType());
                } else {
                    jdbcJoin = new JdbcJoin(edge.getFrom(), edge.getTo(), edge.getForeignKey(),
                            joinType != null ? joinType : edge.getJoinType());
                }

                // 如果边有缓存的 onCondition，也复制过来
                if (edge.hasOnCondition()) {
                    jdbcJoin.setOnCondition(edge.getOnCondition());
                }

                joins.add(jdbcJoin);
            }

            return this;
        }

        public JdbcFrom join(QueryObject queryObject, String foreignKey) {
            if (joins == null) {
                joins = new ArrayList<>();
            }
            for (JdbcJoin join : joins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }

            joins.add(new JdbcJoin(queryObject, foreignKey, null));

            return this;
        }

        public JdbcFrom join(QueryObject queryObject, FsscriptFunction onBuilder, JoinType joinType) {
            if (joins == null) {
                joins = new ArrayList<>();
            }
            for (JdbcJoin join : joins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }

            joins.add(new JdbcJoinOnBuilder(queryObject, onBuilder, joinType));
            return this;
        }

//        public JdbcFrom customJoin(Map mm) {
//            if (joins == null) {
//                joins = new ArrayList<>();
//            }
//            for (JdbcJoin join : joins) {
//                if (join.contain(queryObject)) {
//                    return this;
//                }
//            }
//
//            joins.add(new JdbcJoinOnBuilder(queryObject, onBuilder));
//            return this;
//        }

        public JdbcFrom customJoin(String tableName, String alias, String schema, FsscriptFunction onBuilder) {
            if (joins == null) {
                joins = new ArrayList<>();
            }
            for (JdbcJoin join : joins) {
                if (join.getQueryObject().getAlias().equals(alias)) {
                    //重复了~
                    return this;
                }
            }
            QueryObject queryObject = SimpleQueryObject.of(tableName, alias, schema);

            joins.add(new JdbcJoinOnBuilder(queryObject, onBuilder, JoinType.LEFT));
            return this;
        }

        @Data
        @NoArgsConstructor
        public class JdbcJoin {
            QueryObject queryObject;

            String foreignKey;

            JoinType joinType;

            /**
             * 显式的 LEFT 表（用于嵌套维度等场景）
             * 如果为 null，则默认使用 fromObject（主表）
             */
            QueryObject left;

            /**
             * 自定义 ON 条件构建器
             */
            FsscriptFunction onBuilder;

            /**
             * 预计算的 ON 条件字符串
             * 在 SQL 生成时延迟计算，计算后缓存
             */
            String onCondition;

            public JdbcJoin(QueryObject queryObject, String foreignKey, JoinType joinType) {
                RX.notNull(queryObject, "queryObject不得为空");
                this.queryObject = queryObject;
                this.foreignKey = foreignKey;
                this.joinType = joinType == null ? JoinType.LEFT : joinType;
            }

            /**
             * 带 LEFT 表的构造器（用于嵌套维度）
             */
            public JdbcJoin(QueryObject left, QueryObject right, String foreignKey, JoinType joinType) {
                this(right, foreignKey, joinType);
                this.left = left;
            }

            /**
             * 带 OnBuilder 的构造器
             */
            public JdbcJoin(QueryObject right, FsscriptFunction onBuilder, JoinType joinType) {
                this(right, (String) null, joinType);
                this.onBuilder = onBuilder;
            }

            /**
             * 获取 LEFT 表
             * 如果显式设置了 left，返回 left；否则返回主表 fromObject
             */
            public QueryObject getLeft() {
                return left != null ? left : fromObject;
            }

            public QueryObject getRight() {
                return queryObject;
            }

            public FsscriptFunction getOnBuilder() {
                return onBuilder;
            }

            public boolean contain(QueryObject queryObject) {
                boolean v = this.queryObject.isRootEqual(queryObject);
                return v;
            }

            public String getJoinTypeString() {
                switch (joinType == null ? JoinType.LEFT : joinType) {
                    case RIGHT:
                        return " right join ";
                    case INNER:
                        return " inner join ";
                    case LEFT:
                    default:
                        return " left join ";
                }
            }

            /**
             * 获取 ON 条件（延迟计算）
             * 如果已有 onCondition 缓存，直接返回；否则返回 null 表示需要动态计算
             */
            public String getOnCondition() {
                return onCondition;
            }

            /**
             * 设置预计算的 ON 条件
             */
            public void setOnCondition(String onCondition) {
                this.onCondition = onCondition;
            }
        }

        /**
         * @deprecated 使用 JdbcJoin 的带 left 参数的构造器代替
         */
        @Deprecated
        @Data
        public class JdbcJoinLeft extends JdbcJoin {
            // left 已移到父类，这里为兼容保留
            public JdbcJoinLeft(QueryObject left, QueryObject right, String foreignKey, JoinType joinType) {
                super(left, right, foreignKey, joinType);
            }
        }

        /**
         * @deprecated 使用 JdbcJoin 的带 onBuilder 参数的构造器代替
         */
        @Deprecated
        @Data
        public class JdbcJoinOnBuilder extends JdbcJoin {
            // onBuilder 已移到父类，这里为兼容保留
            public JdbcJoinOnBuilder(QueryObject right, FsscriptFunction onBuilder, JoinType joinType) {
                super(right, onBuilder, joinType);
            }
        }

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public abstract class JdbcCond {
        String link = "";
    }

    @Data
    public abstract class JdbcListCond extends JdbcCond {
        List<JdbcCond> conds = new ArrayList<>();

        public JdbcGroupCond newGroupCond(String link) {
            return new JdbcGroupCond(link);
        }

        public JdbcListCond andQueryTypeValueCond(String name, String queryType, Object value) {
            QueryTypeValueCond c = new QueryTypeValueCond("and", name, queryType, value);
            conds.add(c);
            return this;
        }

        @Deprecated
        public JdbcListCond addSqlFragment(String sqlFragment) {
            SqlFragmentCond c = new SqlFragmentCond(sqlFragment);
            conds.add(c);
            return this;
        }

        public JdbcListCond and(String sqlFragment) {
            SqlFragmentCond c = new SqlFragmentCond(" and " + sqlFragment);
            conds.add(c);
            return this;
        }

        public JdbcListCond and(String sqlFragment, Object value) {
            ValueCond c = new ValueCond("and", sqlFragment, value);
            conds.add(c);
            return this;
        }

        public JdbcListCond link(String sqlFragment, Object value, int link) {
            JdbcLink jdbcLink = JdbcLink.fromCode(link);
            switch (jdbcLink) {
                case OR:
                    return or(sqlFragment, value);
                case AND:
                default:
                    return and(sqlFragment, value);
            }
        }

        public JdbcListCond listLink(String sqlFragment, List<Object> value, int link) {
            String linkStr = "and";
            JdbcLink jdbcLink = JdbcLink.fromCode(link);
            switch (jdbcLink) {
                case OR:
                    linkStr = "OR";
            }

            ListValueCond c = new ListValueCond(linkStr, sqlFragment, value);
            conds.add(c);
            return this;
        }

        public JdbcListCond andList(String sqlFragment, List<Object> value) {
            return listLink(sqlFragment, value, 0);
        }

        public JdbcListCond or(String sqlFragment, Object value) {
            ValueCond c = new ValueCond("or", sqlFragment, value);
            conds.add(c);
            return this;
        }

        public JdbcListCond addCond(JdbcCond cond) {
            conds.add(cond);
            return this;
        }


//        public JdbcListCond orList(String sqlFragment, List<Object> values) {
//            throw new UnsupportedOperationException();
//        }

        public boolean isEmpty() {
            return conds.isEmpty();
        }

        //        public JdbcCond and() {
//
//        }

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class SqlFragmentCond extends JdbcCond {
        String sqlFragment;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class ValueCond extends JdbcCond {
        String sqlFragment;
        Object value;

        public ValueCond(String link, String sqlFragment, Object value) {
            super(link);
            this.sqlFragment = sqlFragment;
            this.value = value;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class QueryTypeValueCond extends JdbcCond {
        Object value;
        String name;
        String queryType;

        public QueryTypeValueCond(String link, String name, String queryType, Object value) {
            super(link);
            this.value = value;
            this.name = name;
            this.queryType = queryType;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class ListValueCond extends JdbcCond {
        String sqlFragment;
        List<Object> value;

        public ListValueCond(String link, String sqlFragment, List<Object> value) {
            super(link);
            this.sqlFragment = sqlFragment;
            this.value = value;
        }
    }

    @Data
    public class JdbcWhere extends JdbcListCond {


    }

    @Data
    public class JdbcGroupCond extends JdbcListCond {
        public JdbcGroupCond(String link) {
            this.link = link;
        }
    }

    @Data
    public class JdbcOrder {
        List<DbQueryOrderColumnImpl> orders;

        public JdbcOrder(DbQueryOrderColumnImpl column) {
            this.orders = new ArrayList<>(1);
            orders.add(column);
        }

        public int size() {
            return orders.size();
        }

        public JdbcOrder(int size) {
            this.orders = new ArrayList<>(size);
        }

        public JdbcOrder(List<DbQueryOrderColumnImpl> orders) {
            this.orders = new ArrayList<>(orders);
        }
    }

    @Data
    public class JdbcGroupBy {
        List<DbQueryGroupColumnImpl> groups;

        public JdbcGroupBy(DbQueryGroupColumnImpl column) {
            this.groups = new ArrayList<>(1);
            groups.add(column);
        }

        public JdbcGroupBy(int size) {
            this.groups = new ArrayList<>(size);
        }

        public JdbcGroupBy(List<DbQueryGroupColumnImpl> orders) {
            this.groups = new ArrayList<>(orders);
        }

        public boolean isEmpty() {
            return groups.isEmpty();
        }
    }
}
