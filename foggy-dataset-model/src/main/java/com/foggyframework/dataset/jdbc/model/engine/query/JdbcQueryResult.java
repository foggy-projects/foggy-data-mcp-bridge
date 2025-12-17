package com.foggyframework.dataset.jdbc.model.engine.query;

import com.foggyframework.dataset.jdbc.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.Data;

@Data
public class JdbcQueryResult {
    PagingResultImpl pagingResult;

    JdbcModelQueryEngine queryEngine;

    public static JdbcQueryResult of(PagingResultImpl pagingResult, JdbcModelQueryEngine queryEngine){
        JdbcQueryResult result = new JdbcQueryResult();
        result.setPagingResult(pagingResult);
        result.setQueryEngine(queryEngine);

        return result;
    }
}
