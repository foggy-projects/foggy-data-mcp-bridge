package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnalyticsFunctionFailureMapperTest {

    @Test
    void preservesOnlyTheStableSemanticValidationCode() {
        AnalyticsSemanticFunctionException failure =
                new AnalyticsSemanticFunctionException(
                        AnalyticsSemanticFunctionException.Code.QUERY_INVALID,
                        "must not cross the Function boundary",
                        "DSL_CTE_STAGE_REFERENCE_INVALID",
                        List.of(Map.of(
                                "code", "UNKNOWN_QUERY_PROPERTY",
                                "path", "$.groupBy[0].grain")),
                        new IllegalArgumentException(
                                "stage input 'source' must reference a prior stage"));

        var error = new AnalyticsFunctionFailureMapper().map(failure);

        assertEquals(AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_INVALID, error.code());
        assertEquals("DSL_CTE_STAGE_REFERENCE_INVALID", error.message());
        assertEquals(List.of(Map.of(
                "code", "UNKNOWN_QUERY_PROPERTY",
                "path", "$.groupBy[0].grain")), error.violations());
        assertFalse(error.message().contains("source"));
        assertFalse(error.message().contains("must not cross"));
        assertFalse(error.message().contains("prior stage"));
    }
}
