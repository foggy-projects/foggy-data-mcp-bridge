package com.foggyframework.dataset.jdbc.model.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KpiItem {
    String name;
    String caption;

    /**
     * JdbcColumnType
     */
    String type;

    boolean measure;

    Object value;


    public static KpiItem of(String name,String caption,String type, boolean measure, Object value) {
        return new KpiItem(name,caption,type, measure, value);
    }

}
