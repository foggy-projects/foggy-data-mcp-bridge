package com.foggyframework.dataset.db.model.impl.query;

import com.foggyframework.dataset.db.model.spi.support.AggregationJdbcColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryGroupColumnImpl {

    AggregationJdbcColumn aggColumn;

}
