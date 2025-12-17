package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.dataset.jdbc.model.def.order.OrderDef;
import com.foggyframework.dataset.jdbc.model.spi.JdbcColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JdbcQueryOrderColumnImpl  {

    JdbcColumn selectColumn;

    String order;

    boolean nullLast;

    boolean nullFirst;

    public JdbcQueryOrderColumnImpl(JdbcColumn selectColumn, String order) {
        this.selectColumn = selectColumn;
        this.order = order;
    }
    public JdbcQueryOrderColumnImpl(JdbcColumn selectColumn, OrderDef def) {
        this.selectColumn = selectColumn;
        this.order = def.getOrder();
        this.nullFirst = def.isNullFirst();
        this.nullLast = def.isNullLast();
    }
}
