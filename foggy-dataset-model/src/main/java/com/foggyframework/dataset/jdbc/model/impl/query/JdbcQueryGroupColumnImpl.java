package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.dataset.jdbc.model.spi.support.AggregationJdbcColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JdbcQueryGroupColumnImpl {

    AggregationJdbcColumn aggColumn;

}
