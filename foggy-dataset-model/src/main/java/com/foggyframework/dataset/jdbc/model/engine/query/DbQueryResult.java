package com.foggyframework.dataset.jdbc.model.engine.query;

import com.foggyframework.dataset.jdbc.model.spi.QueryEngine;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.Data;

@Data
public class DbQueryResult {
    PagingResultImpl pagingResult;

    QueryEngine queryEngine;

    public static DbQueryResult of(PagingResultImpl pagingResult, QueryEngine queryEngine){
        DbQueryResult result = new DbQueryResult();
        result.setPagingResult(pagingResult);
        result.setQueryEngine(queryEngine);

        return result;
    }
}
