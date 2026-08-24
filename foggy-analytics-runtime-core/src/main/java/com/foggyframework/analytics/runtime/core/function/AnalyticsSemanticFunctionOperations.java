package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;

/** Optional host composition for governed ad-hoc semantic reads. */
public interface AnalyticsSemanticFunctionOperations {

    AnalyticsSemanticModelDescription describeModel(
            AnalyticsSemanticModelFunctionRequest request,
            AnalyticsFunctionContext context);

    AnalyticsSemanticQueryResult executeQuery(
            AnalyticsSemanticQueryFunctionRequest request,
            AnalyticsFunctionContext context);
}
