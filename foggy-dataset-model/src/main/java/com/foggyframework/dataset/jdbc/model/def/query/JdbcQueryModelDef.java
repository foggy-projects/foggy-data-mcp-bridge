package com.foggyframework.dataset.jdbc.model.def.query;

import com.foggyframework.dataset.jdbc.model.def.JdbcDefSupport;
import com.foggyframework.dataset.jdbc.model.def.access.JdbcAccessDef;
import com.foggyframework.dataset.jdbc.model.def.column.JdbcColumnGroupDef;
import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
import lombok.Data;

import javax.sql.DataSource;
import java.util.List;

@Data
public class JdbcQueryModelDef extends JdbcDefSupport {

    DataSource dataSource;

    Object model;

    List<OrderDef> orders;

    List<JdbcColumnGroupDef> columnGroups;

    List<QueryConditionDef> conds;

    List<JdbcAccessDef> accesses;

}
