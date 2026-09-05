package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.QueryInputWarning;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryInputWarningsTest {

    @Test
    void attachShouldClearDiagnosticsFromAReusedResponseWhenCurrentRequestHasNone() {
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setQueryInputWarnings(List.of(new QueryInputWarning(
                SemanticQueryPropertyInspector.IGNORED_CODE,
                "$.groupBy[0].grain",
                "ignored",
                "remove it",
                false,
                Map.of())));

        QueryInputWarnings.attach(response, new SemanticQueryRequest());

        assertThat(response.getQueryInputWarnings()).isNull();
    }
}
