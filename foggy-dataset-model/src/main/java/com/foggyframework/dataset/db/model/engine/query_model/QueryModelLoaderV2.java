package com.foggyframework.dataset.db.model.engine.query_model;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.def.query.SelectColumnDef;
import com.foggyframework.dataset.db.model.i18n.DatasetMessages;
import com.foggyframework.dataset.db.model.proxy.*;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.persistence.criteria.JoinType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QM V2 加载器
 *
 * <p>支持新格式的 QM 文件解析，包括：
 * <ul>
 *   <li>{@link TableModelProxy} - 表模型代理</li>
 *   <li>{@link JoinBuilder} - JOIN 构建器</li>
 *   <li>{@link ColumnRef} - 字段引用</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * const fo = loadTableModel('FactOrderModel');
 * const fp = loadTableModel('FactPaymentModel');
 *
 * export const queryModel = {
 *     loader: 'v2',
 *     model: [fo, fo.leftJoin(fp).on(fo.orderId, fp.orderId)],
 *     columnGroups: [{ items: [{ ref: fo.orderId }] }]
 * };
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
@Slf4j
public class QueryModelLoaderV2 {

    private final TableModelLoaderManager tableModelLoaderManager;

    /**
     * 模型名称到 TableModelProxy 的映射（用于字段校验）
     */
    private final Map<String, TableModelProxy> modelProxies = new HashMap<>();

    /**
     * 模型名称到加载后的 TableModel 的映射
     */
    private final Map<String, TableModel> loadedModels = new HashMap<>();

    /**
     * 错误收集器
     */
    private final List<String> errors = new ArrayList<>();

    public QueryModelLoaderV2(TableModelLoaderManager tableModelLoaderManager) {
        this.tableModelLoaderManager = tableModelLoaderManager;
    }

    /**
     * 解析 V2 格式的 model 数组
     *
     * @param queryModelDef QM 定义
     * @param fsscript      脚本对象
     * @return JdbcModelDx 列表
     */
    public List<TableModel> parseModelList(DbQueryModelDef queryModelDef, Fsscript fsscript) {
        Object model = queryModelDef.getModel();

        if (!(model instanceof List)) {
            throw RX.throwAUserTip(DatasetMessages.querymodelModelMissing(queryModelDef.getName()));
        }

        List<?> modelList = (List<?>) model;
        List<TableModel> jdbcModelDxList = new ArrayList<>();
        int aliasCounter = 1;

        for (int i = 0; i < modelList.size(); i++) {
            Object item = modelList.get(i);

            if (item instanceof TableModelProxy proxy) {
                // 主表或普通表引用
                TableModel tm = loadTableModel(proxy.getModelName(), queryModelDef.getName());
                if (tm == null) continue;

                // 分配别名
                String alias = proxy.hasAlias() ? proxy.getAlias() : "t" + aliasCounter++;
                proxy.setAlias(alias);
                modelProxies.put(proxy.getModelName(), proxy);

                QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                        tm, tm.getIdColumn(), null, alias, JoinType.LEFT);
                jdbcModelDxList.add(dx);

            } else if (item instanceof JoinBuilder joinBuilder) {
                // JOIN 表达式
                TableModelProxy rightProxy = joinBuilder.getRight();
                TableModel tm = loadTableModel(rightProxy.getModelName(), queryModelDef.getName());
                if (tm == null) continue;

                // 分配别名
                String alias = rightProxy.hasAlias() ? rightProxy.getAlias() : "t" + aliasCounter++;
                rightProxy.setAlias(alias);
                modelProxies.put(rightProxy.getModelName(), rightProxy);

                // 更新 JoinBuilder 中左表的别名（从已注册的 proxy 获取）
                TableModelProxy leftProxy = joinBuilder.getLeft();
                if (!leftProxy.hasAlias()) {
                    TableModelProxy registeredLeft = modelProxies.get(leftProxy.getModelName());
                    if (registeredLeft != null) {
                        leftProxy.setAlias(registeredLeft.getAlias());
                    }
                }

                // 创建 FsscriptFunction 适配器
                JoinBuilderFunction onBuilder = new JoinBuilderFunction(joinBuilder);

                QueryModelSupport.JdbcModelDx dx = new QueryModelSupport.JdbcModelDx(
                        tm, tm.getIdColumn(), onBuilder, alias, joinBuilder.getJoinType());
                jdbcModelDxList.add(dx);

            } else {
                log.warn("QM [{}] model 数组中包含不支持的类型: {}", queryModelDef.getName(),
                        item == null ? "null" : item.getClass().getName());
            }
        }

