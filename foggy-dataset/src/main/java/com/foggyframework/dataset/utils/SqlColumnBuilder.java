package com.foggyframework.dataset.utils;

import com.foggyframework.dataset.db.table.SqlColumnType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@ToString
public class SqlColumnBuilder {
    Integer length;
    SqlColumnType type;
    String name;
    boolean index;

    String defaultValue;
//    SqlColumn sqlColumn;
}
