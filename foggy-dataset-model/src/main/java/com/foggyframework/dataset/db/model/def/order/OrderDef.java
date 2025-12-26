package com.foggyframework.dataset.db.model.def.order;

import lombok.Data;

@Data
public class OrderDef {
    String name;

    /**
     * desc or asc
     */
    String order;

    boolean nullLast;

    boolean nullFirst;
}
