package com.foggyframework.dataset.model.semantic.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticQueryInputExceptionHandlerTest {

    @Test
    void adviceIsScopedToSemanticQueryControllersAndReturnsStructuredBadRequest() {
        RestControllerAdvice advice = SemanticQueryInputExceptionHandler.class
                .getAnnotation(RestControllerAdvice.class);

        assertThat(advice).isNotNull();
        assertThat(Arrays.asList(advice.assignableTypes()))
                .containsExactlyInAnyOrder(
                        NativeDatasetController.class,
                        SemanticServiceV3TestController.class);

        QueryInputWarning violation = new QueryInputWarning(
                "UNKNOWN_QUERY_PROPERTY",
                "$.groupBy[0].grain",
                "Unknown Query DSL property 'grain' is not allowed.",
                "Remove the property.",
                false,
                Map.of("property", "grain"));
        RX<?> response = new SemanticQueryInputExceptionHandler()
                .handleQueryInputValidationException(
                        new QueryInputValidationException(
                                "UNKNOWN_QUERY_PROPERTY", List.of(violation)));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getEt()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) response.getEt();
        assertThat(details)
                .containsEntry("code", "UNKNOWN_QUERY_PROPERTY")
                .containsEntry("violations", List.of(violation));
    }
}
