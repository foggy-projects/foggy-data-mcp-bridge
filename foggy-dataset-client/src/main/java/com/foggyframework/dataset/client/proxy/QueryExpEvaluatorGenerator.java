package com.foggyframework.dataset.client.proxy;

import com.foggyframework.dataset.model.QueryExpEvaluator;

public interface QueryExpEvaluatorGenerator {
    QueryExpEvaluator generator(QueryExpEvaluator ee, Object[] objects);
}
