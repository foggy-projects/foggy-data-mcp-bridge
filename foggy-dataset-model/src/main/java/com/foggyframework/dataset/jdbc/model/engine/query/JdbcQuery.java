package com.foggyframework.dataset.jdbc.model.engine.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.engine.formula.JdbcLink;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryGroupColumnImpl;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.JdbcQueryRequest;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import com.foggyframework.dataset.jdbc.model.spi.support.AggregationJdbcColumn;
import com.foggyframework.dataset.jdbc.model.spi.support.SimpleQueryObject;
import com.foggyframework.dataset.jdbc.model.spi.support.SimpleSqlJdbcColumn;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;

@Data
public class JdbcQuery {
    JdbcSelect select;
    JdbcFrom from;

    JdbcWhere where = new JdbcWhere();

    JdbcOrder order;

    JdbcGroupBy group;

    JdbcQueryRequest queryRequest;

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

    public JdbcQuery preJoin(QueryObject queryObject, String foreignKey) {
        RX.notNull(from, "调用preJoin之前，需要先设置from");
        from.preJoin(queryObject, foreignKey);
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

    public JdbcQuery preJoin(QueryObject queryObject, FsscriptFunction onBuilder, JoinType joinType) {
        RX.notNull(from, "调用join之前，需要先设置from");
        from.preJoin(queryObject, onBuilder, joinType);
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
        if (from != null) {
            throw RX.throwAUserTip(DatasetMessages.queryFromDuplicate());
        }

        from = new JdbcFrom(queryObject);
        return this;
    }

    public JdbcQuery select(List<JdbcColumn> selectColumns) {
        RX.notNull(selectColumns, "参数selectColumns不得为空");
        for (JdbcColumn selectColumn : selectColumns) {
            select(selectColumn);
        }
        return this;
    }


    public JdbcQuery select(JdbcColumn selectColumn) {
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

    public void addOrders(List<JdbcQueryOrderColumnImpl> orders) {
        if (order == null) {
            order = new JdbcOrder(orders);
        } else {
            order.getOrders().addAll(orders);
        }

    }

    public void addOrder(JdbcQueryOrderColumnImpl column) {
        if (order == null) {
            order = new JdbcOrder(column);
        } else {
            order.getOrders().add(column);
        }

    }

    public boolean containSelect(JdbcColumn jdbcColumn) {
        for (JdbcColumn column : select.columns) {
            if (StringUtils.equals(column.getAlias(), jdbcColumn.getAlias())) {
                return true;
            }
        }
        return false;
    }

    public void addGroupBy(AggregationJdbcColumn aggColumn, JdbcColumn column) {
        if (group == null) {
            group = new JdbcGroupBy(1);
        }
        group.groups.add(new JdbcQueryGroupColumnImpl(aggColumn));
    }

    @Data
    public class JdbcSelect {

        List<JdbcColumn> columns;

        boolean distinct;

        public JdbcSelect select(JdbcColumn selectColumn) {

            RX.notNull(from, "调用select之前，需要先调用from");

            if (columns == null) {
                columns = new ArrayList<>();
            }
            for (JdbcColumn column : columns) {
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


        List<JdbcJoin> preJoins;

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
            if (queryObject.getLinkQueryObject() != null) {
                join(queryObject.getLinkQueryObject(), joinType);
            }

            if (joins == null) {
                joins = new ArrayList<>();
            }
            for (JdbcJoin join : joins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }
            if (queryObject.getOnBuilder() != null) {
                for (JdbcJoin join : preJoins) {
                    //需要先从preJoins检查,确保 join.getJoinType()正确
                    if (join.contain(queryObject)) {
                        joins.add(join);
                        return this;
                    }
                }
                return join(queryObject, queryObject.getOnBuilder(), joinType);
            }

            if (preJoins != null) {
                for (JdbcJoin join : preJoins) {
                    if (join.contain(queryObject)) {
                        addJoin1(join);
                        return this;
                    }
                }
            }

            // 先检查是否有 LinkQueryObject（嵌套维度场景）
            QueryObject linkQueryObject = queryObject.getLinkQueryObject();
            if (linkQueryObject != null) {
                // 嵌套维度：从 LinkQueryObject 获取外键
                String fk = linkQueryObject.getForeignKey(queryObject);
                if (fk != null) {
                    joins.add(new JdbcJoinLeft(linkQueryObject, queryObject, fk, null));
                    return this;
                }
                // 尝试从已加入的表中找 linkQueryObject 并获取外键
                for (JdbcJoin join : joins) {
                    if (join.contain(linkQueryObject)) {
                        // linkQueryObject 已加入，使用它的外键
                        fk = join.getRight().getForeignKey(queryObject);
                        if (fk != null) {
                            joins.add(new JdbcJoinLeft(join.getRight(), queryObject, fk, null));
                            return this;
                        }
                    }
                }
            }

            String fk = fromObject.getForeignKey(queryObject);
            if (fk == null) {
                for (JdbcJoin join : joins) {
                    fk = join.getRight().getForeignKey(queryObject);
                    if (fk != null) {
                        joins.add(new JdbcJoinLeft(join.getRight(), queryObject, fk, null));
                        return this;
                    }
                }

                if (preJoins != null) {
                    for (JdbcJoin join : preJoins) {
                        fk = join.getRight().getForeignKey(queryObject);
                        if (fk != null) {
                            addJoin1(join);
                            joins.add(new JdbcJoinLeft(join.getRight(), queryObject, fk, null));
                            return this;
                        }
                    }
                }
                throw RX.throwAUserTip(DatasetMessages.queryJoinFieldNotfound(queryObject));

            } else {
                joins.add(new JdbcJoin(queryObject, fk, joinType));
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

        public JdbcFrom preJoin(QueryObject queryObject, String foreignKey) {
            if (preJoins == null) {
                preJoins = new ArrayList<>();
            }
            for (JdbcJoin join : preJoins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }

            preJoins.add(new JdbcJoin(queryObject, foreignKey, null));

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

        public JdbcFrom preJoin(QueryObject queryObject, FsscriptFunction onBuilder, JoinType joinType) {
            if (preJoins == null) {
                preJoins = new ArrayList<>();
            }
            for (JdbcJoin join : preJoins) {
                if (join.contain(queryObject)) {
                    return this;
                }
            }

            preJoins.add(new JdbcJoinOnBuilder(queryObject, onBuilder, joinType));
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

            public JdbcJoin(QueryObject queryObject, String foreignKey, JoinType joinType) {
                RX.notNull(queryObject, "queryObject不得为空");
//                RX.notNull(foreignKey, "foreignKey不得为空");
                this.queryObject = queryObject;
                this.foreignKey = foreignKey;
                this.joinType = joinType == null ? JoinType.LEFT : joinType;
            }

            public QueryObject getLeft() {
                return fromObject;
            }

            public QueryObject getRight() {
                return queryObject;
            }

            public FsscriptFunction getOnBuilder() {
                return null;
            }

            public boolean contain(QueryObject queryObject) {
//                return this.queryObject == queryObject;
                boolean v = this.queryObject.isRootEqual(queryObject);
//                if(StringUtils.equalsIgnoreCase(((TableQueryObject)queryObject.getRoot()).getTableName() , "tms_customer" )
//                &&StringUtils.equalsIgnoreCase(((TableQueryObject)this.queryObject.getRoot()).getTableName() , "tms_customer" )
//                ){
//                    System.out.println(" model contain debug");
//                }
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
        }

        @Data
        public class JdbcJoinLeft extends JdbcJoin {
            QueryObject left;

            public JdbcJoinLeft(QueryObject left, QueryObject right, String foreignKey, JoinType joinType) {
                super(right, foreignKey, joinType);
                this.left = left;
            }
        }

        @Data
        public class JdbcJoinOnBuilder extends JdbcJoin {
            FsscriptFunction onBuilder;

            public JdbcJoinOnBuilder(QueryObject right, FsscriptFunction onBuilder, JoinType joinType) {
                super(right, null, joinType);
                this.onBuilder = onBuilder;
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
        List<JdbcQueryOrderColumnImpl> orders;

        public JdbcOrder(JdbcQueryOrderColumnImpl column) {
            this.orders = new ArrayList<>(1);
            orders.add(column);
        }

        public int size() {
            return orders.size();
        }

        public JdbcOrder(int size) {
            this.orders = new ArrayList<>(size);
        }

        public JdbcOrder(List<JdbcQueryOrderColumnImpl> orders) {
            this.orders = new ArrayList<>(orders);
        }
    }

    @Data
    public class JdbcGroupBy {
        List<JdbcQueryGroupColumnImpl> groups;

        public JdbcGroupBy(JdbcQueryGroupColumnImpl column) {
            this.groups = new ArrayList<>(1);
            groups.add(column);
        }

        public JdbcGroupBy(int size) {
            this.groups = new ArrayList<>(size);
        }

        public JdbcGroupBy(List<JdbcQueryGroupColumnImpl> orders) {
            this.groups = new ArrayList<>(orders);
        }

        public boolean isEmpty() {
            return groups.isEmpty();
        }
    }
}
