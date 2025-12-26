package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.dataset.db.model.def.order.OrderDef;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryOrderColumnImpl {

    DbColumn selectColumn;

    String order;

    boolean nullLast;

    boolean nullFirst;

    public DbQueryOrderColumnImpl(DbColumn selectColumn, String order) {
        this.selectColumn = selectColumn;
        this.order = order;
    }
    public DbQueryOrderColumnImpl(DbColumn selectColumn, OrderDef def) {
        this.selectColumn = selectColumn;
        this.order = def.getOrder();
        this.nullFirst = def.isNullFirst();
        this.nullLast = def.isNullLast();
    }
}
