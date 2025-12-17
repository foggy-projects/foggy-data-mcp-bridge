package com.foggyframework.dataset.model;

import org.springframework.jdbc.core.ResultSetExtractor;

public interface TotalCountSetExtractor {
    <T>ResultSetExtractor<T> getResultSetExtractor();

    Object extractTotal(Object obj, int start, int limit, int total);
}
