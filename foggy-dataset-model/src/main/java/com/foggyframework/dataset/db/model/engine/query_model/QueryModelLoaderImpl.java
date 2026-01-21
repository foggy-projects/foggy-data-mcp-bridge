package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.bundle.Bundle;
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
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
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

    @Resource
    private DataSource defaultDataSource;

    private DbModelFileChangeHandler fileChangeHandler;

    /**
     * 命名空间级别的缓存结构
     * Key: namespace (空字符串表示默认命名空间)
     * Value: NamespaceCache（包含该命名空间下的所有模型缓存）
     */
    private Map<String, NamespaceCache> namespaceCaches = new HashMap<>();


    private List<QueryModelBuilder> queryModelBuilders;
    /**
     * 驼峰命名模式，用于提取大写字母
     */
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("[A-Z][a-z]*");

    /**
     * 命名空间缓存内部类
     * 封装单个命名空间下的所有缓存数据
     */
    private static class NamespaceCache {
        /**
         * 模型名称到模型实例的映射
         */
        Map<String, QueryModel> name2QueryModel = new HashMap<>();

        /**
         * 简称到模型名称的映射
         */
        Map<String, String> shortAlias2Name = new HashMap<>();

        /**
         * 已使用的简称集合（包括模型全名）
         */
        Set<String> usedAliases = new HashSet<>();

        void clear() {
            name2QueryModel.clear();
            shortAlias2Name.clear();
            usedAliases.clear();
        }
    }

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
        namespaceCaches.clear();
        log.debug("已清除所有命名空间的QueryModel缓存");
    }

    @Override
    public void clearByNamespace(String namespace) {
        String normalizedNs = normalizeNamespace(namespace);
        NamespaceCache removed = namespaceCaches.remove(normalizedNs);
        if (removed != null) {
            log.info("已清除命名空间 [{}] 的QueryModel缓存，包含 {} 个模型",
                    normalizedNs.isEmpty() ? "默认" : normalizedNs,
                    removed.name2QueryModel.size());
        } else {
            log.debug("命名空间 [{}] 的缓存不存在，无需清除",
                    normalizedNs.isEmpty() ? "默认" : normalizedNs);
        }
    }

    /**
     * 标准化命名空间（null或空字符串都视为默认命名空间）
     */
    private String normalizeNamespace(String namespace) {
        return (namespace == null || namespace.trim().isEmpty()) ? "" : namespace.trim();
    }

    /**
     * 获取或创建命名空间缓存
     */
    private NamespaceCache getOrCreateCache(String namespace) {
        String normalizedNs = normalizeNamespace(namespace);
        return namespaceCaches.computeIfAbsent(normalizedNs, k -> new NamespaceCache());
    }

    /**
     * 在执行查询前，我们需要先获取查询模型
     *
     * <p>支持通过模型全名或简称查询（从默认命名空间）
     *
     * @param queryModelNameOrAlias 模型名称或简称
     * @return 查询模型
     */
    @Override
    public QueryModel getJdbcQueryModel(String queryModelNameOrAlias) {
        return getJdbcQueryModel(queryModelNameOrAlias, "");
    }

    /**
     * 在执行查询前，我们需要先获取查询模型
     *
     * <p>支持通过模型全名或简称查询（从指定命名空间）
     *
     * @param queryModelNameOrAlias 模型名称或简称
     * @param namespace             命名空间（null或空字符串表示默认命名空间）
     * @return 查询模型
     */
    @Override
    public QueryModel getJdbcQueryModel(String queryModelNameOrAlias, String namespace) {
        String normalizedNs = normalizeNamespace(namespace);
        NamespaceCache cache = getOrCreateCache(normalizedNs);

        // 1. 先尝试通过全名查找
        QueryModel tm = cache.name2QueryModel.get(queryModelNameOrAlias);
        if (tm != null) {
            return tm;
        }

        // 2. 尝试通过简称查找
        String fullName = cache.shortAlias2Name.get(queryModelNameOrAlias);
        if (fullName != null) {
            return cache.name2QueryModel.get(fullName);
        }

        // 3. 加载新模型（此时 queryModelNameOrAlias 应该是全名）
        Fsscript fsscript = findFsscriptWithNamespace(queryModelNameOrAlias, normalizedNs, "qm");
        ExpEvaluator ee = evalQmScript(fsscript);
        Object queryModel = ee.getExportObject("queryModel");
        DbQueryModelDef queryModelDef = FsscriptConversionService.getSharedInstance().convert(queryModel, DbQueryModelDef.class);

        tm = loadJdbcQueryModel(ee, fsscript, queryModelDef);
        registerQueryModel(queryModelNameOrAlias, (QueryModelSupport) tm, normalizedNs);
        return tm;
    }

    /**
     * 在指定命名空间中查找FSScript
     */
    private Fsscript findFsscriptWithNamespace(String modelName, String namespace, String suffix) {
        String fileName = modelName + "." + suffix;
        BundleResource resource = systemBundlesContext.findResourceByName(fileName, namespace, true);
        return fileFsscriptLoader.findLoadFsscript(resource);
    }

    @Override
    public QueryModel loadJdbcQueryModel(BundleResource bundleResource) {
        // 从BundleResource中提取namespace
        String namespace = getNamespaceFromBundleResource(bundleResource);

        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource);
        ExpEvaluator ee = evalQmScript(fsscript);
        Object queryModel = ee.getExportObject("queryModel");
        DbQueryModelDef queryModelDef = FsscriptConversionService.getSharedInstance().convert(queryModel, DbQueryModelDef.class);
        try {
            QueryModelSupport qm = loadJdbcQueryModel(ee, fsscript, queryModelDef);
            // 注册模型并分配简称（使用提取的namespace）
            String modelName = qm.getName();
            NamespaceCache cache = getOrCreateCache(namespace);
            if (!cache.name2QueryModel.containsKey(modelName)) {
                registerQueryModel(modelName, qm, namespace);
            }
            return qm;
        } catch (Throwable t) {
            log.error(String.format("加载%s时出现异常", bundleResource));
            throw ErrorUtils.toRuntimeException(t);
        }
    }

    /**
     * 从BundleResource中提取namespace
     */
    private String getNamespaceFromBundleResource(BundleResource bundleResource) {
        if (bundleResource == null || bundleResource.getBundle() == null) {
            return "";
        }

        Bundle bundle = bundleResource.getBundle();
        com.foggyframework.core.bundle.BundleDefinition bundleDef = bundle.getDefinition();

        if (bundleDef == null) {
            return "";
        }

        String namespace = bundleDef.getNamespace();
        return namespace != null ? namespace : "";
    }

    /**
     * 执行 QM 脚本，并注入 V2 内置函数
     *
     * @param fsscript QM 脚本
     * @return 执行后的 ExpEvaluator
     */
    private ExpEvaluator evalQmScript(Fsscript fsscript) {
        ExpEvaluator ee = fsscript.newInstance(systemBundlesContext.getApplicationContext());
        // 注入 loadTableModel 内置函数（支持 V2 格式）
        ee.getCurrentFsscriptClosure().setVar("loadTableModel",
                com.foggyframework.dataset.db.model.proxy.LoadTableModelFunction.getInstance());
        fsscript.eval(ee);
        return ee;
    }

    private QueryModelSupport loadJdbcQueryModel(ExpEvaluator ee, Fsscript fsscript, DbQueryModelDef queryModelDef) {
        if (queryModelDef == null) {
            throw RX.throwAUserTip(DatasetMessages.querymodelExportMissing(fsscript.getPath()));
        }
        if (StringUtils.isEmpty(queryModelDef.getModel())) {
            throw RX.throwAUserTip(DatasetMessages.querymodelModelMissing(queryModelDef.getName()));
        }

        /**
         * 构建JdbcQueryModelImpl
         */
        QueryModelSupport qm = null;
        log.debug("开始遍历 QueryModelBuilder，共 {} 个", queryModelBuilders.size());
        for (QueryModelBuilder queryModelBuilder : queryModelBuilders) {
            log.debug("尝试 Builder: {}", queryModelBuilder.getClass().getName());
            qm = queryModelBuilder.build(queryModelDef, fsscript);
            if (qm != null) {
                log.debug("Builder {} 成功构建 QM: {}", queryModelBuilder.getClass().getSimpleName(), qm.getClass().getName());
                break;
            } else {
                log.debug("Builder {} 返回 null", queryModelBuilder.getClass().getSimpleName());
            }
        }
        if (qm == null) {
            throw RX.throwAUserTip("无法找到对应的QueryModelBuilder");
        }

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
                    condColumnName = dimensionColumn.getDimension().getForeignKeyDbColumn().getName();
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
                qm.addOrder(qm.findJdbcQueryColumnByName(d.getName(), true).getSelectColumn(), d);
            }
        }
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
                    // V2 格式：ref 可能是 ColumnRef 对象
                    // aliasRef: 使用 _ 分隔，用于列名/别名和列查找
                    String aliasRef = item.getRefAsString();
                    // lookupRef: 使用 . 分隔，用于在 TableModel 中查找维度
                    String lookupRef = item.getRefForLookup();
                    boolean hasRef = StringUtils.isNotEmpty(aliasRef);

                    // 如果有 ref 且没有显式指定 name/alias，使用 aliasRef 作为默认值
                    if (hasRef) {
                        if (StringUtils.isEmpty(item.getName())) {
                            item.setName(aliasRef);
                        }
                        if (StringUtils.isEmpty(item.getAlias())) {
                            item.setAlias(aliasRef);
                        }
                    }

                    // 使用 lookupRef 进行维度查找（dot格式支持嵌套路径）
                    String dimRef = hasRef ? lookupRef : item.getName();
                    // 使用 aliasRef 作为列名和列查找（使用 _ 分隔，因为列索引用alias格式）
                    String columnName = hasRef ? aliasRef : item.getName();

                    DbDimension dimension = qm.findDimension(dimRef);
                    if (dimension != null) {
                        //维度，自动拆解成$id及$caption两列
                        addColumn(qm, group, columnName + "$id", item, hasRef);
                        addColumn(qm, group, columnName + "$caption", item, hasRef);
                    } else {
                        addColumn(qm, group, columnName, item, hasRef);
                    }
                }
                columnGroups.add(group);
            }
            qm.setColumnGroups(columnGroups);
        }
    }

    private void addColumn(QueryModelSupport qm, QueryColumnGroup group, String columnName, SelectColumnDef item, boolean hasRef) {
        // columnName 使用 alias 格式（_ 分隔），因为列在 TableModel 中以 alias 格式索引
        DbColumn jdbcColumn = qm.findJdbcColumnForCond(columnName, true);

        DbQueryColumn dbQueryColumn = new DbQueryColumnImpl(jdbcColumn, columnName, item.getCaption(), item.getAlias(), item.getField());
        dbQueryColumn.setHasRef(hasRef);

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
            // 简化后：直接收集 queryBuilder，不再强制绑定到 dimension/property
            if (accessDef.getQueryBuilder() != null) {
                qm.getAccessBuilders().add(accessDef.getQueryBuilder());
            }
        }
    }

    // ==================== 简称分配相关方法 ====================

    /**
     * 注册查询模型并分配简称（在指定命名空间中）
     *
     * @param modelName 模型全名
     * @param qm        查询模型实例
     * @param namespace 命名空间
     */
    private void registerQueryModel(String modelName, QueryModelSupport qm, String namespace) {
        NamespaceCache cache = getOrCreateCache(namespace);

        // 先将模型全名加入已使用集合，防止简称与全名冲突
        cache.usedAliases.add(modelName);

        // 分配简称（在namespace范围内）
        String shortAlias = allocateShortAlias(modelName, cache);
        qm.setShortAlias(shortAlias);

        // 注册映射
        cache.name2QueryModel.put(modelName, qm);
        cache.shortAlias2Name.put(shortAlias, modelName);

        log.debug("已为模型 {} 分配简称: {} (namespace: {})", modelName, shortAlias,
                namespace.isEmpty() ? "默认" : namespace);
    }

    /**
     * 为模型分配唯一简称（在指定命名空间范围内）
     *
     * <p>算法规则：
     * <ol>
     *   <li>去掉 QueryModel 后缀</li>
     *   <li>提取驼峰词的首字母组合（如 FactSales → FS）</li>
     *   <li>如果简称已存在或与模型全名冲突，追加数字后缀</li>
     * </ol>
     *
     * @param modelName 模型全名
     * @param cache     命名空间缓存
     * @return 分配的唯一简称
     */
    private String allocateShortAlias(String modelName, NamespaceCache cache) {
        // 1. 去掉 QueryModel 后缀
        String baseName = modelName;
        if (baseName.endsWith("QueryModel")) {
            baseName = baseName.substring(0, baseName.length() - "QueryModel".length());
        } else if (baseName.endsWith("Model")) {
            baseName = baseName.substring(0, baseName.length() - "Model".length());
        }

        // 2. 提取驼峰词首字母
        String baseAlias = extractCamelCaseInitials(baseName);

        // 3. 确保唯一性（在namespace范围内）
        String alias = baseAlias;
        int suffix = 2;
        while (cache.usedAliases.contains(alias)) {
            alias = baseAlias + suffix;
            suffix++;
        }

        cache.usedAliases.add(alias);
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