        return jdbcModelDxList;
    }

    /**
     * 校验 columnGroups 中的 ColumnRef 引用
     *
     * @param queryModelDef QM 定义
     * @param qm            查询模型（用于查找列）
     */
    public void validateColumnGroups(DbQueryModelDef queryModelDef, QueryModelSupport qm) {
        if (queryModelDef.getColumnGroups() == null) return;

        for (int gi = 0; gi < queryModelDef.getColumnGroups().size(); gi++) {
            DbColumnGroupDef groupDef = queryModelDef.getColumnGroups().get(gi);
            if (groupDef.getItems() == null) continue;

            for (int ii = 0; ii < groupDef.getItems().size(); ii++) {
                SelectColumnDef item = groupDef.getItems().get(ii);
                if (item == null) continue;

                if (item.isColumnRefType()) {
                    ColumnRef columnRef = item.getRefAsColumnRef();
                    validateColumnRef(columnRef, queryModelDef.getName(),
                            String.format("columnGroups[%d].items[%d].ref", gi, ii), qm);
                }
            }
        }
    }

    /**
     * 校验 orders 中的 ColumnRef 引用
     *
     * @param queryModelDef QM 定义
     * @param qm            查询模型
     */
    public void validateOrders(DbQueryModelDef queryModelDef, QueryModelSupport qm) {
        if (queryModelDef.getOrders() == null) return;

        for (int i = 0; i < queryModelDef.getOrders().size(); i++) {
            OrderDef orderDef = queryModelDef.getOrders().get(i);
            // TODO: 如果 OrderDef 也支持 ColumnRef 类型的 ref，在这里校验
        }
    }

    /**
     * 校验字段引用的有效性
     *
     * @param columnRef     字段引用
     * @param qmName        QM 名称
     * @param location      引用位置
     * @param qm            查询模型
     */
    private void validateColumnRef(ColumnRef columnRef, String qmName, String location, QueryModelSupport qm) {
        String modelName = columnRef.getModelName();
        String columnName = columnRef.getFullRef();

        // 检查模型是否在 model 数组中声明
        if (!modelProxies.containsKey(modelName)) {
            addError(qmName, location,
                    String.format("字段引用的模型 '%s' 未在 model 数组中声明", modelName));
            return;
        }

        // 检查字段是否存在于模型中
        TableModel tm = loadedModels.get(modelName);
        if (tm != null) {
            // 尝试在 QM 中查找对应的列
            try {
                var column = qm.findJdbcColumnForCond(columnName, false);
                if (column == null) {
                    // 提供可用字段建议
                    String suggestion = getSuggestedColumns(tm);
                    addError(qmName, location,
                            String.format("字段 '%s' 在模型 '%s' 中不存在。可用字段: %s",
                                    columnName, modelName, suggestion));
                }
            } catch (Exception e) {
                log.debug("校验字段 {} 时发生异常: {}", columnName, e.getMessage());
            }
        }
    }

    /**
     * 获取模型的可用字段建议
     */
    private String getSuggestedColumns(TableModel tm) {
        List<String> columns = new ArrayList<>();

        // 添加属性
        if (tm.getProperties() != null) {
            tm.getProperties().forEach(p -> columns.add(p.getName()));
        }
        // 添加维度
        if (tm.getDimensions() != null) {
            tm.getDimensions().forEach(d -> columns.add(d.getName()));
        }
        // 添加度量
        if (tm.getMeasures() != null) {
            tm.getMeasures().forEach(m -> columns.add(m.getName()));
        }

        if (columns.size() > 10) {
            return columns.stream().limit(10).collect(Collectors.joining(", ")) + ", ...";
        }
        return String.join(", ", columns);
    }

    /**
     * 加载表模型
     */
    private TableModel loadTableModel(String modelName, String qmName) {
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

    /**
     * 添加错误
     */
    private void addError(String qmName, String location, String message) {
        errors.add(String.format("QM [%s] %s: %s", qmName, location, message));
    }

    /**
     * 检查是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 获取所有错误
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * 抛出收集的所有错误
     */
    public void throwIfHasErrors() {
        if (hasErrors()) {
            String errorMessage = String.join("\n  ", errors);
            throw RX.throwAUserTip("QM 加载失败:\n  " + errorMessage);
        }
    }

    /**
     * 获取已加载的模型代理映射
     */
    public Map<String, TableModelProxy> getModelProxies() {
        return modelProxies;
    }

    /**
     * 清理资源
     */
    public void clear() {
        modelProxies.clear();
        loadedModels.clear();
        errors.clear();
    }
}
