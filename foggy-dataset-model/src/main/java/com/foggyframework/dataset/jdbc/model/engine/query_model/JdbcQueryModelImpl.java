package com.foggyframework.dataset.jdbc.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.MongoModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.i18n.DatasetMessages;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.impl.dimension.JdbcDimensionSupport;
import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelSupport;
import com.foggyframework.dataset.jdbc.model.impl.query.*;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.spi.support.JdbcColumnGroup;
import com.foggyframework.dataset.jdbc.model.utils.JdbcModelNamedUtils;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.core.tuple.Tuple3;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

@Data
@Slf4j
public class JdbcQueryModelImpl extends JdbcObjectSupport implements JdbcQueryModel {
    /**
     * selectQueryColumns、或columnGroups
     */

    /**
     * 模型短简称，由 JdbcQueryModelLoader 在加载时分配
     * 用于 AI 元数据生成，减少 token 消耗
     */
    String shortAlias;

    List<JdbcQueryColumn> jdbcQueryColumns = new ArrayList<>();

    Map<String, JdbcQueryColumn> nameToJdbcQueryColumn = new HashMap<>();

    List<JdbcQueryDimension> queryDimensions = new ArrayList<>();

    List<JdbcQueryProperty> queryProperties = new ArrayList<>();

    JdbcModel jdbcModel;

    SqlFormulaService sqlFormulaService;

    DataSource defaultDataSource;

    MongoTemplate defaultMongoTemplate;

    List<JdbcQueryCondition> jdbcQueryConditions;
    Map<String, JdbcQueryCondition> name2JdbcQueryCond = new HashMap<>();

    List<JdbcColumnGroup> columnGroups;

    Map<String, JdbcQueryAccessImpl> dimToJdbcQueryAccess = new HashMap<>();

    Fsscript fsscript;

    List<JdbcQueryOrderColumnImpl> orders = new ArrayList<>();

    List<JdbcModel> jdbcModelList;

    Map<Object, String> name2Alias = new HashMap<>();

    @Getter
    public abstract static class AbstractJdbcModelSupport extends AbstractDelegateDecorate<JdbcModel> implements JdbcModel {
        public AbstractJdbcModelSupport(JdbcModel delegate) {
            super(delegate);
        }

        @Delegate(excludes = AbstractDelegateDecorate.class)
        public JdbcModel getDelegate() {
            return delegate;
        }


    }

    @Getter
    public static class JdbcModelDx extends AbstractJdbcModelSupport implements JdbcModel {

        String alias;

        String foreignKey;

        FsscriptFunction onBuilder;

        JdbcModel dependsOn;

        Map<String, JdbcColumn> name2JdbcColumn = new HashMap<>();

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

        public JdbcModelDx(JdbcModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
        }

        public JdbcModelDx(JdbcModel delegate, String foreignKey, FsscriptFunction onBuilder, String alias, JoinType joinType) {
            super(delegate);
            this.foreignKey = foreignKey;
            this.onBuilder = onBuilder;
            this.alias = alias;
            this.joinType = joinType;
        }


        public String getAlias() {
            return StringUtils.isEmpty(alias) ? delegate.getAlias() : alias;
        }

        public void addDependsOn(JdbcModel dm) {
            dependsOn = dm;
        }

    }

    public JdbcQueryModelImpl(List<JdbcModel> jdbcModelList, Fsscript fsscript, SqlFormulaService sqlFormulaService, DataSource defaultDataSource, MongoTemplate defaultMongoTemplate) {
        this.jdbcModel = jdbcModelList.get(0);
        this.sqlFormulaService = sqlFormulaService;
        this.defaultDataSource = defaultDataSource;
        this.fsscript = fsscript;
        this.jdbcModelList = jdbcModelList;
        for (JdbcModel model : jdbcModelList) {
            Object key = model.getQueryObject();
//            if(name2Alias.containsKey(key)){
//                throw new UnsupportedOperationException();
//            }
            //呃,临时 方案,确保下面的public String getAlias(QueryObject queryObject)能够得到正确的alias
            name2Alias.put(key, model.getAlias());
            name2Alias.put(model.getQueryObject().getDecorate(JdbcModelSupport.ModelQueryObject.class), model.getAlias());
        }
        this.defaultMongoTemplate = defaultMongoTemplate;
    }

    public void init() {

    }

