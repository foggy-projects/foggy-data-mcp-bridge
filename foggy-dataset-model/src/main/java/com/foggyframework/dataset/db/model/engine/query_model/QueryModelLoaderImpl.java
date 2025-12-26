package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.tuple.Tuple2;
import com.foggyframework.core.utils.ErrorUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.access.DbAccessDef;
import com.foggyframework.dataset.db.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.def.query.QueryConditionDef;
import com.foggyframework.dataset.db.model.def.query.SelectColumnDef;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.impl.query.*;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.JoinType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Setter
@Getter
public class QueryModelLoaderImpl extends LoaderSupport implements QueryModelLoader {

    private TableModelLoaderManager tableModelLoaderManager;
//
//    SqlFormulaService sqlFormulaService;

    @Resource
    private DataSource defaultDataSource;

    private DbModelFileChangeHandler fileChangeHandler;

    private Map<String, QueryModel> name2JdbcQueryModel = new HashMap<>();

    /**
     * 简称到模型名称的映射，用于通过简称查询模型
     */
    private Map<String, String> shortAlias2Name = new HashMap<>();

    /**
     * 已使用的简称集合，包括所有模型全名（避免简称与全名冲突）
     */
    private Set<String> usedAliases = new HashSet<>();


    private List<QueryModelBuilder> queryModelBuilders;
    /**
     * 驼峰命名模式，用于提取大写字母
     */
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("[A-Z][a-z]*");

    public QueryModelLoaderImpl(TableModelLoaderManager tableModelLoaderManager,
                                SystemBundlesContext systemBundlesContext,
                                FileFsscriptLoader fileFsscriptLoader,
                                List<QueryModelBuilder> queryModelBuilders) {
        super(systemBundlesContext, fileFsscriptLoader);
        this.tableModelLoaderManager = tableModelLoaderManager;
        this.queryModelBuilders = queryModelBuilders;
    }

    @Override
    public void clearAll() {
        name2JdbcQueryModel = new HashMap<>();
        shortAlias2Name = new HashMap<>();
        usedAliases = new HashSet<>();
    }

    /**
     * 在执行查询前，我们需要先获取查询模型
     *
     * <p>支持通过模型全名或简称查询
     *
     * @param queryModelNameOrAlias 模型名称或简称
     * @return 查询模型
     */
    @Override
    public QueryModel getJdbcQueryModel(String queryModelNameOrAlias) {
        // 1. 先尝试通过全名查找
        QueryModel tm = name2JdbcQueryModel.get(queryModelNameOrAlias);
        if (tm != null) {
            return tm;
        }

        // 2. 尝试通过简称查找
        String fullName = shortAlias2Name.get(queryModelNameOrAlias);
        if (fullName != null) {
            return name2JdbcQueryModel.get(fullName);
        }

        // 3. 加载新模型（此时 queryModelNameOrAlias 应该是全名）
        Fsscript fsscript = findFsscript(queryModelNameOrAlias, "qm");
        ExpEvaluator ee = fsscript.eval(systemBundlesContext.getApplicationContext());
        Object queryModel = ee.getExportObject("queryModel");
        DbQueryModelDef queryModelDef = FsscriptConversionService.getSharedInstance().convert(queryModel, DbQueryModelDef.class);

        tm = loadJdbcQueryModel(ee, fsscript, queryModelDef);
        registerQueryModel(queryModelNameOrAlias, (QueryModelSupport) tm);
        return tm;
    }

