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

    Object model;

    List<OrderDef> orders;

    List<DbColumnGroupDef> columnGroups;

    List<QueryConditionDef> conds;

    List<DbAccessDef> accesses;

}