    @Override
    public JdbcModel getJdbcModelByQueryObject(QueryObject queryObject) {
        for (JdbcModel model : this.jdbcModelList) {
            if (model.getQueryObject() == queryObject) {
                return model;
            }
        }
        return null;
    }

    public JdbcQueryOrderColumnImpl addOrder(JdbcColumn jdbcColumn, String order) {
        JdbcQueryOrderColumnImpl c = new JdbcQueryOrderColumnImpl(jdbcColumn, order);
        orders.add(c);
        return c;
    }

    public JdbcQueryOrderColumnImpl addOrder(JdbcColumn jdbcColumn, OrderDef d) {
        JdbcQueryOrderColumnImpl c = new JdbcQueryOrderColumnImpl(jdbcColumn, d);
        orders.add(c);
        return c;
    }


    @Override
    public List<JdbcQueryOrderColumnImpl> getOrders() {
        return orders;
    }

    @Override
    public JdbcQueryColumn getIdJdbcQueryColumn() {
        String idColumn = jdbcModel.getIdColumn();
        if (StringUtils.isEmpty(idColumn)) {
            return null;
        }
        for (JdbcQueryColumn jdbcQueryColumn : jdbcQueryColumns) {

            if (StringUtils.equalsIgnoreCase(jdbcQueryColumn.getSelectColumn().getSqlColumn().getName(), idColumn)) {
                return jdbcQueryColumn;
            }
        }
        return null;
    }

