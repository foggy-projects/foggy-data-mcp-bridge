package com.foggyframework.dataset.model;

import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

public interface KpiModel {

    Map<String,Object> queryMap(QueryExpEvaluator ee);
    List<Object> queryList(QueryExpEvaluator ee);

    QueryExpEvaluator newQueryExpEvaluator(ApplicationContext appCtx);
}
