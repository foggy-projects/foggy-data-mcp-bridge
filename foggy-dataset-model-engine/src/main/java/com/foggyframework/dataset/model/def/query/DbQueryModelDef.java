package com.foggyframework.dataset.model.def.query;

import com.foggyframework.dataset.model.def.DbDefSupport;
import com.foggyframework.dataset.model.def.access.DbAccessDef;
import com.foggyframework.dataset.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.model.def.order.OrderDef;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.permission.ModelPermissionsDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.model.semantic.member.permission.QmMemberPermissionDef;
import com.foggyframework.dataset.model.proxy.TableModelProxy;
import com.foggyframework.dataset.model.spi.TableModel;
import lombok.Data;

import javax.sql.DataSource;
import java.util.List;

@Data
public class DbQueryModelDef extends DbDefSupport {

    DataSource dataSource;

    /**
     * 旧模型兼容字段。当前 QM 默认按 V2 结构加载，不再通过该字段选择加载器。
     */
    String loader;

    TableModelProxy model;

    /**
     * JOIN 关系定义（V2 格式）
     * <p>joins 数组直接映射到 JoinGraph.addEdge()
     * <p>每个元素应为 JoinBuilder，如: fo.leftJoin(fp).on(fo.orderId, fp.orderId)
     */
    List<Object> joins;

    /**
     * V2 构建器解析后的模型列表
     * <p>由 JDBC V2 Builder 解析并设置，供后续 Builder（如 MongoDB Builder）使用
     */
    List<TableModel> parsedModels;

    List<OrderDef> orders;

    List<DbColumnGroupDef> columnGroups;

    List<QueryConditionDef> conds;

    List<DbAccessDef> accesses;

    /** 成员权限配置列表，每项按 dimension 定位根维度，patch 覆盖 TM 默认值 */
    List<QmMemberPermissionDef> memberPermissions;

    /**
     * QM-level dynamic field permission narrowing layer.
     */
    FieldPermissionsDef fieldPermissions;

    /**
     * Query-model action authorization. Omitted/public declarations preserve
     * the existing open-model behavior.
     */
    ModelPermissionsDef modelPermissions;

    public void apply(QueryModelSupport queryModelSupport) {
        super.apply(queryModelSupport);
        if (modelPermissions != null) {
            modelPermissions.resolvedMode();
        }
        queryModelSupport.setFieldPermissions(fieldPermissions);
        queryModelSupport.setModelPermissions(modelPermissions);
    }

}
