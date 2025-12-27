package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.join.JoinEdge;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.impl.dimension.DbDimensionSupport;
import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.dataset.db.model.impl.query.*;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Slf4j
public  abstract class QueryModelSupport extends DbObjectSupport implements QueryModel {
    /**
     * selectQueryColumns、或columnGroups
     */
    @Override
    public List<DbQueryColumn> getJdbcQueryColumns() {
        return dbQueryColumns;
    }

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

    protected   List<DbQueryOrderColumnImpl> orders = new ArrayList<>();

    protected  List<TableModel> jdbcModelList;

    protected   Map<Object, String> name2Alias = new HashMap<>();

    /**
     * 合并后的 JoinGraph（延迟初始化，线程安全）
     * <p>
     * 对于单模型：直接引用主模型的 JoinGraph
     * 对于多模型：合并所有模型的 JoinGraph
     * </p>
     */
    private volatile JoinGraph mergedJoinGraph;

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
            // 使用 getRoot() 作为规范的 key，所有包装器（如 JdbcModelDx）都会解析到同一个 root
            name2Alias.put(model.getQueryObject().getRoot(), model.getAlias());
        }
    }

    /**
     * 获取合并后的 JoinGraph
     * <p>
     * 线程安全的延迟初始化。对于单模型直接返回主模型的 JoinGraph，
     * 对于多模型则合并所有模型的 JoinGraph 并缓存。
     * </p>
     *
     * @return 合并后的 JoinGraph
     */
    public JoinGraph getMergedJoinGraph() {
        if (mergedJoinGraph == null) {
            synchronized (this) {
                if (mergedJoinGraph == null) {
                    mergedJoinGraph = buildMergedJoinGraph();
                }
            }
        }
        return mergedJoinGraph;
    }

    /**
     * 构建合并后的 JoinGraph
     */
    private JoinGraph buildMergedJoinGraph() {
        JoinGraph baseGraph = jdbcModel.getJoinGraph();

        // 单模型：直接返回（不复制，节省内存）
        if (jdbcModelList == null || jdbcModelList.size() <= 1) {
            return baseGraph;
        }

        // 多模型：复制并合并
        JoinGraph merged = baseGraph.copy();

        for (int i = 1; i < jdbcModelList.size(); i++) {
            TableModel tm = jdbcModelList.get(i);
            JdbcModelDx dx = tm.getDecorate(JdbcModelDx.class);

            // 使用原始 delegate 的 QueryObject（与度量列的 QueryObject 的 alias 匹配）
            QueryObject targetQueryObject = dx.getDelegate().getQueryObject();

            // 确定 FROM 表：优先使用 dependsOn，否则使用主模型的 root
            QueryObject fromQueryObject = (dx.getDependsOn() != null)
                    ? dx.getDependsOn().getQueryObject()
                    : baseGraph.getRoot();

            // 添加主边
            if (dx.getOnBuilder() != null) {
                merged.addEdge(fromQueryObject, targetQueryObject,
                        dx.getOnBuilder(), dx.getJoinType());
            } else if (StringUtils.isNotEmpty(dx.getForeignKey())) {
                merged.addEdge(fromQueryObject, targetQueryObject,
                        dx.getForeignKey());
            }

            // 添加次模型的维度边
            JoinGraph secondaryGraph = tm.getJoinGraph();
            if (secondaryGraph != null) {
                for (JoinEdge edge : secondaryGraph.getAllEdges()) {
                    merged.addEdge(edge.getFrom(), edge.getTo(),
                            edge.getForeignKey(), edge.getOnBuilder(),
                            edge.getJoinType());
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("QueryModel [{}] JoinGraph 构建完成: 节点={}, 边={}",
                    name, merged.getNodeCount(), merged.getEdgeCount());
        }

        return merged;
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

    public DbQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, String order) {
        DbQueryOrderColumnImpl c = new DbQueryOrderColumnImpl(jdbcColumn, order);
        orders.add(c);
        return c;
    }

    public DbQueryOrderColumnImpl addOrder(DbColumn jdbcColumn, OrderDef d) {
        DbQueryOrderColumnImpl c = new DbQueryOrderColumnImpl(jdbcColumn, d);
        orders.add(c);
        return c;
    }


    @Override
    public List<DbQueryOrderColumnImpl> getOrders() {
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
            DbColumn foreignKeyJdbcColumn = support.getDimension().getForeignKeyDbColumn();
            DbColumn captionJdbcColumn = support.getDimension().getCaptionDbColumn();
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
                DbQueryColumn aliasIdColumn = new DbQueryColumnImpl(foreignKeyJdbcColumn, aliasIdName, foreignKeyJdbcColumn.getCaption(), aliasIdName, aliasIdName);
                nameToJdbcQueryColumn.put(aliasIdName, aliasIdColumn);
                dbQueryColumns.add(aliasIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(aliasCaptionName)) {
                DbQueryColumn aliasCaptionColumn = new DbQueryColumnImpl(captionJdbcColumn, aliasCaptionName, caption, aliasCaptionName, aliasCaptionName);
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
                DbQueryColumn fullPathIdColumn = new DbQueryColumnImpl(foreignKeyJdbcColumn, fullPathIdName, foreignKeyJdbcColumn.getCaption(), fullPathIdName, fullPathIdName);
                nameToJdbcQueryColumn.put(fullPathIdName, fullPathIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(fullPathCaptionName)) {
                DbQueryColumn fullPathCaptionColumn = new DbQueryColumnImpl(captionJdbcColumn, fullPathCaptionName, caption, fullPathCaptionName, fullPathCaptionName);
                nameToJdbcQueryColumn.put(fullPathCaptionName, fullPathCaptionColumn);
            }
        }
    }

    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
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
        if (queryObject == null) {
            return null;
        }
        if (name2Alias == null) {
            return queryObject.getAlias();
        }
        // 使用 getRoot() 作为 key 查找，与注册时保持一致
        String alias = name2Alias.get(queryObject.getRoot());
        return StringUtils.isNotEmpty(alias) ? alias : queryObject.getAlias();
    }

    @Override
    public List<DbQueryCondition> getDbQueryConds() {
        return dbQueryConditions;
    }

}
