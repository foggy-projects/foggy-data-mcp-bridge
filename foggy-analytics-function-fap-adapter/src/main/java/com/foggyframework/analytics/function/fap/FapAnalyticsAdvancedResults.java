package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** FAP result projection for the advanced semantic surfaces. */
final class FapAnalyticsAdvancedResults {

    private FapAnalyticsAdvancedResults() {
    }

    static Map<String, Object> queryModel(AnalyticsQueryModelResult value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespace", value.namespace());
        result.put("modelName", value.modelName());
        result.put("mode", value.mode());
        result.put("response", value.response());
        return FapAnalyticsValues.object("queryModel", result);
    }

    static Map<String, Object> compose(AnalyticsComposeResult value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("namespace", value.namespace());
        result.put("mode", value.mode());
        result.put("valid", value.valid());
        result.put("executed", value.executed());
        result.put("value", value.value());
        result.put("sql", value.sql());
        result.put("params", value.params());
        result.put("warnings", value.warnings());
        return FapAnalyticsValues.object("compose", result);
    }
}
