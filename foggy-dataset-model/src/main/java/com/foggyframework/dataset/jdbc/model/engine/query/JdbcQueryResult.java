package com.foggyframework.dataset.jdbc.model.engine.query;

import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.spi.QueryEngine;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.Data;

@Data
public class JdbcQueryResult {
    PagingResultImpl pagingResult;

    QueryEngine queryEngine;

    public static JdbcQueryResult of(PagingResultImpl pagingResult, QueryEngine queryEngine){
        JdbcQueryResult result = new JdbcQueryResult();
        result.setPagingResult(pagingResult);
        result.setQueryEngine(queryEngine);

        return result;
    }
}
