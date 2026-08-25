package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;

/** Optional host composition for the full query-model DSL and restricted Compose/CTE. */
public interface AnalyticsAdvancedSemanticFunctionOperations {

    AnalyticsQueryModelResult runQueryModel(
            AnalyticsQueryModelFunctionRequest request,
            AnalyticsFunctionContext context);

    AnalyticsComposeResult runCompose(
            AnalyticsComposeFunctionRequest request,
            AnalyticsFunctionContext context);
}
