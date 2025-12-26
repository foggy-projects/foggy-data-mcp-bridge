package com.foggyframework.dataset.jdbc.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.jdbc.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.jdbc.model.impl.query.*;
import com.foggyframework.dataset.jdbc.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Data
@Slf4j
public  abstract class QueryModelSupport extends JdbcObjectSupport implements QueryModel {
    /**
     * selectQueryColumns、或columnGroups
     */

    /**
     * 模型短简称，由 JdbcQueryModelLoader 在加载时分配
     * 用于 AI 元数据生成，减少 token 消耗
     */
  protected   String shortAlias;

    protected  List<DbQueryColumn> dbQueryColumns = new ArrayList<>();

    protected   Map<String, DbQueryColumn> nameToJdbcQueryColumn = new HashMap<>();

    protected  List<DbQueryDimension> queryDimensions = new ArrayList<>();

    protected  List<DbQueryProperty> queryProperties = new ArrayList<>();

    protected TableModel jdbcModel;
//
//    SqlFormulaService sqlFormulaService;
//
//    DataSource defaultDataSource;
//
//    MongoTemplate defaultMongoTemplate;

    protected  List<DbQueryCondition> dbQueryConditions;
    protected  Map<String, DbQueryCondition> name2JdbcQueryCond = new HashMap<>();

    protected  List<QueryColumnGroup> columnGroups;

    protected  Map<String, DbQueryAccessImpl> dimToJdbcQueryAccess = new HashMap<>();

    protected  Fsscript fsscript;

    protected   List<JdbcQueryOrderColumnImpl> orders = new ArrayList<>();

    protected  List<TableModel> jdbcModelList;

    protected   Map<Object, String> name2Alias = new HashMap<>();

    @Getter
    public abstract static class AbstractJdbcModelSupport extends AbstractDelegateDecorate<TableModel> implements TableModel {
        public AbstractJdbcModelSupport(TableModel delegate) {
            super(delegate);
        }

        @Delegate(excludes = AbstractDelegateDecorate.class)
        public TableModel getDelegate() {
            return delegate;
        }


    }

    @Getter
    public static class JdbcModelDx extends AbstractJdbcModelSupport implements TableModel {

        String alias;

        String foreignKey;

        FsscriptFunction onBuilder;

        TableModel dependsOn;

        Map<String, DbColumn> name2JdbcColumn = new HashMap<>();

        QueryObject dxQueryObject;

        JoinType joinType = JoinType.LEFT;

        @Override
        public QueryObject getQueryObject() {
            if (dxQueryObject == null) {
                dxQueryObject = new QueryObjectDelegate(delegate.getQueryObject()) {
                    @Override
                    public String getAlias() {
                        return StringUtils.isEmpty(alias) ? super.getAlias() : alias;
                    }

                    @Override
                    public FsscriptFunction getOnBuilder() {
                        return onBuilder == null ? super.getOnBuilder() : onBuilder;
                    }

                    @Override
                    public QueryObject getLinkQueryObject() {
                        if (dependsOn != null) {
                            return dependsOn.getQueryObject();
                        }
                        return super.getLinkQueryObject();
                    }

                    @Override
                    public String getForeignKey(QueryObject joinObject) {
                        if (StringUtils.isNotEmpty(foreignKey) && JdbcModelDx.this.delegate.getQueryObject().isRootEqual(joinObject)) {
                            return foreignKey;
                        }
                        return super.getForeignKey(joinObject);
                    }

                };
            }
            return dxQueryObject;
        }

        public JdbcModelDx(TableModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
        }

        public JdbcModelDx(TableModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias, JoinType joinType) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
            this.joinType = joinType;
        }


        public String getAlias() {
            return StringUtils.isEmpty(alias) ? delegate.getAlias() : alias;
        }

