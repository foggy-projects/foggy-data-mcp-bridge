package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
import com.foggyframework.dataset.jdbc.model.spi.DbColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JdbcQueryOrderColumnImpl  {

    DbColumn selectColumn;

    String order;

    boolean nullLast;

    boolean nullFirst;

    public JdbcQueryOrderColumnImpl(DbColumn selectColumn, String order) {
        this.selectColumn = selectColumn;
        this.order = order;
    }
    public JdbcQueryOrderColumnImpl(DbColumn selectColumn, OrderDef def) {
        this.selectColumn = selectColumn;
        this.order = def.getOrder();
        this.nullFirst = def.isNullFirst();
        this.nullLast = def.isNullLast();
    }
}
