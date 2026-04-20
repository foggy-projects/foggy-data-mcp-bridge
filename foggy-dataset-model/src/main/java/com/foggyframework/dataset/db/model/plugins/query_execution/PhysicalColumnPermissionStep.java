package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.impl.query.DbQueryGroupColumnImpl;
import com.foggyframework.dataset.db.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.db.model.impl.utils.QueryObjectDelegate;
import com.foggyframework.dataset.db.model.impl.utils.TableQueryObject;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 物理列级权限检查步骤
 * <p>
 * 在 JdbcQuery 构建完成后、SQL 执行前，检查查询引用的物理列
 * 是否在 {@code deniedColumns} 黑名单中。
 * </p>
 * <p>
 * 此时所有 QM→物理列的解析、计算字段展开、维度 JOIN 已全部完成，
 * 权限检查变为简单的 schema.table.column 集合匹配。
 * </p>
 * <p>
 * 与 {@code FieldAccessPermissionStep}（QM 字段名白名单）并存互不干扰。
 * </p>
 *
 * @since 8.2.0
 */
@Slf4j
@Component
public class PhysicalColumnPermissionStep implements QueryExecutionStep {

    @Override
    public int order() {
        // 在 PreAggRewriteStep(1000) 和 L2CacheStep(900) 之前执行
        // 权限检查最先，避免对受限查询进行预聚合匹配或缓存查找
        return 1100;
    }

    @Override
    public int beforeExecute(QueryExecutionContext ctx) {
        ModelResultContext modelCtx = ctx.getModelResultContext();
        if (modelCtx == null) {
            return CONTINUE;
        }

        List<DeniedPhysicalColumn> denied = modelCtx.getDeniedColumns();
        if (denied == null || denied.isEmpty()) {
            return CONTINUE;
        }

        // 如果 QM 映射缓存可用，deniedColumns 已在 FieldAccessPermissionStep (beforeQuery)
        // 中通过 toDeniedQmFields() 转换并校验。此处作为后备仅在映射缓存不可用时执行。
        if (ctx.getQueryEngine() != null
                && ctx.getQueryEngine().getJdbcQueryModel() != null
                && ctx.getQueryEngine().getJdbcQueryModel().getPhysicalColumnMapping() != null) {
            return CONTINUE;
        }

        JdbcQuery jdbcQuery = ctx.getQueryEngine().getJdbcQuery();
        if (jdbcQuery == null) {
            return CONTINUE;
        }

        // 构建快速匹配集合
        Set<String> deniedSet = buildDeniedSet(denied);

        // 检查 SELECT 列
        if (jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn col : jdbcQuery.getSelect().getColumns()) {
                checkColumn(col, deniedSet);
            }
        }

        // 检查 ORDER BY 列
        if (jdbcQuery.getOrder() != null && jdbcQuery.getOrder().getOrders() != null) {
            for (DbQueryOrderColumnImpl orderCol : jdbcQuery.getOrder().getOrders()) {
                if (orderCol.getSelectColumn() != null) {
                    checkColumn(orderCol.getSelectColumn(), deniedSet);
                }
            }
        }

        // 检查 GROUP BY 列
        if (jdbcQuery.getGroup() != null && jdbcQuery.getGroup().getGroups() != null) {
            for (DbQueryGroupColumnImpl groupCol : jdbcQuery.getGroup().getGroups()) {
                if (groupCol.getAggColumn() != null) {
                    checkColumn(groupCol.getAggColumn(), deniedSet);
                }
            }
        }

        // WHERE / HAVING 条件在 JdbcQuery 中以 SQL 片段（SqlFragmentCond/ValueCond）存储，
        // 无法从结构中提取 DbColumn 引用。
        // 已知限制：slice 条件可引用不在 SELECT 中的物理列（如 WHERE profit_amount > 100
        // 而 SELECT 不含 profit_amount），此处无法拦截。
        // 安全缓解：与 FieldAccessPermissionStep 配合使用时，slice 字段已在 beforeQuery 阶段
        // 按 QM 字段名校验。仅使用 deniedColumns 时，WHERE 列检查依赖 FieldAccessPermissionStep。

        if (log.isDebugEnabled()) {
            log.debug("PhysicalColumnPermission check passed for model: {}", ctx.getModelName());
        }

        return CONTINUE;
    }

    /**
     * 检查单个 DbColumn 是否引用了受限物理列
     */
    private void checkColumn(DbColumn col, Set<String> deniedSet) {
        QueryObject qo = col.getQueryObject();

        // 解包 QueryObjectDelegate（装饰器模式），获取底层真实 QueryObject
        if (qo instanceof QueryObjectDelegate delegate) {
            qo = delegate.getDelegate();
        }

        if (!(qo instanceof TableQueryObject tqo)) {
            return; // SubQuery / View 等非物理表引用，跳过
        }

        String sqlColumnName = col.getSqlColumnName();
        if (sqlColumnName == null) {
            return; // 表达式列（如 COUNT(*)），无物理列名
        }

        // 检查 table.column（schema 无关匹配）
        String keyNoSchema = tqo.getTableName() + "." + sqlColumnName;
        if (deniedSet.contains(keyNoSchema)) {
            throw RX.throwB("物理列访问被拒绝: " + keyNoSchema + " 在受限列黑名单中");
        }

        // 检查 schema.table.column（精确匹配）
        if (tqo.getSchema() != null && !tqo.getSchema().isEmpty()) {
            String keyWithSchema = tqo.getSchema() + "." + keyNoSchema;
            if (deniedSet.contains(keyWithSchema)) {
                throw RX.throwB("物理列访问被拒绝: " + keyWithSchema + " 在受限列黑名单中");
            }
        }
    }

    /**
     * 构建快速匹配集合
     * <p>
     * 每个 DeniedPhysicalColumn 始终生成 {@code table.column} key（schema 无关匹配），
     * 若 schema 非 null 则额外生成 {@code schema.table.column} key（精确匹配）。
     * 这确保 denied 有 schema 时也能匹配无 schema 的查询（如 SQLite）。
     */
    private Set<String> buildDeniedSet(List<DeniedPhysicalColumn> denied) {
        Set<String> set = new HashSet<>(denied.size() * 2);
        for (DeniedPhysicalColumn d : denied) {
            if (d.getTable() == null || d.getColumn() == null) {
                continue;
            }
            // 始终添加 table.column（跨 schema 兼容）
            set.add(d.getTable() + "." + d.getColumn());
            // 有 schema 时额外添加精确 key
            if (d.getSchema() != null && !d.getSchema().isEmpty()) {
                set.add(d.getSchema() + "." + d.getTable() + "." + d.getColumn());
            }
        }
        return set;
    }
}