        public void addDependsOn(TableModel dm) {
            dependsOn = dm;
        }

    }

    public QueryModelSupport(List<TableModel> jdbcModelList, Fsscript fsscript) {
        this.jdbcModel = jdbcModelList.get(0);
        this.fsscript = fsscript;
        this.jdbcModelList = jdbcModelList;
        for (TableModel model : jdbcModelList) {
            Object key = model.getQueryObject();
//            if(name2Alias.containsKey(key)){
//                throw new UnsupportedOperationException();
//            }
            //呃,临时 方案,确保下面的public String getAlias(QueryObject queryObject)能够得到正确的alias
            name2Alias.put(key, model.getAlias());
            name2Alias.put(model.getQueryObject().getDecorate(TableModelSupport.ModelQueryObject.class), model.getAlias());
        }
    }



    @Override
    public TableModel getJdbcModelByQueryObject(QueryObject queryObject) {
        for (TableModel model : this.jdbcModelList) {
            if (model.getQueryObject() == queryObject) {
                return model;
            }
        }
        return null;
    }

    public JdbcQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, String order) {
        JdbcQueryOrderColumnImpl c = new JdbcQueryOrderColumnImpl(jdbcColumn, order);
        orders.add(c);
        return c;
    }

    public JdbcQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, OrderDef d) {
        JdbcQueryOrderColumnImpl c = new JdbcQueryOrderColumnImpl(jdbcColumn, d);
        orders.add(c);
        return c;
    }


    @Override
    public List<JdbcQueryOrderColumnImpl> getOrders() {
        return orders;
    }

    @Override
    public DbQueryColumn getIdJdbcQueryColumn() {
        String idColumn = jdbcModel.getIdColumn();
        if (StringUtils.isEmpty(idColumn)) {
            return null;
        }
        for (DbQueryColumn dbQueryColumn : dbQueryColumns) {

            if (StringUtils.equalsIgnoreCase(dbQueryColumn.getSelectColumn().getSqlColumn().getName(), idColumn)) {
                return dbQueryColumn;
            }
        }
        return null;
    }

    public void addJdbcQueryColumn(DbQueryColumn dbQueryColumn) {
        if (dbQueryColumns == null) {
            dbQueryColumns = new ArrayList<>();
        }

        // 维度特殊处理
        if (dbQueryColumn.isDimension()) {
            DbDimensionSupport.DimensionCaptionDbColumn support = dbQueryColumn.getSelectColumn().getDecorate(DbDimensionSupport.DimensionCaptionDbColumn.class);
            if (support == null) {
                return;
            }
            DbDimension dbDimension = support.getDimension();
            DbColumn foreignKeyJdbcColumn = support.getDimension().getForeignKeyJdbcColumn();
            DbColumn captionJdbcColumn = support.getDimension().getCaptionJdbcColumn();
            registerNestedDimensionAliases(dbDimension, foreignKeyJdbcColumn, captionJdbcColumn, dbQueryColumn.getCaption());
        } else {
            for (DbQueryColumn selectQueryColumn : dbQueryColumns) {
                if ((selectQueryColumn.getSelectColumn() == dbQueryColumn.getSelectColumn()) && (StringUtils.equals(selectQueryColumn.getName(), dbQueryColumn.getName()))) {
                    throw RX.throwAUserTip(DatasetMessages.querymodelDuplicateColumn(selectQueryColumn.getSelectColumn().getName()));
                }
            }
            dbQueryColumns.add(dbQueryColumn);
            if (nameToJdbcQueryColumn.containsKey(dbQueryColumn.getName())) {
                throw RX.throwAUserTip(DatasetMessages.querymodelDuplicateQuerycolumn(dbQueryColumn.getName()));
            }
            nameToJdbcQueryColumn.put(dbQueryColumn.getName(), dbQueryColumn);
        }

//        /**
//         * 开始支持$id与$caption，通过维度名称如user,user$id,user$caption。定位维度的id和caption，简化原来user.userCaption/user.userId需要定义三处的设计（user、userId、userCaption）
//         */
//        if (jdbcQueryColumn.isDimension()) {
//            JdbcDimensionSupport.DimensionCaptionJdbcColumn support = jdbcQueryColumn.getSelectColumn().getDecorate(JdbcDimensionSupport.DimensionCaptionJdbcColumn.class);
//            if (support != null && support.getJdbcDimension().getCaptionJdbcColumn() == jdbcQueryColumn.getSelectColumn()) {
//
//                String dimName = jdbcQueryColumn.getName().split("\\.")[0];
//                String idName = dimName + "$id";
//                String captionName = dimName + "$caption";
//                JdbcColumn foreignKeyJdbcColumn = support.getJdbcDimension().getForeignKeyJdbcColumn();
//                JdbcColumn captionJdbcColumn = support.getJdbcDimension().getCaptionJdbcColumn();
//                // 只有当列名不存在时才添加，避免与 QM 中直接定义的列重复
//                if (!nameToJdbcQueryColumn.containsKey(idName)) {
//                    JdbcQueryColumn idColumn = new JdbcQueryColumnImpl(foreignKeyJdbcColumn, idName, foreignKeyJdbcColumn.getCaption(), idName, idName);
//                    nameToJdbcQueryColumn.put(idName, idColumn);
//                }
//                if (!nameToJdbcQueryColumn.containsKey(captionName)) {
//                    JdbcQueryColumn captionColumn = new JdbcQueryColumnImpl(captionJdbcColumn, captionName, jdbcQueryColumn.getCaption(), captionName, captionName);
//                    nameToJdbcQueryColumn.put(captionName, captionColumn);
//                }
//
//
//            }
//        } else if (jdbcQueryColumn.isProperty()) {
//            JdbcPropertyColumn jdbcQueryProperty = jdbcQueryColumn.getSelectColumn().getDecorate(JdbcPropertyColumn.class);
//            if (jdbcQueryProperty.getJdbcProperty().isDict()) {
//                //带字典表的补下$id
//                String name = jdbcQueryProperty.getName();
//                String idName = name + "$id";
//                JdbcColumn column = jdbcQueryColumn.getSelectColumn();
//                JdbcQueryColumn idColumn = new JdbcQueryColumnImpl(column, idName, jdbcQueryProperty.getCaption(), idName, idName);
//                nameToJdbcQueryColumn.put(idName, idColumn);
//
//                String captionName = name + "$caption";
//                JdbcQueryColumnImpl captionColumn = new JdbcQueryColumnImpl(column, captionName, jdbcQueryProperty.getCaption(), captionName, captionName);
//                nameToJdbcQueryColumn.put(captionName, captionColumn);
//                captionColumn.setValueFormatter(new ClassDictObjectTransFormatter(jdbcQueryProperty.getJdbcProperty().getExtDataValue("dictClass")));
//
//            }
//        }
    }

    /**
     * 为嵌套维度注册别名和完整路径的访问方式
     *
     * @param dbDimension        维度
     * @param foreignKeyJdbcColumn 外键列
     * @param captionJdbcColumn    标题列
     * @param caption              标题
     */
    private void registerNestedDimensionAliases(DbDimension dbDimension, DbColumn foreignKeyJdbcColumn, DbColumn captionJdbcColumn, String caption) {
        // 1. 如果有别名，用别名注册
        String alias = dbDimension.getAlias();
        if (StringUtils.isNotEmpty(alias)) {
            String aliasIdName = alias + "$id";
            String aliasCaptionName = alias + "$caption";
            if (!nameToJdbcQueryColumn.containsKey(aliasIdName)) {
                DbQueryColumn aliasIdColumn = new JdbcQueryColumnImpl(foreignKeyJdbcColumn, aliasIdName, foreignKeyJdbcColumn.getCaption(), aliasIdName, aliasIdName);
                nameToJdbcQueryColumn.put(aliasIdName, aliasIdColumn);
                dbQueryColumns.add(aliasIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(aliasCaptionName)) {
                DbQueryColumn aliasCaptionColumn = new JdbcQueryColumnImpl(captionJdbcColumn, aliasCaptionName, caption, aliasCaptionName, aliasCaptionName);
                nameToJdbcQueryColumn.put(aliasCaptionName, aliasCaptionColumn);
                dbQueryColumns.add(aliasCaptionColumn);
            }
        }

        // 2. 如果是嵌套维度，用完整路径注册
        if (dbDimension.isNestedDimension()) {
            String fullPath = dbDimension.getFullPath();
            String fullPathIdName = fullPath + "$id";
            String fullPathCaptionName = fullPath + "$caption";
            if (!nameToJdbcQueryColumn.containsKey(fullPathIdName)) {
                DbQueryColumn fullPathIdColumn = new JdbcQueryColumnImpl(foreignKeyJdbcColumn, fullPathIdName, foreignKeyJdbcColumn.getCaption(), fullPathIdName, fullPathIdName);
                nameToJdbcQueryColumn.put(fullPathIdName, fullPathIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(fullPathCaptionName)) {
                DbQueryColumn fullPathCaptionColumn = new JdbcQueryColumnImpl(captionJdbcColumn, fullPathCaptionName, caption, fullPathCaptionName, fullPathCaptionName);
                nameToJdbcQueryColumn.put(fullPathCaptionName, fullPathCaptionColumn);
            }
        }
    }

    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

//    @Override
//    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
//        switch (this.jdbcModel.getModelType()) {
//            case mongo:
//                return queryMongo(systemBundlesContext, context.getRequest());
//            case jdbc:
//            default:
//                return queryJdbc(systemBundlesContext, context);
//        }
//    }



    @Override
    public QueryObject getQueryObject() {
        return jdbcModel.getQueryObject();
    }

    @Override
    public DbColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound) {
        return findJdbcColumnForCond(condColumnName, errorIfNotFound, errorIfNotFound);
    }

    /**
     * @param condColumnName
     * @param errorIfNotFound
     * @param extSearch       当传入true时，会进行扩展搜索，从nameToJdbcQueryColumn抢救下
     * @return
     */
    @Override
    public DbColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound, boolean extSearch) {

        DbQueryCondition cond = name2JdbcQueryCond.get(condColumnName);
        if (cond != null) {
            return cond.getColumn();
        }

        DbColumn jdbcColumn = null;
        for (TableModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(condColumnName);
            if (jdbcColumn != null) {
                break;
            }
        }

        if (extSearch && jdbcColumn == null) {
            for (TableModel model : this.jdbcModelList) {
                if (model.isDeprecated(condColumnName)) {
                    return null;
                }
            }
            DbQueryColumn qc = this.nameToJdbcQueryColumn.get(condColumnName);
            if (qc != null) {
                return qc.getSelectColumn();
            }


            //还没有找到？启动从维度Caption搜索~
//            for (JdbcQueryColumn jdbcQueryColumn : jdbcQueryColumns) {
//                /**
//                 * 当维度nextTeam.nextTeamCaption，用户传入nextTeamId，找不到而抛错的情况~
//                 * 注意，jdbcQueryColumn必须是DimensionCaptionJdbcColumn列才行~~
//                 */
//                if (jdbcQueryColumn.getSelectColumn() != null && jdbcQueryColumn.getSelectColumn().isDimension()) {
//                    JdbcDimensionSupport.DimensionCaptionJdbcColumn support = jdbcQueryColumn.getSelectColumn().getDecorate(JdbcDimensionSupport.DimensionCaptionJdbcColumn.class);
//                    if (support != null && StringUtils.equals(support.getJdbcDimension().getForeignKeyAlias(), condColumnName)) {
//                        return jdbcQueryColumn;
//                    }
//
//                }
//            }
//            if (jdbcQueryConds != null) {
//                for (JdbcQueryCond jdbcQueryColumn : jdbcQueryConds) {
//                    /**
//                     * 当维度nextTeam.nextTeamCaption，用户传入nextTeamId，找不到而抛错的情况~
//                     * 注意，jdbcQueryColumn必须是DimensionCaptionJdbcColumn列才行~~
//                     */
//                    if (jdbcQueryColumn.getJdbcColumn() != null && jdbcQueryColumn.getJdbcColumn().isDimension()) {
//                        JdbcDimensionSupport.DimensionCaptionJdbcColumn support = jdbcQueryColumn.getJdbcColumn().getDecorate(JdbcDimensionSupport.DimensionCaptionJdbcColumn.class);
//                        if (support != null && StringUtils.equals(support.getJdbcDimension().getCaptionAlias(), condColumnName)) {
//                            return jdbcQueryColumn.getJdbcColumn();
//                        }
//                        JdbcDimensionSupport.DimensionJdbcColumn support2 = jdbcQueryColumn.getJdbcColumn().getDecorate(JdbcDimensionSupport.DimensionJdbcColumn.class);
//                        if (support2 != null) {
//                            if (StringUtils.equals(support2.getJdbcDimension().getForeignKeyAlias(), condColumnName)) {
//                                return jdbcQueryColumn.getJdbcColumn();
//                            } else if (StringUtils.equals(support2.getJdbcDimension().getCaptionAlias(), condColumnName)) {
//                                return support2.getJdbcDimension().getCaptionJdbcColumn();
//                            }
//                        }
//
//                    }
//                }
//            } else {
//                log.warn("jdbcQueryConds is null");
//            }

            if (errorIfNotFound) {
                throw RX.throwAUserTip(DatasetMessages.querymodelColumnNotfound(getName(), toJdbcModelListName(), condColumnName, findDimension(condColumnName)));
            }
        }

        return jdbcColumn;
    }

    private String toJdbcModelListName() {
        StringBuilder sb = new StringBuilder();
        for (TableModel model : this.jdbcModelList) {
            sb.append(model.getName()).append(",");
        }
        return sb.toString();
    }

    @Override
    public DbQueryColumn findJdbcColumnForSelectByName(String jdbcColumName, boolean errorIfNotFound) {

        DbQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }

        for (DbQueryColumn dbQueryColumn : dbQueryColumns) {
            if (StringUtils.equals(dbQueryColumn.getName(), jdbcColumName)) {
                return dbQueryColumn;
            }
        }