    @Override
    public QueryModel loadJdbcQueryModel(BundleResource bundleResource) {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource);
        ExpEvaluator ee = fsscript.eval(systemBundlesContext.getApplicationContext());
        Object queryModel = ee.getExportObject("queryModel");
        DbQueryModelDef queryModelDef = FsscriptConversionService.getSharedInstance().convert(queryModel, DbQueryModelDef.class);
        try {
            QueryModelSupport qm = loadJdbcQueryModel(ee, fsscript, queryModelDef);
            // 注册模型并分配简称
            String modelName = qm.getName();
            if (!name2JdbcQueryModel.containsKey(modelName)) {
                registerQueryModel(modelName, qm);
            }
            return qm;
        } catch (Throwable t) {
            log.error(String.format("加载%s时出现异常", bundleResource));
            throw ErrorUtils.toRuntimeException(t);
        }
    }

    private QueryModelSupport loadJdbcQueryModel(ExpEvaluator ee, Fsscript fsscript, DbQueryModelDef queryModelDef) {
        if (queryModelDef == null) {
            throw RX.throwAUserTip(DatasetMessages.querymodelExportMissing(fsscript.getPath()));
        }
//        RX.notNull(queryModelDef, "需要有查询模型定义");
        if (StringUtils.isEmpty(queryModelDef.getModel())) {
            throw RX.throwAUserTip(DatasetMessages.querymodelModelMissing(queryModelDef.getName()));
        }
//        MongoTemplate modelMongoTemplate = null;

        List<TableModel> jdbcModelDxList = null;
        if (queryModelDef.getModel() instanceof List) {
            List<Object> ll = (List<Object>) queryModelDef.getModel();
            jdbcModelDxList = new ArrayList<>(ll.size());
            Map<String, TableModel> aliasToJdbcModel = new HashMap<>();
            for (Object s : ll) {
                if (s instanceof String) {
                    TableModel jdbcModel = tableModelLoaderManager.load((String) s);
                    jdbcModelDxList.add(new DbQueryModelImpl.JdbcModelDx(jdbcModel, jdbcModel.getIdColumn(), null, null));
                } else if (s instanceof Map) {
                    TableModel jdbcModel = tableModelLoaderManager.load((String) ((Map<?, ?>) s).get("name"));
                    String foreignKey = (String) ((Map<?, ?>) s).get("foreignKey");
                    if (StringUtils.isEmpty(foreignKey)) {
                        foreignKey = jdbcModel.getIdColumn();
                    }
                    String alias = (String) ((Map<?, ?>) s).get("alias");
                    String join = (String) ((Map<?, ?>) s).get("join");

                    FsscriptFunction onBuilder = (FsscriptFunction) ((Map<?, ?>) s).get("onBuilder");
                    DbQueryModelImpl.JdbcModelDx dx = new DbQueryModelImpl.JdbcModelDx(jdbcModel, foreignKey, onBuilder, alias,
                            StringUtils.isEmpty(join) ? JoinType.LEFT : JoinType.valueOf(join.toUpperCase()));
                    jdbcModelDxList.add(dx);

                    //处理dependsOn
                    String d = (String) ((Map<?, ?>) s).get("dependsOn");
                    if (StringUtils.isNotTrimEmpty(d)) {
                        TableModel dm = aliasToJdbcModel.get(d);
                        RX.notNull(dm, String.format("未能根据alias:[%s]在当前查询模型:[%s]中找到TM,请确保在:[%s]前定义",
                                d, queryModelDef.getName(), alias
                        ));
                        dx.addDependsOn(dm);
                    }

                    aliasToJdbcModel.put(dx.getAlias(), dx);

                } else {
                    throw new UnsupportedOperationException();
                }

            }


        } else if (queryModelDef.getModel() instanceof String) {
            TableModel jdbcModel = tableModelLoaderManager.load((String) queryModelDef.getModel());
//            modelMongoTemplate = jdbcModel.getMongoTemplate();
            jdbcModelDxList = new ArrayList<>(1);
            jdbcModelDxList.add(new DbQueryModelImpl.JdbcModelDx(jdbcModel, jdbcModel.getIdColumn(), null, null, JoinType.LEFT));
        } else {
            throw new UnsupportedOperationException();
        }

        /**
         * 构建JdbcQueryModelImpl
         */
        QueryModelSupport qm = null;
        for (QueryModelBuilder queryModelBuilder : queryModelBuilders) {
            qm = queryModelBuilder.build(queryModelDef, fsscript, jdbcModelDxList);
            if (qm != null) {
                break;
            }
        }
        if (qm == null) {
            throw RX.throwAUserTip("无法找到对应的QueryModelBuilder");
        }

//        if (modelMongoTemplate == null) {
//            modelMongoTemplate = mongoTemplate;
//        }
//        DataSource ds = queryModelDef.getDataSource();
//        //从tm文件中提取数据源
//        if (ds == null) {
//            for (JdbcModel jdbcModel : jdbcModelDxList) {
//                if (jdbcModel.getDataSource() != null) {
//                    if (ds == null) {
//                        ds = jdbcModel.getDataSource();
//                    } else if (ds != jdbcModel.getDataSource()) {
//                        throw RX.throwAUserTip("不同数据源的TM不能配置在一起");
//                    }
//                }
//            }
//        }

//        JdbcQueryModelImpl qm = new JdbcQueryModelImpl(jdbcModelDxList, fsscript, sqlFormulaService,
//                ds == null ? defaultDataSource : ds,
//                modelMongoTemplate);
        queryModelDef.apply(qm);

        /**
         * step10.加载columnGroups中的列
         */
        loadColumnGroups(qm, queryModelDef);
        /**
         * step20.构建查询条件JdbcQueryCond，原则上，所有的select列都需要有查询条件
         */
        //先生成QM中定义的条件
        List<QueryConditionDef> conds = queryModelDef.getConds();
        List<DbQueryCondition> dbQueryConditions = new ArrayList<>();
        if (conds != null) {
            for (QueryConditionDef cond : conds) {
                String field = cond.getField();
                String column = cond.getColumn();
                RX.hasText(column, String.format("查询模型%s中条件%s的column属性不能为空", queryModelDef.getName(), cond));

                DbColumn jdbcColumn = qm.findJdbcColumnForCond(column, true);
                RX.notNull(jdbcColumn, String.format("查询模型%s中通过条件的field:%s未能找到JdbcColumn", queryModelDef.getName(), cond));

                DbQueryConditionImpl jdbcQueryCond = new DbQueryConditionImpl();
                cond.apply(jdbcQueryCond);
                jdbcQueryCond.setQueryModel(qm);
                jdbcQueryCond.setColumn(jdbcColumn);
                if (StringUtils.isEmpty(jdbcQueryCond.getName())) {
                    //如果条件没有定义 name,则默认同它的jdbcColumn
                    jdbcQueryCond.setName(jdbcColumn.getName());
                }

                dbQueryConditions.add(jdbcQueryCond);
                DbDimensionColumn dimensionColumn = jdbcColumn.getDecorate(DbDimensionColumn.class);
                if (dimensionColumn != null) {
                    qm.addQueryDimensionIfNotExist(dimensionColumn.getDimension());
                    jdbcQueryCond.setDimension(dimensionColumn.getDimension());
                }
                DbPropertyColumn dbPropertyColumn = jdbcColumn.getDecorate(DbPropertyColumn.class);
                if (dbPropertyColumn != null) {
                    qm.addQueryPropertyIfNotExist(dbPropertyColumn.getProperty());
                    jdbcQueryCond.setProperty(dbPropertyColumn.getProperty());
                }


            }
        }
        qm.addJdbcQueryConds(dbQueryConditions);
        /**
         * step30.为JdbcQueryColumn补jdbcQueryCond
         */
        for (DbQueryColumn dbQueryColumn : qm.getDbQueryColumns()) {
            String condColumnName = dbQueryColumn.getName();
            DbColumn jdbcColumn = dbQueryColumn.getSelectColumn();
            DbDimensionColumn dimensionColumn = jdbcColumn.getDecorate(DbDimensionColumn.class);
            if (dimensionColumn != null && dimensionColumn.isCaptionColumn()) {
                //这里比较特殊,如果是维度的标题列，则应当用id列来查，但是，如果条件中已经定义了condColumnName这个查询条件，则以查询条件中定义的为准！
                DbQueryCondition dbQueryCondition = qm.findJdbcQueryCondByName(condColumnName);
                if (dbQueryCondition == null) {
                    condColumnName = dimensionColumn.getDimension().getForeignKeyJdbcColumn().getName();
                }

            }
            if (jdbcColumn.isDimension()) {
                //把该维度加到列表
                qm.addQueryDimensionIfNotExist(jdbcColumn.getDecorate(DbDimensionColumn.class).getDimension());
            } else if (jdbcColumn.isProperty()) {
                //把该维度加到列表
                qm.addQueryPropertyIfNotExist(jdbcColumn.getDecorate(DbPropertyColumn.class).getProperty());
            }

            DbQueryCondition dbQueryCondition = qm.findJdbcQueryCondByName(condColumnName);

            if (dbQueryCondition == null) {
                //该selectColumn没有关联的jdbcQueryCond？定义一个
                dbQueryCondition = autoCreateJdbcQueryCond(qm, dbQueryColumn, qm.findJdbcColumnForCond(condColumnName, true));
                qm.addJdbcQueryCond(dbQueryCondition);
            }
//            jdbcQueryColumn.getDecorate(JdbcQueryColumnImpl.class).setd(jdbcQueryCond);
            dbQueryColumn.getDecorate(DbQueryColumnImpl.class).setDbQueryCondition(dbQueryCondition);
        }
        /**
         * step35.加载orders
         */
        loadOrders(qm, queryModelDef.getOrders());

        /**
         * step40.加载权限数据
         */
        loadAccesses(qm, queryModelDef.getAccesses());

        /**
         * step50.补一些默认值
         */
        for (DbQueryCondition dbQueryCondition : qm.getDbQueryConditions()) {
            fixJdbcQueryCond(qm, (DbQueryConditionImpl) dbQueryCondition, qm.findJdbcColumnForCond(dbQueryCondition.getName(), false));
        }

        /**
         * 呃，如果存在ID列，默认用它来排
         */
        DbQueryColumn idQueryColumn = qm.getIdJdbcQueryColumn();
        if (idQueryColumn != null) {
            boolean inOrder = false;
            for (DbQueryOrderColumnImpl order : qm.getOrders()) {
                if (order.getSelectColumn() == idQueryColumn.getSelectColumn()) {
                    inOrder = true;
                }
            }
            if (!inOrder) {
                qm.addOrder(idQueryColumn.getSelectColumn(), "desc");
            }
        }

        return qm;
    }

    private void loadOrders(QueryModelSupport qm, List<OrderDef> orders) {
        if (orders != null) {
            for (int i = 0; i < orders.size(); i++) {
                OrderDef d = orders.get(i);
//                qm.addOrder(qm.findJdbcQueryColumnByName(d.getName(), true).getSelectColumn(), d.getOrder());
                qm.addOrder(qm.findJdbcQueryColumnByName(d.getName(), true).getSelectColumn(), d);
            }
//            for (int i = orders.size() - 1; i >= 0; i--) {
//
//            }
        }
    }

    private void loadDimension(DbQueryModelImpl qm, DbQueryModelDef queryModelDef) {
//        qm.setQueryDimensions(qm.getJdbcModel().getDimensions().stream().map(e -> new JdbcQueryDimensionImpl(e)).collect(Collectors.toList()));
    }

    /**
     * 加载columnGroups中的列,注意，此时不关联查询条件
     *
     * @param qm
     * @param queryModelDef
     */
    private void loadColumnGroups(QueryModelSupport qm, DbQueryModelDef queryModelDef) {
        if (queryModelDef.getColumnGroups() != null && !queryModelDef.getColumnGroups().isEmpty()) {
            List<QueryColumnGroup> columnGroups = new ArrayList<>();
            for (DbColumnGroupDef columnGroupDef : queryModelDef.getColumnGroups()) {
                if (columnGroupDef.getItems() == null || columnGroupDef.getItems().isEmpty()) {
                    continue;
                }
                QueryColumnGroup group = new QueryColumnGroup();
                group.setCaption(columnGroupDef.getCaption());
                for (SelectColumnDef item : columnGroupDef.getItems()) {
                    if (item == null) {
                        continue;
                    }
                    boolean hasRef = StringUtils.isNotEmpty(item.getRef());
                    if (hasRef && StringUtils.isEmpty(item.getAlias()) && StringUtils.isNotEmpty(item.getName())) {
                        item.setAlias(item.getName());
                    }
                    String ref = hasRef ? item.getRef() : item.getName();

                    DbDimension dimension = qm.findDimension(ref);
                    if (dimension != null) {
                        //维度，自动拆解成$id及$caption两列
                        addColumn(qm, group, ref + "$id", item, hasRef);
                        addColumn(qm, group, ref + "$caption", item, hasRef);
                    } else {
                        addColumn(qm, group, ref, item, hasRef);
                    }
                }
                columnGroups.add(group);
            }
            qm.setColumnGroups(columnGroups);
        }
    }

    private void addColumn(QueryModelSupport qm, QueryColumnGroup group, String name, SelectColumnDef item, boolean hasRef) {

        DbColumn jdbcColumn = qm.findJdbcColumnForCond(name, true);

        DbQueryColumn dbQueryColumn = new DbQueryColumnImpl(jdbcColumn, item.getName(), item.getCaption(), item.getAlias(), item.getField());
        dbQueryColumn.setHasRef(hasRef);
        dbQueryColumn.getDecorate(DbQueryColumnImpl.class).setUi(item.getUi());

        qm.addJdbcQueryColumn(dbQueryColumn);
        group.addJdbcColumn(dbQueryColumn);
    }

    private void fixJdbcQueryCond(QueryModelSupport qm, DbQueryConditionImpl jdbcQueryCond, DbColumn selectColumn) {
        if (selectColumn == null) {
            return;
        }
        if (StringUtils.isEmpty(jdbcQueryCond.getCaption())) {
            jdbcQueryCond.setCaption(selectColumn.getCaption());
        }
        DbDimensionColumn dbDimensionColumn = selectColumn.getDecorate(DbDimensionColumn.class);
        DbMeasureColumn jdbcMeasureColumn = selectColumn.getDecorate(DbMeasureColumn.class);
        DbPropertyColumn dbPropertyColumn = selectColumn.getDecorate(DbPropertyColumn.class);
        String autoQueryType = "=";
        DbQueryCondType autoType = null;

        if (dbDimensionColumn != null && dbPropertyColumn == null) {
            //仅当jdbcQueryCond中的jdbcColumn为foreignKey列时，才使用下拉查询

            DbDimension dimension = dbDimensionColumn.getDimension();
            QueryObject dimQueryObject = dimension.getQueryObject();
            jdbcQueryCond.setDimension(dimension);

            if (dimQueryObject != null && !dbDimensionColumn.isCaptionColumn()) {
                //有关联的维表,且不是caption列
                jdbcQueryCond.setType(DbQueryCondType.DIM);

            } else {
                DbDimensionType dimType = dimension.getType();
                Tuple2<String, DbQueryCondType> r = autoFix(jdbcQueryCond, dimType);
                autoQueryType = r.getT1();
                autoType = r.getT2();
            }


        } else if (jdbcMeasureColumn != null) {
            if (jdbcQueryCond.getType() == null) {
                // 度量类型不直接映射到查询条件类型，保持为 null 让后续逻辑处理
            }
            autoQueryType = "[]";
        } else if (dbPropertyColumn != null) {
            jdbcQueryCond.setProperty(dbPropertyColumn.getProperty());
            DbDimensionType dimType = DbDimensionType.fromColumnType(dbPropertyColumn.getProperty().getType());
            Tuple2<String, DbQueryCondType> r = autoFix(jdbcQueryCond, dimType);
            autoQueryType = r.getT1();
            autoType = r.getT2();
        }

        if (jdbcQueryCond.getType() == null) {
            jdbcQueryCond.setType(autoType);
        }
        if (StringUtils.isEmpty(jdbcQueryCond.getQueryType())) {
            jdbcQueryCond.setQueryType(autoQueryType);
        }
    }

    private Tuple2<String, DbQueryCondType> autoFix(DbQueryConditionImpl jdbcQueryCond, DbDimensionType type) {
        String autoQueryType = "=";
        DbQueryCondType autoType = null;
        if (type == DbDimensionType.DATETIME) {
            //日期维
            autoQueryType = "[)";
            autoType = DbQueryCondType.DATE_RANGE;
        } else if (type == DbDimensionType.DICT) {
            //字典表维
            autoType = DbQueryCondType.DICT;
        } else if (type == DbDimensionType.BOOL) {
            //boolean
            autoType = DbQueryCondType.BOOL;
        } else if (type == DbDimensionType.DOUBLE) {
            //boolean
            autoType = DbQueryCondType.DOUBLE;
        } else if (type == DbDimensionType.INTEGER) {
            //boolean
            autoType = DbQueryCondType.INTEGER;
        } else if (type == DbDimensionType.DAY) {
            //字典表维
            autoQueryType = "[]";
            autoType = DbQueryCondType.DAY_RANGE;
        } else {
            jdbcQueryCond.setType(DbQueryCondType.COMMON);
            autoType = DbQueryCondType.COMMON;
        }
        return new Tuple2<>(autoQueryType, autoType);
    }

    private DbQueryConditionImpl autoCreateJdbcQueryCond(QueryModelSupport qm, DbQueryColumn dbQueryColumn, DbColumn selectColumn) {


        DbQueryConditionImpl jdbcQueryCond = new DbQueryConditionImpl();
        jdbcQueryCond.setQueryModel(qm);
        jdbcQueryCond.setColumn(selectColumn);
        if (dbQueryColumn.isHasRef()) {
            jdbcQueryCond.setField(dbQueryColumn.getField());
            jdbcQueryCond.setName(dbQueryColumn.getName());
        } else {
            jdbcQueryCond.setField(selectColumn.getField());
            jdbcQueryCond.setName(selectColumn.getName());
        }


        fixJdbcQueryCond(qm, jdbcQueryCond, selectColumn);

        return jdbcQueryCond;
    }

    private void loadAccesses(QueryModelSupport qm, List<DbAccessDef> accessDefs) {
        if (accessDefs == null) {
            return;
        }
        for (DbAccessDef accessDef : accessDefs) {
            String dimension = accessDef.getDimension();
            String property = accessDef.getProperty();
            if (StringUtils.isNotEmpty(dimension)) {
//                RX.hasText(accessDef.getDimension(), "access必须定义维度");

                DbQueryDimension jdbcDimension = qm.findQueryDimension(dimension, false);
                if (jdbcDimension == null) {
                    //把该维度加到列表
                    jdbcDimension = qm.addQueryDimensionIfNotExist(qm.findDimension(dimension));
                }

                RX.notNull(jdbcDimension, "未能找到维度" + dimension);
                DbQueryAccessImpl impl = new DbQueryAccessImpl();
                BeanUtils.copyProperties(accessDef, impl);
                jdbcDimension.getDecorate(DbQueryDimensionImpl.class).setQueryAccess(impl);
            } else if (StringUtils.isNotEmpty(property)) {
                DbQueryProperty dbQueryProperty = qm.findQueryProperty(property, false);
                if (dbQueryProperty == null) {
                    //把该维度加到列表
                    dbQueryProperty = qm.addQueryPropertyIfNotExist(qm.findProperty(property, true));
                }

                RX.notNull(dbQueryProperty, "未能找到维度" + dimension);
                DbQueryAccessImpl impl = new DbQueryAccessImpl();
                BeanUtils.copyProperties(accessDef, impl);
                dbQueryProperty.getDecorate(DbQueryPropertyImpl.class).setQueryAccess(impl);

            } else {
                throw RX.throwAUserTip(DatasetMessages.querymodelAccessInvalid());
            }

//            qm.setDimAccess(jdbcDimension,);
        }
    }

    // ==================== 简称分配相关方法 ====================

    /**
     * 注册查询模型并分配简称
     *
     * @param modelName 模型全名
     * @param qm        查询模型实例
     */
    private void registerQueryModel(String modelName, QueryModelSupport qm) {
        // 先将模型全名加入已使用集合，防止简称与全名冲突
        usedAliases.add(modelName);

        // 分配简称
        String shortAlias = allocateShortAlias(modelName);
        qm.setShortAlias(shortAlias);

        // 注册映射
        name2JdbcQueryModel.put(modelName, qm);
        shortAlias2Name.put(shortAlias, modelName);

        log.debug("已为模型 {} 分配简称: {}", modelName, shortAlias);
    }

    /**
     * 为模型分配唯一简称
     *
     * <p>算法规则：
     * <ol>
     *   <li>去掉 QueryModel 后缀</li>
     *   <li>提取驼峰词的首字母组合（如 FactSales → FS）</li>
     *   <li>如果简称已存在或与模型全名冲突，追加数字后缀</li>
     * </ol>
     *
     * @param modelName 模型全名
     * @return 分配的唯一简称
     */
    private String allocateShortAlias(String modelName) {
        // 1. 去掉 QueryModel 后缀
        String baseName = modelName;
        if (baseName.endsWith("QueryModel")) {
            baseName = baseName.substring(0, baseName.length() - "QueryModel".length());
        } else if (baseName.endsWith("Model")) {
            baseName = baseName.substring(0, baseName.length() - "Model".length());
        }

        // 2. 提取驼峰词首字母
        String baseAlias = extractCamelCaseInitials(baseName);

        // 3. 确保唯一性
        String alias = baseAlias;
        int suffix = 2;
        while (usedAliases.contains(alias)) {
            alias = baseAlias + suffix;
            suffix++;
        }

        usedAliases.add(alias);
        return alias;
    }

    /**
     * 提取驼峰命名中各单词的首字母
     *
     * <p>示例：
     * <ul>
     *   <li>FactSales → FS</li>
     *   <li>DimProduct → DP</li>
     *   <li>FactInventorySnapshot → FIS</li>
     * </ul>
     *
     * @param name 驼峰命名的字符串
     * @return 首字母组合（大写）
     */
    private String extractCamelCaseInitials(String name) {
        StringBuilder initials = new StringBuilder();
        Matcher matcher = CAMEL_CASE_PATTERN.matcher(name);
        while (matcher.find()) {
            initials.append(matcher.group().charAt(0));
        }
        // 如果没有匹配到（如全小写），使用前两个字符
        if (initials.length() == 0 && name.length() > 0) {
            initials.append(name.substring(0, Math.min(2, name.length())).toUpperCase());
        }
        return initials.toString();
    }

}
