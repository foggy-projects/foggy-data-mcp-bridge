package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainRequest;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainResponse;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainService;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Isolated endpoint for on-demand semantic evidence. */
@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeQueryExplainController {

    private final RuntimeApiResponseFactory responses;
    private final SemanticExplainService explainService;
    private final ObjectMapper objectMapper;
    private final DatasetProperties datasetProperties;

    public RuntimeQueryExplainController(
            RuntimeApiResponseFactory responses,
            SemanticExplainService explainService,
            ObjectMapper objectMapper,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this.responses = responses;
        this.explainService = explainService;
        this.objectMapper = objectMapper;
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
    }

    @PostMapping(RuntimeApiRoutes.V1.QUERY_EXPLAIN)
    public RuntimeEnvelope<SemanticExplainResponse> explain(
            @PathVariable String model,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String normalizedModel = blankToNull(model);
        if (normalizedModel == null) {
            return fail("INVALID_REQUEST", "Missing required path variable: model",
                    model, "Provide a QM model name in the URL path.", false);
        }

        final SemanticExplainRequest request;
        try {
            request = toExplainRequest(body);
        } catch (IllegalArgumentException e) {
            return fail("INVALID_DSL_SYNTAX", "Invalid explain request JSON.", normalizedModel,
                    "Fix the explain request JSON and retry.", true);
        }

        String bodyNamespace = body != null && body.has("namespace")
                && body.get("namespace").isTextual()
                ? body.get("namespace").asText()
                : null;
        String effectiveNamespace = DatasetRequestNamespaceResolver.resolve(
                datasetProperties, namespace, bodyNamespace);
        PermissionAction action = request.getPayload() == null
                ? PermissionAction.DESCRIBE
                : PermissionAction.EXECUTE;
        try {
            SemanticExplainResponse response = explainService.explain(
                    normalizedModel,
                    request,
                    SemanticRequestContext.of(effectiveNamespace, authorization)
                            .withPermissionAction(action));
            return responses.ok(response);
        } catch (ModelPermissionException e) {
            return fail(e.getCode(), e.getMessage(), normalizedModel,
                    "Use an identity authorized for this model operation.", false);
        } catch (Exception e) {
            String message = blankToNull(e.getMessage());
            boolean modelNotFound = message != null
                    && message.toLowerCase().contains("model not found");
            return fail(
                    modelNotFound ? "MODEL_NOT_FOUND" : "QUERY_EXPLAIN_FAILED",
                    modelNotFound ? "The requested model was not found."
                            : "Semantic query explanation failed.",
                    normalizedModel,
                    "Inspect the explain request, caller permissions, and model metadata, then retry.",
                    false);
        }
    }

    private SemanticExplainRequest toExplainRequest(JsonNode body) {
        if (body == null || body.isNull()) {
            return new SemanticExplainRequest();
        }
        JsonNode payload = firstNonNull(body.get("payload"), body.get("request"));
        if (payload == null && body.has("columns")) {
            SemanticExplainRequest direct = new SemanticExplainRequest();
            direct.setPayload(objectMapper.convertValue(body, SemanticQueryRequest.class));
            return direct;
        }
        SemanticExplainRequest request = objectMapper.convertValue(body, SemanticExplainRequest.class);
        if (payload != null) {
            request.setPayload(objectMapper.convertValue(payload, SemanticQueryRequest.class));
        }
        return request;
    }

    private RuntimeEnvelope<SemanticExplainResponse> fail(
            String code,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return responses.fail(
                code,
                "query.explain",
                message,
                model,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair);
    }

    private JsonNode firstNonNull(JsonNode first, JsonNode second) {
        if (first != null && !first.isNull()) {
            return first;
        }
        return second != null && !second.isNull() ? second : null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