//        /**
//         * 下面的两个循环，考虑在jdbcQueryColumns时就初始化好一个附加列名映射nameToJdbcQueryColumn，例如
//         * nextTeam.nextTeamCaption/nextTeam.nextTeamId
//         * 事先就维护好nextTeamCaption、nextTeamId的map表~，在上面的循环找不到时使用（需要检查这两个必须是在同一个维度~）
//         */
//        for (JdbcQueryColumn jdbcQueryColumn : jdbcQueryColumns) {
//            //用于兼容nextTeam.nextTeamCaption/nextTeam.nextTeamId这样的
//            //这里加是比较安全的，因为找不到，下面就是抛异常了~
//            if (jdbcQueryColumn.getSelectColumn() != null && StringUtils.equals(jdbcQueryColumn.getSelectColumn().getAlias(), jdbcColumName)) {
//                return jdbcQueryColumn;
//            }
//        }
//        //还没有找到？启动从维度Caption搜索~
//        for (JdbcQueryColumn jdbcQueryColumn : jdbcQueryColumns) {
//            /**
//             * 当维度nextTeam.nextTeamCaption，用户传入nextTeamId，找不到而抛错的情况~
//             * 注意，jdbcQueryColumn必须是DimensionCaptionJdbcColumn列才行~~
//             */
//            if (jdbcQueryColumn.getSelectColumn() != null && jdbcQueryColumn.getSelectColumn().isDimension()) {
//                JdbcDimensionSupport.DimensionCaptionJdbcColumn support = jdbcQueryColumn.getSelectColumn().getDecorate(JdbcDimensionSupport.DimensionCaptionJdbcColumn.class);
//                if (support != null && StringUtils.equals(support.getJdbcDimension().getForeignKeyAlias(), jdbcColumName)) {
//                    return jdbcQueryColumn;
//                }
//
//            }
//        }
        /**
         * end ***************************
         */
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelColumnNotfoundSimple(this.name, jdbcColumName, findDimension(jdbcColumName)));
        }

        return null;
    }

    @Override
    public DbQueryColumn findJdbcQueryColumnByName(String jdbcColumName, boolean errorIfNotFound) {
        DbQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }
        for (DbQueryColumn dbQueryColumn : this.dbQueryColumns) {
            if (StringUtils.equals(dbQueryColumn.getSelectColumn().getName(), jdbcColumName)) {
                return dbQueryColumn;
            }
        }

        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelQuerycolumnNotfound(getName(), jdbcColumName));
        }

        return null;
    }

    @Override
    public DbColumn findJdbcColumn(String name) {

        DbColumn jdbcColumn = null;
        for (TableModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(name);
            if (jdbcColumn != null) {
                break;
            }
        }
        return jdbcColumn;
    }

    @Override
    public DbDimension findDimension(String name) {

        DbDimension dimension = null;
        for (TableModel model : this.jdbcModelList) {
            dimension = model.findJdbcDimensionByName(name);
            if (dimension != null) {
                break;
            }
        }

        return dimension;
    }

    @Override
    public DbProperty findProperty(String name, boolean errorIfNull) {

        DbProperty p = jdbcModel.findJdbcPropertyByName(name);
        if (p != null) {
            return p;
        }
        for (TableModel model : this.jdbcModelList) {
            p = model.findJdbcPropertyByName(name);
            if (p != null) {
                return p;
            }
        }
        if (errorIfNull) {
            throw new RuntimeException("未能找到属性:" + name);
        }
        return p;
    }

    @Override
    public DbQueryDimension findQueryDimension(String name, boolean errorIfNotFound) {

        for (DbQueryDimension queryDimension : queryDimensions) {
            if (StringUtils.equals(queryDimension.getName(), name)) {
                return queryDimension;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelDimensionNotfound(jdbcModel.getName(), name));
        }
        return null;
    }

    @Override
    public DbQueryProperty findQueryProperty(String name, boolean errorIfNotFound) {

        for (DbQueryProperty queryProperty : queryProperties) {
            if (StringUtils.equals(queryProperty.getName(), name)) {
                return queryProperty;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelPropertyNotfound(this.name, name));
        }
        return null;
    }