    public void addJdbcQueryColumn(JdbcQueryColumn jdbcQueryColumn) {
        if (jdbcQueryColumns == null) {
            jdbcQueryColumns = new ArrayList<>();
        }

        // 维度特殊处理
        if (jdbcQueryColumn.isDimension()) {
            JdbcDimensionSupport.DimensionCaptionJdbcColumn support = jdbcQueryColumn.getSelectColumn().getDecorate(JdbcDimensionSupport.DimensionCaptionJdbcColumn.class);
            if (support == null) {
                return;
            }
            JdbcDimension jdbcDimension = support.getJdbcDimension();
            JdbcColumn foreignKeyJdbcColumn = support.getJdbcDimension().getForeignKeyJdbcColumn();
            JdbcColumn captionJdbcColumn = support.getJdbcDimension().getCaptionJdbcColumn();
            registerNestedDimensionAliases(jdbcDimension, foreignKeyJdbcColumn, captionJdbcColumn, jdbcQueryColumn.getCaption());
        } else {
            for (JdbcQueryColumn selectQueryColumn : jdbcQueryColumns) {
                if ((selectQueryColumn.getSelectColumn() == jdbcQueryColumn.getSelectColumn()) && (StringUtils.equals(selectQueryColumn.getName(), jdbcQueryColumn.getName()))) {
                    throw RX.throwAUserTip(DatasetMessages.querymodelDuplicateColumn(selectQueryColumn.getSelectColumn().getName()));
                }
            }
            jdbcQueryColumns.add(jdbcQueryColumn);
            if (nameToJdbcQueryColumn.containsKey(jdbcQueryColumn.getName())) {
                throw RX.throwAUserTip(DatasetMessages.querymodelDuplicateQuerycolumn(jdbcQueryColumn.getName()));
            }
            nameToJdbcQueryColumn.put(jdbcQueryColumn.getName(), jdbcQueryColumn);
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
     * @param jdbcDimension        维度
     * @param foreignKeyJdbcColumn 外键列
     * @param captionJdbcColumn    标题列
     * @param caption              标题
     */
    private void registerNestedDimensionAliases(JdbcDimension jdbcDimension, JdbcColumn foreignKeyJdbcColumn, JdbcColumn captionJdbcColumn, String caption) {
        // 1. 如果有别名，用别名注册
        String alias = jdbcDimension.getAlias();
        if (StringUtils.isNotEmpty(alias)) {
            String aliasIdName = alias + "$id";
            String aliasCaptionName = alias + "$caption";
            if (!nameToJdbcQueryColumn.containsKey(aliasIdName)) {
                JdbcQueryColumn aliasIdColumn = new JdbcQueryColumnImpl(foreignKeyJdbcColumn, aliasIdName, foreignKeyJdbcColumn.getCaption(), aliasIdName, aliasIdName);
                nameToJdbcQueryColumn.put(aliasIdName, aliasIdColumn);
                jdbcQueryColumns.add(aliasIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(aliasCaptionName)) {
                JdbcQueryColumn aliasCaptionColumn = new JdbcQueryColumnImpl(captionJdbcColumn, aliasCaptionName, caption, aliasCaptionName, aliasCaptionName);
                nameToJdbcQueryColumn.put(aliasCaptionName, aliasCaptionColumn);
                jdbcQueryColumns.add(aliasCaptionColumn);
            }
        }

        // 2. 如果是嵌套维度，用完整路径注册
        if (jdbcDimension.isNestedDimension()) {
            String fullPath = jdbcDimension.getFullPath();
            String fullPathIdName = fullPath + "$id";
            String fullPathCaptionName = fullPath + "$caption";
            if (!nameToJdbcQueryColumn.containsKey(fullPathIdName)) {
                JdbcQueryColumn fullPathIdColumn = new JdbcQueryColumnImpl(foreignKeyJdbcColumn, fullPathIdName, foreignKeyJdbcColumn.getCaption(), fullPathIdName, fullPathIdName);
                nameToJdbcQueryColumn.put(fullPathIdName, fullPathIdColumn);
            }
            if (!nameToJdbcQueryColumn.containsKey(fullPathCaptionName)) {
                JdbcQueryColumn fullPathCaptionColumn = new JdbcQueryColumnImpl(captionJdbcColumn, fullPathCaptionName, caption, fullPathCaptionName, fullPathCaptionName);
                nameToJdbcQueryColumn.put(fullPathCaptionName, fullPathCaptionColumn);
            }
        }
    }

    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        switch (this.jdbcModel.getModelType()) {
            case mongo:
                return queryMongo(systemBundlesContext, context.getRequest());
            case jdbc:
            default:
                return queryJdbc(systemBundlesContext, context);
        }
    }

    public JdbcQueryResult queryMongo(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form) {
        JdbcQueryRequestDef queryRequest = form.getParam();

        MongoModelQueryEngine queryEngine = new MongoModelQueryEngine(this);

        /**
         * 构建 查询语句
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        Tuple3<Criteria, ProjectionOperation, Sort> options = queryEngine.buildOptions();

        if (log.isDebugEnabled()) {
            log.debug("生成查询对象");
            log.debug(JdbcModelNamedUtils.criteriaToString(options.getT1()));
            log.debug(JdbcModelNamedUtils.projectionOperationToString(options.getT2()));
            if (options.getT3() != null) {
                log.debug(JdbcModelNamedUtils.formatSort(options.getT3()));
            }
        }

        Aggregation queryAgg = Aggregation.newAggregation(Aggregation.match(options.getT1()), options.getT2());
        if (options.getT3() != null) {
            queryAgg.getPipeline().add(Aggregation.sort(options.getT3()));
        }
        queryAgg.getPipeline().add(Aggregation.skip(form.getStart()));
        queryAgg.getPipeline().add(Aggregation.limit(form.getLimit()));

        AggregationResults<Document> results = defaultMongoTemplate.aggregate(queryAgg, this.jdbcModel.getTableName(), Document.class);

        /**
         * 转换objectId
         */
        for (Document r : results.getMappedResults()) {
            if (r.get("_id") instanceof ObjectId) {
                r.put("_id", r.get("_id").toString());
            }
        }
        /**
         * TODO 查询汇总数据
         */
        Map<String, Object> totalData = null;

        int total = 0;
        if (form.getParam().isReturnTotal()) {
            GroupOperation groupOperation = queryEngine.buildGroupOperation(systemBundlesContext, null, queryRequest);
            Aggregation groupAgg = Aggregation.newAggregation(Aggregation.match(options.getT1()), options.getT2(), groupOperation);
            totalData = defaultMongoTemplate.aggregate(groupAgg, this.jdbcModel.getTableName(), Document.class).getUniqueMappedResult();
//            totalData = DataSourceQueryUtils.getDatasetTemplate(defaultDataSource).queryMapObject1(queryEngine.getAggSql(), queryEngine.getValues());
            Number it = totalData == null ? 0 : (Number) totalData.get("total");
            if (it != null && totalData != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }
//        return PagingResultImpl.of(results.getMappedResults(), form.getStart(), form.getLimit(), totalData, total);
        return JdbcQueryResult.of(PagingResultImpl.of(results.getMappedResults(), form.getStart(), form.getLimit(), totalData, total), null);
    }

    /**
     * 执行 JDBC 查询
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文（可能已预处理）
     * @return 查询结果
     */
    public JdbcQueryResult queryJdbc(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        PagingRequest<JdbcQueryRequestDef> form = context.getRequest();
        JdbcQueryRequestDef queryRequest = form.getParam();

        JdbcModelQueryEngine queryEngine = new JdbcModelQueryEngine(this, sqlFormulaService);

        /**
         * 构建 查询语句
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, context);

        String pagingSql = DbUtils.getDialect(defaultDataSource).generatePagingSql(queryEngine.getSql(), form.getStart(), form.getLimit());

        List items;
        if (form.getLimit() < 0) {
            //前端传了小于0的值，意味着不需要查明细~
            items = Collections.EMPTY_LIST;
        } else {
            items = DataSourceQueryUtils.getDatasetTemplate(defaultDataSource).getTemplate().queryForList(pagingSql, queryEngine.getValues().toArray(new Object[0]));
        }

        //对items中的数据进行格式化
        for (JdbcColumn column : queryEngine.getJdbcQuery().getSelect().getColumns()) {
//            log.warn("1");
            if (column instanceof JdbcQueryColumn) {
                ObjectTransFormatter<?> ff = ((JdbcQueryColumn) column).getValueFormatter();
                if (ff != null) {
                    String name = column.getName();
                    for (Object item : items) {
                        if (item instanceof Map) {
                            Map mm = (Map) item;
                            Object v = ff.format(mm.get(name));
                            mm.put(name, v);
                        }
                    }
                }
            }
        }

        /**
         * 查询汇总数据
         */
        Map<String, Object> totalData = null;
        int total = 0;
        if (form.getParam().isReturnTotal()) {
            totalData = DataSourceQueryUtils.getDatasetTemplate(defaultDataSource).queryMapObject1(queryEngine.getAggSql(), queryEngine.getValues());
            Number it = (Number) totalData.get("total");
            if (it != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }
        return JdbcQueryResult.of(PagingResultImpl.of(items, form.getStart(), form.getLimit(), totalData, total), queryEngine);
    }

    @Override
    public QueryObject getQueryObject() {
        return jdbcModel.getQueryObject();
    }

    @Override
    public JdbcColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound) {
        return findJdbcColumnForCond(condColumnName, errorIfNotFound, errorIfNotFound);
    }

    /**
     * @param condColumnName
     * @param errorIfNotFound
     * @param extSearch       当传入true时，会进行扩展搜索，从nameToJdbcQueryColumn抢救下
     * @return
     */
    @Override
    public JdbcColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound, boolean extSearch) {

        JdbcQueryCondition cond = name2JdbcQueryCond.get(condColumnName);
        if (cond != null) {
            return cond.getJdbcColumn();
        }

        JdbcColumn jdbcColumn = null;
        for (JdbcModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(condColumnName);
            if (jdbcColumn != null) {
                break;
            }
        }

        if (extSearch && jdbcColumn == null) {
            for (JdbcModel model : this.jdbcModelList) {
                if (model.isDeprecated(condColumnName)) {
                    return null;
                }
            }
            JdbcQueryColumn qc = this.nameToJdbcQueryColumn.get(condColumnName);
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
        for (JdbcModel model : this.jdbcModelList) {
            sb.append(model.getName()).append(",");
        }
        return sb.toString();
    }

    @Override
    public JdbcQueryColumn findJdbcColumnForSelectByName(String jdbcColumName, boolean errorIfNotFound) {

        JdbcQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }

        for (JdbcQueryColumn jdbcQueryColumn : jdbcQueryColumns) {
            if (StringUtils.equals(jdbcQueryColumn.getName(), jdbcColumName)) {
                return jdbcQueryColumn;
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
    public JdbcQueryColumn findJdbcQueryColumnByName(String jdbcColumName, boolean errorIfNotFound) {
        JdbcQueryColumn queryColumn = nameToJdbcQueryColumn.get(jdbcColumName);
        if (queryColumn != null) {
            return queryColumn;
        }
        for (JdbcQueryColumn jdbcQueryColumn : this.jdbcQueryColumns) {
            if (StringUtils.equals(jdbcQueryColumn.getSelectColumn().getName(), jdbcColumName)) {
                return jdbcQueryColumn;
            }
        }

        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelQuerycolumnNotfound(getName(), jdbcColumName));
        }

        return null;
    }

    @Override
    public JdbcColumn findJdbcColumn(String name) {

        JdbcColumn jdbcColumn = null;
        for (JdbcModel model : this.jdbcModelList) {
            jdbcColumn = model.findJdbcColumnByName(name);
            if (jdbcColumn != null) {
                break;
            }
        }
        return jdbcColumn;
    }

    @Override
    public JdbcDimension findDimension(String name) {

        JdbcDimension dimension = null;
        for (JdbcModel model : this.jdbcModelList) {
            dimension = model.findJdbcDimensionByName(name);
            if (dimension != null) {
                break;
            }
        }

        return dimension;
    }

    @Override
    public JdbcProperty findProperty(String name, boolean errorIfNull) {

        JdbcProperty p = jdbcModel.findJdbcPropertyByName(name);
        if (p != null) {
            return p;
        }
        for (JdbcModel model : this.jdbcModelList) {
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
    public JdbcQueryDimension findQueryDimension(String name, boolean errorIfNotFound) {

        for (JdbcQueryDimension queryDimension : queryDimensions) {
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
    public JdbcQueryProperty findQueryProperty(String name, boolean errorIfNotFound) {

        for (JdbcQueryProperty queryProperty : queryProperties) {
            if (StringUtils.equals(queryProperty.getName(), name)) {
                return queryProperty;
            }
        }
        if (errorIfNotFound) {
            throw RX.throwAUserTip(DatasetMessages.querymodelPropertyNotfound(this.name, name));
        }
        return null;
    }

    @Override
    public DataSource getDataSource() {
        return defaultDataSource;
    }

    @Override
    public FDialect getDialect() {
        return DbUtils.getDialect(defaultDataSource);
    }


    public void addJdbcQueryConds(List<JdbcQueryCondition> values) {
        if (jdbcQueryConditions == null) {
            jdbcQueryConditions = new ArrayList<>();
        }
        jdbcQueryConditions.addAll(values);
        for (JdbcQueryCondition value : values) {
            name2JdbcQueryCond.put(value.getName(), value);
        }

    }

    public void addJdbcQueryCond(JdbcQueryCondition jdbcQueryCondition) {
        if (jdbcQueryConditions == null) {
            jdbcQueryConditions = new ArrayList<>();
        }

        jdbcQueryConditions.add(jdbcQueryCondition);
        name2JdbcQueryCond.put(jdbcQueryCondition.getName(), jdbcQueryCondition);
    }

    @Override
    @Nullable
    public JdbcQueryCondition findJdbcQueryCondByField(String field) {
        if (jdbcQueryConditions == null) {
            return null;
        }
        for (JdbcQueryCondition jdbcQueryCondition : jdbcQueryConditions) {
            if (StringUtils.equals(jdbcQueryCondition.getField(), field)) {
                return jdbcQueryCondition;
            }
        }
        return null;

    }

    @Override
    @Nullable
    public JdbcQueryCondition findJdbcQueryCondByName(String name) {
        if (jdbcQueryConditions == null) {
            return null;
        }
        for (JdbcQueryCondition jdbcQueryCondition : jdbcQueryConditions) {
            if (StringUtils.equals(jdbcQueryCondition.getName(), name)) {
                return jdbcQueryCondition;
            }
        }
        return null;

    }

    @Override
    public List<JdbcColumn> getSelectColumns(boolean newList) {
        List ll = newList ? jdbcQueryColumns.stream().collect(Collectors.toList()) : jdbcQueryColumns;
        return ll;
    }


    public JdbcQueryDimension addQueryDimensionIfNotExist(JdbcDimension jdbcDimension) {
        JdbcQueryDimension d = findQueryDimension(jdbcDimension.getName(), false);
        if (d == null) {
            d = new JdbcQueryDimensionImpl(this, jdbcDimension);
            queryDimensions.add(d);
        }
        return d;
    }

    public JdbcQueryProperty addQueryPropertyIfNotExist(JdbcProperty jdbcProperty) {
        JdbcQueryProperty d = findQueryProperty(jdbcProperty.getName(), false);
        if (d == null) {
            d = new JdbcQueryPropertyImpl(jdbcProperty);
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
    public List<JdbcQueryCondition> getJdbcQueryConds() {
        return jdbcQueryConditions;
    }

}
