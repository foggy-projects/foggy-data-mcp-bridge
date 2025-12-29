package com.foggyframework.dataset.db.model.def.query;

import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.def.access.DbAccessDef;
import com.foggyframework.dataset.db.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.db.model.def.order.OrderDef;
import lombok.Data;

import javax.sql.DataSource;
import java.util.List;

@Data
public class DbQueryModelDef extends DbDefSupport {

    DataSource dataSource;

    /**
     * 加载器版本
     * <ul>
     *   <li>{@code null} 或 {@code "v1"} - 使用原有加载逻辑</li>
     *   <li>{@code "v2"} - 使用 V2 加载器，支持 TableModelProxy 和 ColumnRef</li>
     * </ul>
     */
    String loader;

    Object model;

    /**
     * JOIN 关系定义（V2 格式）
     * <p>当使用 V2 加载器时，joins 数组直接映射到 JoinGraph.addEdge()
     * <p>每个元素应为 JoinBuilder，如: fo.leftJoin(fp).on(fo.orderId, fp.orderId)
     */
    List<Object> joins;

    List<OrderDef> orders;

    List<DbColumnGroupDef> columnGroups;

    List<QueryConditionDef> conds;

    List<DbAccessDef> accesses;

}