//    @Override
//    public DataSource getDataSource() {
//        return defaultDataSource;
//    }
//
//    @Override
//    public FDialect getDialect() {
//        return DbUtils.getDialect(defaultDataSource);
//    }


    public void addJdbcQueryConds(List<DbQueryCondition> values) {
        if (dbQueryConditions == null) {
            dbQueryConditions = new ArrayList<>();
        }
        dbQueryConditions.addAll(values);
        for (DbQueryCondition value : values) {
            name2JdbcQueryCond.put(value.getName(), value);
        }

    }

    public void addJdbcQueryCond(DbQueryCondition dbQueryCondition) {
        if (dbQueryConditions == null) {
            dbQueryConditions = new ArrayList<>();
        }

        dbQueryConditions.add(dbQueryCondition);
        name2JdbcQueryCond.put(dbQueryCondition.getName(), dbQueryCondition);
    }

    @Override
    @Nullable
    public DbQueryCondition findJdbcQueryCondByField(String field) {
        if (dbQueryConditions == null) {
            return null;
        }
        for (DbQueryCondition dbQueryCondition : dbQueryConditions) {
            if (StringUtils.equals(dbQueryCondition.getField(), field)) {
                return dbQueryCondition;
            }
        }
        return null;

    }

    @Override
    @Nullable
    public DbQueryCondition findJdbcQueryCondByName(String name) {
        if (dbQueryConditions == null) {
            return null;
        }
        for (DbQueryCondition dbQueryCondition : dbQueryConditions) {
            if (StringUtils.equals(dbQueryCondition.getName(), name)) {
                return dbQueryCondition;
            }
        }
        return null;

    }

    @Override
    public List<DbColumn> getSelectColumns(boolean newList) {
        List ll = newList ? dbQueryColumns.stream().collect(Collectors.toList()) : dbQueryColumns;
        return ll;
    }


    public DbQueryDimension addQueryDimensionIfNotExist(DbDimension dbDimension) {
        DbQueryDimension d = findQueryDimension(dbDimension.getName(), false);
        if (d == null) {
            d = new DbQueryDimensionImpl(this, dbDimension);
            queryDimensions.add(d);
        }
        return d;
    }

    public DbQueryProperty addQueryPropertyIfNotExist(DbProperty dbProperty) {
        DbQueryProperty d = findQueryProperty(dbProperty.getName(), false);
        if (d == null) {
            d = new DbQueryPropertyImpl(dbProperty);
            queryProperties.add(d);
        }
        return d;
    }

    @Override
    public String getAlias(QueryObject queryObject) {
        String name = null;
        if (name2Alias == null) {
            return queryObject.getAlias();
        }
        if (queryObject == null) {
            return null;
        }
        name = name2Alias.get(queryObject);
        if (StringUtils.isEmpty(name)) {
            return queryObject.getAlias();
        }
        return name;
    }

    @Override
    public List<DbQueryCondition> getJdbcQueryConds() {
        return dbQueryConditions;
    }

}
