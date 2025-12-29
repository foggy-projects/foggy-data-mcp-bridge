package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.impl.model.DbTableModelImpl;
import com.foggyframework.dataset.db.model.interceptor.SqlLoggingInterceptor;
import com.foggyframework.dataset.db.model.proxy.*;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
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
@Order(Ordered.HIGHEST_PRECEDENCE) // V2 优先处理
public class JdbcQueryModelBuilderV2 implements QueryModelBuilder {

    @Resource
    private TableModelLoaderManager tableModelLoaderManager;

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private DataSource defaultDataSource;

    @Autowired(required = false)
    private SqlLoggingInterceptor sqlLoggingInterceptor;

    /**
     * 模型名称到 TableModelProxy 的映射
     */
    private final ThreadLocal<Map<String, TableModelProxy>> modelProxiesLocal = ThreadLocal.withInitial(HashMap::new);

    /**
     * 模型名称到加载后的 TableModel 的映射
     */
    private final ThreadLocal<Map<String, TableModel>> loadedModelsLocal = ThreadLocal.withInitial(HashMap::new);

    /**
     * 错误收集器
     */
    private final ThreadLocal<List<String>> errorsLocal = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript) {
        log.debug("V2 构建器尝试构建 QM: {}", queryModelDef.getName());
        TableModelProxy model = queryModelDef.getModel();

        try {
            // 1. 解析 model 和 joins，获取模型列表
            List<TableModel> parsedModels = parseModelAndJoins(queryModelDef);

            if (parsedModels.isEmpty()) {
                throwIfHasErrors(queryModelDef.getName());
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
        proxy.setAlias(alias);
        getModelProxies().put(proxy.getModelName(), proxy);

        QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                tm, tm.getIdColumn(), null, alias, JoinType.LEFT);
        result.add(dx);

        return aliasCounter;
    }

    /**
     * 解析 JOIN 项（可以是 JoinBuilder 或其他）
     */
    private int parseJoinItem(Object joinItem, List<TableModel> result,
                               int aliasCounter, String qmName) {
        if (joinItem instanceof JoinBuilder joinBuilder) {
            return parseJoinBuilder(joinBuilder, result, aliasCounter, qmName);
        }
        if (joinItem != null) {
            log.warn("QM [{}] joins 数组中包含不支持的类型: {}", qmName, joinItem.getClass().getName());
        }
        return aliasCounter;
    }

    /**
     * 解析 JoinBuilder
     */
    private int parseJoinBuilder(JoinBuilder joinBuilder, List<TableModel> result,
                                  int aliasCounter, String qmName) {
        TableModelProxy rightProxy = joinBuilder.getRight();
        TableModel tm = loadTableModel(rightProxy.getModelName(), qmName);
        if (tm == null) return aliasCounter;

        // 分配别名
        String alias = rightProxy.hasAlias() ? rightProxy.getAlias() : "t" + aliasCounter++;
        rightProxy.setAlias(alias);
        getModelProxies().put(rightProxy.getModelName(), rightProxy);

        // 更新左表别名（从已注册的 proxy 获取）
        TableModelProxy leftProxy = joinBuilder.getLeft();
        if (!leftProxy.hasAlias()) {
            TableModelProxy registeredLeft = getModelProxies().get(leftProxy.getModelName());
            if (registeredLeft != null) {
                leftProxy.setAlias(registeredLeft.getAlias());
            }
        }

        // 创建 onBuilder 适配器
        JoinBuilderFunction onBuilder = new JoinBuilderFunction(joinBuilder);

        QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                tm, tm.getIdColumn(), onBuilder, alias, joinBuilder.getJoinType());
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
        Map<String, TableModel> loadedModels = getLoadedModels();
        if (loadedModels.containsKey(modelName)) {
            return loadedModels.get(modelName);
        }

        try {
            TableModel tm = tableModelLoaderManager.load(modelName);
            loadedModels.put(modelName, tm);
            return tm;
        } catch (Exception e) {
            addError(qmName, "loadTableModel('" + modelName + "')",
                    String.format("表模型 '%s' 加载失败: %s", modelName, e.getMessage()));
            return null;
        }
    }

    // ==================== ThreadLocal 管理 ====================

    private Map<String, TableModelProxy> getModelProxies() {
        return modelProxiesLocal.get();
    }

    private Map<String, TableModel> getLoadedModels() {
        return loadedModelsLocal.get();
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
        loadedModelsLocal.remove();
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
