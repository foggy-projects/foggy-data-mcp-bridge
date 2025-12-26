package com.foggyframework.dataset.jdbc.model.def.query;

import com.foggyframework.dataset.jdbc.model.def.DbDefSupport;
import com.foggyframework.dataset.jdbc.model.def.access.DbAccessDef;
import com.foggyframework.dataset.jdbc.model.def.column.DbColumnGroupDef;
import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
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
