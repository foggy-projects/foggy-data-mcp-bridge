package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeQueryController {

    private final RuntimeApiResponseFactory responses;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final ObjectMapper objectMapper;
    private final DatasetProperties datasetProperties;

    public RuntimeQueryController(
            RuntimeApiResponseFactory responses,
            SemanticQueryServiceV3 semanticQueryServiceV3,
            ObjectMapper objectMapper,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this.responses = responses;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
        this.objectMapper = objectMapper;
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
    }

    @PostMapping("/query/{model}/validate")
    public RuntimeEnvelope<SemanticQueryResponse> validateQuery(
            @PathVariable String model,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return query(model, body, namespace, "validate", "query.validate");
    }

    @PostMapping("/query/{model}/execute")
    public RuntimeEnvelope<SemanticQueryResponse> executeQuery(
            @PathVariable String model,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return query(model, body, namespace, "execute", "query.execute");
    }

    private RuntimeEnvelope<SemanticQueryResponse> query(
            String model,
            JsonNode body,
            String headerNamespace,
            String mode,
            String phase
    ) {
        String normalizedModel = blankToNull(model);
        if (normalizedModel == null) {
            return fail("INVALID_REQUEST", phase, "Missing required path variable: model",
                    model, null, "Provide a QM model name in the URL path.", false);
        }
        if (body == null || body.isNull()) {
            return fail("INVALID_REQUEST", phase, "Missing request body.",
                    normalizedModel, null, "Provide a query payload.", false);
        }

        SemanticQueryRequest request;
        try {
            request = toSemanticQueryRequest(body);
        } catch (IllegalArgumentException e) {
            return fail("INVALID_DSL_SYNTAX", phase, e.getMessage(),
                    normalizedModel, null, "Fix the query JSON shape and retry.", true);
        }

        try {
            SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                    normalizedModel,
                    request,
                    mode,
                    SemanticRequestContext.ofNamespace(resolveNamespace(headerNamespace, bodyNamespace(body)))
            );
            if ("query.validate".equals(phase)) {
                String blockingWarning = firstBlockingValidationWarning(response);
                if (blockingWarning != null) {
                    QueryErrorMapping errorMapping = mapQueryError(new IllegalArgumentException(blockingWarning), phase);
                    return fail(errorMapping.code(), phase, blockingWarning, normalizedModel, errorMapping.field(),
                            errorMapping.suggestedNextAction(), errorMapping.safeToAutoRepair());
                }
            }
            return responses.ok(response);
        } catch (Exception e) {
            QueryErrorMapping errorMapping = mapQueryError(e, phase);
            return fail(errorMapping.code(), phase, e.getMessage(), normalizedModel, errorMapping.field(),
                    errorMapping.suggestedNextAction(), errorMapping.safeToAutoRepair());
        }
    }

    private SemanticQueryRequest toSemanticQueryRequest(JsonNode body) {
        JsonNode payload = firstNonNull(body.get("payload"), body.get("request"));
        JsonNode queryNode = payload != null ? payload : body;
        return objectMapper.convertValue(queryNode, SemanticQueryRequest.class);
    }

    private String bodyNamespace(JsonNode body) {
        JsonNode namespaceNode = body.get("namespace");
        if (namespaceNode != null && namespaceNode.isTextual()) {
            return namespaceNode.asText();
        }
        return null;
    }

    private String resolveNamespace(String headerNamespace, String bodyNamespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
    }

    private RuntimeEnvelope<SemanticQueryResponse> fail(
            String code,
            String phase,
            String message,
            String model,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return responses.fail(
                code,
                phase,
                message,
                model,
                field,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    private static QueryErrorMapping mapQueryError(Exception e, String phase) {
        String message = e.getMessage();
        String normalized = message != null ? message.toLowerCase() : "";
        if ("query.validate".equals(phase)) {
            if ((normalized.contains("field") && normalized.contains("not found"))
                    || normalized.contains("未能找到")
                    || normalized.contains("字段不存在")) {
                return new QueryErrorMapping(
                        "FIELD_NOT_FOUND",
                        extractField(message),
                        "Call model describe and retry with an existing field.",
                        true
                );
            }
            if (normalized.contains("syntax")
                    || normalized.contains("parse")
                    || normalized.contains("operator")
                    || normalized.contains("json")
                    || normalized.contains("dsl")) {
                return new QueryErrorMapping(
                        "INVALID_DSL_SYNTAX",
                        null,
                        "Fix the query DSL syntax and retry.",
                        true
                );
            }
            if (normalized.contains("raw_measure_selection")) {
                return new QueryErrorMapping(
                        "AMBIGUOUS_MEASURE_SELECTION",
                        "columns",
                        "Use explicit aggregate expressions such as sum(amount) as amount, or include a detail dimension/id field.",
                        true
                );
            }
            return new QueryErrorMapping(
                    "QUERY_VALIDATE_FAILED",
                    null,
                    "Inspect the query payload and model metadata, then retry.",
                    false
            );
        }
        return new QueryErrorMapping(
                "QUERY_EXECUTE_FAILED",
                null,
                "Inspect diagnostics and runtime logs, then retry.",
                false
        );
    }

    private static String firstBlockingValidationWarning(SemanticQueryResponse response) {
        if (response == null || response.getWarnings() == null) {
            return null;
        }
        for (String warning : response.getWarnings()) {
            String normalized = warning != null ? warning.toLowerCase() : "";
            if ((normalized.contains("field") && normalized.contains("not found"))
                    || normalized.contains("unknown field")
                    || normalized.contains("未能找到")
                    || normalized.contains("字段不存在")) {
                return warning;
            }
            if (normalized.contains("raw_measure_selection")) {
                return warning;
            }
        }
        return null;
    }

    private static String extractField(String message) {
        String normalized = blankToNull(message);
        if (normalized == null) {
            return null;
        }
        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < normalized.length() - 1) {
            return blankToNull(normalized.substring(colonIndex + 1));
        }
        return null;
    }

    private static JsonNode firstNonNull(JsonNode first, JsonNode second) {
        if (first != null && !first.isNull()) {
            return first;
        }
        return second != null && !second.isNull() ? second : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record QueryErrorMapping(
            String code,
            String field,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
    }
}
