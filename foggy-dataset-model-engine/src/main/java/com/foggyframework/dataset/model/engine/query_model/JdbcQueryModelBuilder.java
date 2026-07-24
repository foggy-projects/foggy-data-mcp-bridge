package com.foggyframework.dataset.model.engine.query_model;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.i18n.DatasetMessages;
import com.foggyframework.dataset.model.impl.model.AggregateJoinTableModel;
import com.foggyframework.dataset.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.model.interceptor.SqlLoggingInterceptor;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStepExecutor;
import com.foggyframework.dataset.model.proxy.*;
import com.foggyframework.dataset.model.spi.*;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.JoinType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;

/**
 * V2 格式的 QueryModel 构建器
 *
 * <p>支持两种 V2 语法：
 * <pre>
 * // 语法1：数组形式（兼容）
 * model: [fo, fo.leftJoin(fp).on(fo.orderId, fp.orderId)]
 *
 * // 语法2：分离形式（推荐）
 * model: fo,
 * joins: [fo.leftJoin(fp).on(fo.orderId, fp.orderId)]
 * </pre>
 *
 * <p>语法2更优雅，直接映射到 JoinGraph：
 * <ul>
 *   <li>{@code model} 对应 {@code JoinGraph.root}</li>
 *   <li>{@code joins} 数组中的每个 JoinBuilder 对应 {@code JoinGraph.addEdge()}</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JdbcQueryModelBuilder implements QueryModelBuilder, DetachedQueryModelBuilderFactory {

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private DataSource defaultDataSource;

    @Autowired(required = false)
    private SqlLoggingInterceptor sqlLoggingInterceptor;

    @Resource
    private QueryExecutionStepExecutor queryExecutionStepExecutor;

    /**
     * 模型名称到 TableModelProxy 的映射
     */
    private final ThreadLocal<Map<String, TableModelProxy>> modelProxiesLocal = ThreadLocal.withInitial(HashMap::new);

    /**
     * 错误收集器
     */
    private final ThreadLocal<List<String>> errorsLocal = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public QueryModelBuilder createDetachedQueryModelBuilder(
            TableModelLoaderManager tableModelLoaderManager,
            SystemBundlesContext systemBundlesContext,
            FileFsscriptLoader fileFsscriptLoader
    ) {
        JdbcQueryModelBuilder detached = new JdbcQueryModelBuilder();
        detached.tableModelLoaderManager = tableModelLoaderManager;
        detached.sqlFormulaService = sqlFormulaService;
        detached.defaultDataSource = defaultDataSource;
        detached.sqlLoggingInterceptor = sqlLoggingInterceptor;
        detached.queryExecutionStepExecutor = queryExecutionStepExecutor;
        return detached;
    }

    @Override
    public QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript) {
        log.debug("V2 构建器尝试构建 QM: {}", queryModelDef.getName());
        TableModelProxy model = queryModelDef.getModel();

        try {
            // 1. 解析 model 和 joins，获取模型列表
            List<TableModel> parsedModels = parseModelAndJoins(queryModelDef);

            // A QM is an all-or-nothing publication unit.  The parser deliberately
            // accumulates dependency errors so callers get one useful diagnostic,
            // but none of those errors may be downgraded to a partially usable QM.
            // In particular, a valid root TM must not hide a failed joined TM.
            throwIfHasErrors(queryModelDef.getName());

            if (parsedModels.isEmpty()) {
                throw RX.throwAUserTip(DatasetMessages.querymodelModelMissing(queryModelDef.getName()));
            }

            // 2. 检查主表模型类型，非 JDBC 模型则退出，交由其他 Builder 处理
            TableModel firstModel = parsedModels.get(0);
            if (firstModel instanceof QueryModelSupport.JdbcModelDx dx) {
                firstModel = dx.getDelegate();
            }
            if (!isJdbcModel(firstModel)) {
                log.debug("QM [{}] 非 JDBC 模型 (modelType={}), 交由其他 Builder 处理",
                        queryModelDef.getName(),
                        firstModel.getModelType());
                // 将解析好的模型列表存储到 queryModelDef 中，供其他 Builder 使用
                queryModelDef.setParsedModels(parsedModels);
                return null;
            }

            // 3. 构建 JDBC QueryModel
            return buildJdbcQueryModel(queryModelDef, fsscript, parsedModels);
        } finally {
            clearThreadLocalData();
        }
    }

    /**
     * 构建 JDBC QueryModel
     */
    private QueryModelSupport buildJdbcQueryModel(DbQueryModelDef queryModelDef, Fsscript fsscript, List<TableModel> jdbcModelDxList) {
        // 验证数据源一致性
        DataSource ds = resolveDataSource(queryModelDef, jdbcModelDxList);

        // 创建 QueryModel
        JdbcQueryModelImpl qm = new JdbcQueryModelImpl(jdbcModelDxList, fsscript, sqlFormulaService, ds);

        if (sqlLoggingInterceptor != null) {
            qm.setSqlLoggingInterceptor(sqlLoggingInterceptor);
        }

        // 注入查询执行步骤执行器
        qm.setQueryExecutionStepExecutor(queryExecutionStepExecutor);

        queryModelDef.apply(qm);

        log.debug("V2 构建器成功构建 JDBC QM: {}", queryModelDef.getName());
        return qm;
    }

    /**
     * 解析 model 和 joins 配置
     *
     * <p>V2 格式：
     * <pre>
     * const fo = loadTableModel('FactOrder');
     * const fp = loadTableModel('FactPayment');
     *
     * model: fo,                              // 主表（TableModelProxy）
     * joins: [fo.leftJoin(fp).on(...), ...]   // JOIN 关系数组（可选）
     * </pre>
     */
    private List<TableModel> parseModelAndJoins(DbQueryModelDef queryModelDef) {
        TableModelProxy model = queryModelDef.getModel();
        List<Object> joins = queryModelDef.getJoins();
        String qmName = queryModelDef.getName();

        List<TableModel> result = new ArrayList<>();
        int aliasCounter = 1;

        // 解析主表
        aliasCounter = parseTableModelProxy(model, result, aliasCounter, qmName, true);

        // 解析 joins 数组
        if (joins != null) {
            for (Object joinItem : joins) {
                aliasCounter = parseJoinItem(joinItem, result, aliasCounter, qmName);
            }
        }

        return result;
    }

    /**
     * 解析 TableModelProxy
     */
    private int parseTableModelProxy(TableModelProxy proxy, List<TableModel> result,
                                      int aliasCounter, String qmName, boolean isRoot) {
        TableModel tm = loadTableModel(proxy.getModelName(), qmName);
        if (tm == null) return aliasCounter;

        // 分配别名
        String alias = proxy.hasAlias() ? proxy.getAlias() : "t" + aliasCounter++;
        ensureAliasAvailable(result, alias, qmName);
        proxy.setAlias(alias);
        getModelProxies().put(proxyKey(proxy), proxy);

        QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                tm, tm.getIdColumn(), null, alias, JoinType.LEFT, isRoot);
        result.add(dx);

        return aliasCounter;
    }

    /**
     * 解析 JOIN 项（可以是 JoinBuilder 或其他）
     */
    private int parseJoinItem(Object joinItem, List<TableModel> result,
                               int aliasCounter, String qmName) {
        if (joinItem instanceof AggregateJoinBuilder aggregateJoinBuilder) {
            return parseAggregateJoinBuilder(aggregateJoinBuilder, result, aliasCounter, qmName);
        }
        if (joinItem instanceof JoinBuilder joinBuilder) {
            return parseJoinBuilder(joinBuilder, result, aliasCounter, qmName);
        }
        String typeName = joinItem == null ? "null" : joinItem.getClass().getName();
        addError(qmName, "joins", "不支持的 JOIN 类型: " + typeName);
        log.warn("QM [{}] joins 数组中包含不支持的类型: {}", qmName, typeName);
        return aliasCounter;
    }

    /**
     * 解析 aggregate join。
     *
     * <p>右表先被包装为一个运行时合成的聚合子查询模型，再复用现有 JoinGraph + onBuilder 机制。
     */
    private int parseAggregateJoinBuilder(AggregateJoinBuilder aggregateJoinBuilder, List<TableModel> result,
                                          int aliasCounter, String qmName) {
        TableModelProxy rightProxy = aggregateJoinBuilder.getRight();
        TableModel sourceTableModel = loadTableModel(rightProxy.getModelName(), qmName);
        if (sourceTableModel == null) return aliasCounter;

        // 分配别名
        String alias = rightProxy.hasAlias() ? rightProxy.getAlias() : "t" + aliasCounter++;
        ensureAliasAvailable(result, alias, qmName);
        rightProxy.setAlias(alias);
        getModelProxies().put(proxyKey(rightProxy), rightProxy);

        // 更新左表别名（从已注册的 proxy 获取）
        TableModelProxy leftProxy = aggregateJoinBuilder.getLeft();
        if (!leftProxy.hasAlias()) {
            TableModelProxy registeredLeft = getModelProxies().get(proxyKey(leftProxy));
            if (registeredLeft != null) {
                leftProxy.setAlias(registeredLeft.getAlias());
            }
        }

        TableModel aggregateTableModel = AggregateJoinTableModel.from(sourceTableModel, aggregateJoinBuilder, result);
        JoinBuilderFunction onBuilder = new JoinBuilderFunction(aggregateJoinBuilder);

        QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                aggregateTableModel, aggregateTableModel.getIdColumn(), onBuilder, alias, aggregateJoinBuilder.getJoinType());
        TableModel dependsOn = findParsedModelByAlias(result, leftProxy.getAlias());
        if (dependsOn != null) {
            dx.addDependsOn(dependsOn);
        } else {
            addError(qmName, "aggregateJoin('" + alias + "')",
                    "找不到左侧依赖模型: " + leftProxy.getModelName());
        }
        result.add(dx);

        return aliasCounter;
    }

    /**
     * 解析 JoinBuilder
     */
    private int parseJoinBuilder(JoinBuilder joinBuilder, List<TableModel> result,
                                  int aliasCounter, String qmName) {
        TableModelProxy rightProxy = joinBuilder.getRight();
        TableModel sourceTableModel = loadTableModel(rightProxy.getModelName(), qmName);
        if (sourceTableModel == null) return aliasCounter;

        // 分配别名
        String alias = rightProxy.hasAlias() ? rightProxy.getAlias() : "t" + aliasCounter++;
        ensureAliasAvailable(result, alias, qmName);
        rightProxy.setAlias(alias);
        getModelProxies().put(proxyKey(rightProxy), rightProxy);

        // 更新左表别名（从已注册的 proxy 获取）
        TableModelProxy leftProxy = joinBuilder.getLeft();
        if (!leftProxy.hasAlias()) {
            TableModelProxy registeredLeft = getModelProxies().get(proxyKey(leftProxy));
            if (registeredLeft != null) {
                leftProxy.setAlias(registeredLeft.getAlias());
            }
        }

        TableModel tm = sourceTableModel;
        if (rightProxy instanceof AggregateRelationProxy aggregateRelationProxy) {
            tm = AggregateJoinTableModel.from(sourceTableModel, aggregateRelationProxy, joinBuilder, result);
        }

        // 创建 onBuilder 适配器
        JoinBuilderFunction onBuilder = new JoinBuilderFunction(joinBuilder);

        QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                tm, tm.getIdColumn(), onBuilder, alias, joinBuilder.getJoinType());
        TableModel dependsOn = findParsedModelByAlias(result, leftProxy.getAlias());
        if (dependsOn != null) {
            dx.addDependsOn(dependsOn);
        } else {
            addError(qmName, "join('" + alias + "')",
                    "找不到左侧依赖模型: " + leftProxy.getModelName());
        }
        result.add(dx);

        return aliasCounter;
    }

    /**
     * 解析数据源
     */
    private DataSource resolveDataSource(DbQueryModelDef queryModelDef, List<TableModel> jdbcModelDxList) {
        DataSource ds = queryModelDef.getDataSource();

        if (ds == null) {
            for (TableModel jdbcModel : jdbcModelDxList) {
                DbTableModelImpl tm = jdbcModel.getDecorate(DbTableModelImpl.class);
                if (tm != null && tm.getDataSource() != null) {
                    if (ds == null) {
                        ds = tm.getDataSource();
                    } else if (ds != tm.getDataSource()) {
                        throw RX.throwAUserTip("不同数据源的TM不能配置在一起");
                    }
                }
            }
        }

        return ds != null ? ds : defaultDataSource;
    }

    /**
     * 加载表模型
     */
    private TableModel loadTableModel(String modelName, String qmName) {

        try {
            // 使用NamespaceContext获取当前线程的namespace
            String namespace = com.foggyframework.dataset.model.spi.NamespaceContext.getNamespace();
            TableModel tm = tableModelLoaderManager.load(modelName, namespace);
            return tm;
        } catch (Exception e) {
            String namespace = com.foggyframework.dataset.model.spi.NamespaceContext.getNamespace();
            addError(qmName, "loadTableModel('" + modelName + "')",
                    String.format("表模型 '%s' 加载失败 (namespace=%s): %s",
                            modelName, namespace != null ? namespace : "default", e.getMessage()));
            log.error("QM [{}] 加载表模型 '{}' 失败: {}", qmName, modelName, e.getMessage());
            return null;
        }
    }

    private void ensureAliasAvailable(List<TableModel> result, String alias, String qmName) {
        for (TableModel model : result) {
            if (StringUtils.equals(model.getAlias(), alias)) {
                throw RX.throwAUserTip(String.format("QM [%s] 表别名重复: %s", qmName, alias));
            }
        }
    }

    private TableModel findParsedModelByAlias(List<TableModel> result, String alias) {
        if (StringUtils.isEmpty(alias)) {
            return null;
        }
        for (TableModel model : result) {
            if (StringUtils.equals(model.getAlias(), alias)) {
                return model;
            }
        }
        return null;
    }

    private String proxyKey(TableModelProxy proxy) {
        if (proxy == null) {
            return "";
        }
        String alias = proxy.hasAlias() ? proxy.getAlias() : "";
        return proxy.getModelName() + "\u0000" + alias;
    }

    // ==================== ThreadLocal 管理 ====================

    private Map<String, TableModelProxy> getModelProxies() {
        return modelProxiesLocal.get();
    }

    private List<String> getErrors() {
        return errorsLocal.get();
    }

    private void addError(String qmName, String location, String message) {
        getErrors().add(String.format("QM [%s] %s: %s", qmName, location, message));
    }

    private void throwIfHasErrors(String qmName) {
        List<String> errors = getErrors();
        if (!errors.isEmpty()) {
            String errorMessage = String.join("\n  ", errors);
            throw RX.throwAUserTip("QM [" + qmName + "] 加载失败:\n  " + errorMessage);
        }
    }

    private void clearThreadLocalData() {
        modelProxiesLocal.remove();
        errorsLocal.remove();
    }

    /**
     * 获取已加载的模型代理（用于字段校验）
     */
    public Map<String, TableModelProxy> getModelProxiesSnapshot() {
        return new HashMap<>(getModelProxies());
    }

    /**
     * 检查 TableModel 是否是 JDBC 模型
     *
     * <p>通过 modelType 判断，JDBC 模型的 modelType 为 null 或 jdbc
     *
     * @param model TableModel
     * @return true 如果是 JDBC 模型
     */
    private boolean isJdbcModel(TableModel model) {
        if (model == null) return false;
        DbModelType modelType = model.getModelType();
        // modelType 为 null 或 jdbc 表示 JDBC 模型
        return modelType == null || modelType == DbModelType.jdbc;
    }
}
