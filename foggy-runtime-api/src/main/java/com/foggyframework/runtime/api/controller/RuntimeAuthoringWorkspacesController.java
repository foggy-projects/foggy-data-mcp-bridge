package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.candidate.CandidateQueryException;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.semantic.support.UnknownQueryPropertyPolicy;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDeleteRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDiffRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceDiffResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceListResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceQueryRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceQueryResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePublishRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRecoverRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceResource;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceResourcesResponse;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRevisionRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceSaveRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceException;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspacePublicationService;
import com.foggyframework.runtime.api.service.RuntimeCandidateQueryService.Phase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.Locale;
import java.util.function.Supplier;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeAuthoringWorkspacesController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeAuthoringWorkspaceService workspaces;
    private final RuntimeAuthoringWorkspacePublicationService publications;
    private final ObjectMapper objectMapper;
    private final SemanticQueryPayloadMapper payloadMapper;
    private final DatasetProperties datasetProperties;

    public RuntimeAuthoringWorkspacesController(
            RuntimeApiResponseFactory responses,
            RuntimeAuthoringWorkspaceService workspaces
    ) {
        this(responses, workspaces, null, new ObjectMapper(), new DatasetProperties());
    }

    public RuntimeAuthoringWorkspacesController(
            RuntimeApiResponseFactory responses,
            RuntimeAuthoringWorkspaceService workspaces,
            RuntimeAuthoringWorkspacePublicationService publications
    ) {
        this(responses, workspaces, publications, new ObjectMapper(), new DatasetProperties());
    }

    @Autowired
    public RuntimeAuthoringWorkspacesController(
            RuntimeApiResponseFactory responses,
            RuntimeAuthoringWorkspaceService workspaces,
            RuntimeAuthoringWorkspacePublicationService publications,
            ObjectMapper objectMapper,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this(responses, workspaces, publications, objectMapper,
                datasetPropertiesProvider.getIfAvailable(DatasetProperties::new));
    }

    private RuntimeAuthoringWorkspacesController(
            RuntimeApiResponseFactory responses,
            RuntimeAuthoringWorkspaceService workspaces,
            RuntimeAuthoringWorkspacePublicationService publications,
            ObjectMapper objectMapper,
            DatasetProperties datasetProperties
    ) {
        this.responses = responses;
        this.workspaces = workspaces;
        this.publications = publications;
        this.objectMapper = objectMapper;
        this.payloadMapper = new SemanticQueryPayloadMapper(objectMapper);
        this.datasetProperties = datasetProperties;
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_WORKSPACES)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> create(
            @RequestBody(required = false) AuthoringWorkspaceCreateRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        return invoke(() -> workspaces.create(namespace, request));
    }

    @GetMapping(RuntimeApiRoutes.V1.AUTHORING_WORKSPACES)
    public RuntimeEnvelope<AuthoringWorkspaceListResponse> list(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "false") boolean includeDiscarded
    ) {
        return invoke(() -> workspaces.list(
                namespace, parseState(state), includeDiscarded));
    }

    @GetMapping(RuntimeApiRoutes.V1.AUTHORING_WORKSPACE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> get(
            @PathVariable String workspaceId
    ) {
        return invoke(() -> workspaces.get(workspaceId));
    }

    @DeleteMapping(RuntimeApiRoutes.V1.AUTHORING_WORKSPACE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> discard(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String expectedCandidateRevision
    ) {
        return invoke(() -> workspaces.discard(
                workspaceId, expectedCandidateRevision));
    }

    @GetMapping(RuntimeApiRoutes.V1.AUTHORING_RESOURCES)
    public RuntimeEnvelope<AuthoringWorkspaceResourcesResponse> resources(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String candidateRevision
    ) {
        return invoke(() -> workspaces.listResources(
                workspaceId, candidateRevision));
    }

    @GetMapping(RuntimeApiRoutes.V1.AUTHORING_RESOURCE_CONTENT)
    public RuntimeEnvelope<AuthoringWorkspaceResource> content(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String candidateRevision
    ) {
        return invoke(() -> workspaces.readResource(
                workspaceId, candidateRevision, path));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_RESOURCES_SAVE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> save(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceSaveRequest request
    ) {
        return invoke(() -> workspaces.save(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_RESOURCES_DELETE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> delete(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceDeleteRequest request
    ) {
        return invoke(() -> workspaces.delete(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_DIFF)
    public RuntimeEnvelope<AuthoringWorkspaceDiffResponse> diff(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceDiffRequest request
    ) {
        return invoke(() -> workspaces.diff(
                workspaceId,
                request == null ? null : request.candidateRevision(),
                request != null && Boolean.TRUE.equals(request.includeContent())));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_VALIDATE)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> validate(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceRevisionRequest request
    ) {
        return invoke(() -> workspaces.validate(
                workspaceId,
                request == null ? null : request.candidateRevision()));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_PUBLISH)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> publish(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspacePublishRequest request
    ) {
        return invoke(() -> publicationService().publish(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_PUBLISH_RECOVER)
    public RuntimeEnvelope<AuthoringWorkspaceInfo> recoverPublication(
            @PathVariable String workspaceId,
            @RequestBody(required = false) AuthoringWorkspaceRecoverRequest request
    ) {
        return invoke(() -> publicationService().recover(workspaceId, request));
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_QUERY_VALIDATE)
    public RuntimeEnvelope<AuthoringWorkspaceQueryResponse> validateQueryRequest(
            @PathVariable String workspaceId,
            @PathVariable String model,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            HttpServletResponse httpResponse
    ) {
        try {
            return validateQuery(workspaceId, model, toAuthoringQueryRequest(rawBody), authorization);
        } catch (QueryInputValidationException failure) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            return queryInputFailure(failure, "workspaces.query.validate", model);
        } catch (IllegalArgumentException failure) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            return invalidQueryJson("workspaces.query.validate", model);
        }
    }

    public RuntimeEnvelope<AuthoringWorkspaceQueryResponse> validateQuery(
            @PathVariable String workspaceId,
            @PathVariable String model,
            @RequestBody(required = false) AuthoringWorkspaceQueryRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization
    ) {
        return invokeQuery(() -> workspaces.query(
                workspaceId, model,
                request == null ? null : request.candidateRevision(),
                request == null ? null : request.request(), authorization,
                Phase.VALIDATE), "QUERY_VALIDATE_FAILED",
                "workspaces.query.validate");
    }

    @PostMapping(RuntimeApiRoutes.V1.AUTHORING_QUERY_EXECUTE)
    public RuntimeEnvelope<AuthoringWorkspaceQueryResponse> executeQueryRequest(
            @PathVariable String workspaceId,
            @PathVariable String model,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization,
            HttpServletResponse httpResponse
    ) {
        try {
            return executeQuery(workspaceId, model, toAuthoringQueryRequest(rawBody), authorization);
        } catch (QueryInputValidationException failure) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            return queryInputFailure(failure, "workspaces.query.execute", model);
        } catch (IllegalArgumentException failure) {
            httpResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            return invalidQueryJson("workspaces.query.execute", model);
        }
    }

    public RuntimeEnvelope<AuthoringWorkspaceQueryResponse> executeQuery(
            @PathVariable String workspaceId,
            @PathVariable String model,
            @RequestBody(required = false) AuthoringWorkspaceQueryRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization
    ) {
        return invokeQuery(() -> workspaces.query(
                workspaceId, model,
                request == null ? null : request.candidateRevision(),
                request == null ? null : request.request(), authorization,
                Phase.EXECUTE), "QUERY_EXECUTE_FAILED",
                "workspaces.query.execute");
    }

    private AuthoringWorkspaceQueryRequest toAuthoringQueryRequest(String rawBody) {
        RuntimeQueryJsonSupport.ParsedJson parsed = RuntimeQueryJsonSupport.parse(objectMapper, rawBody);
        JsonNode body = parsed.body();
        if (body == null || body.isNull()) {
            return null;
        }
        String candidateRevision = body.hasNonNull("candidateRevision")
                ? body.get("candidateRevision").asText()
                : null;
        JsonNode requestNode = body.get("request");
        if (requestNode == null || requestNode.isNull()) {
            return new AuthoringWorkspaceQueryRequest(candidateRevision, null);
        }
        Map<String, Object> payload = objectMapper.convertValue(
                requestNode, new TypeReference<Map<String, Object>>() { });
        return new AuthoringWorkspaceQueryRequest(
                candidateRevision,
                payloadMapper.toQueryRequest(
                        payload,
                        unknownPropertyPolicy(),
                        RuntimeQueryJsonSupport.nestedDuplicates(parsed.duplicates(), "request")));
    }

    private UnknownQueryPropertyPolicy unknownPropertyPolicy() {
        return datasetProperties != null && datasetProperties.getQuery() != null
                ? datasetProperties.getQuery().getUnknownPropertyPolicy()
                : UnknownQueryPropertyPolicy.WARN;
    }

    private RuntimeEnvelope<AuthoringWorkspaceQueryResponse> queryInputFailure(
            QueryInputValidationException failure,
            String phase,
            String model
    ) {
        var first = failure.getViolations().isEmpty() ? null : failure.getViolations().get(0);
        RuntimeError error = new RuntimeError(
                failure.getCode(), phase, failure.getMessage(), model, null,
                first == null ? null : first.path(),
                first == null
                        ? "Remove unsupported Query DSL properties and retry."
                        : first.suggestedNextAction(),
                false, null, null);
        return responses.fail(error, RuntimeQueryJsonSupport.validationDiagnostics(failure));
    }

    private RuntimeEnvelope<AuthoringWorkspaceQueryResponse> invalidQueryJson(
            String phase,
            String model
    ) {
        RuntimeError error = new RuntimeError(
                "INVALID_DSL_SYNTAX", phase, "Invalid authoring query request JSON.",
                model, null, null, "Fix the query JSON shape and retry.",
                true, null, null);
        return responses.fail(error, RuntimeDiagnostics.empty());
    }

    private <T> RuntimeEnvelope<T> invoke(Supplier<T> action) {
        try {
            return responses.ok(action.get());
        } catch (RuntimeAuthoringWorkspaceException failure) {
            return workspaceFailure(failure);
        } catch (CandidateQueryException failure) {
            return candidateFailure(failure);
        }
    }

    private <T> RuntimeEnvelope<T> invokeQuery(
            Supplier<T> action,
            String fallbackCode,
            String phase
    ) {
        try {
            return invoke(action);
        } catch (RuntimeException queryFailure) {
            return responses.fail(
                    fallbackCode, phase,
                    "Workspace candidate query failed.", null, null, null,
                    "Inspect the candidate request and Runtime diagnostics, then retry.",
                    false);
        }
    }

    private <T> RuntimeEnvelope<T> workspaceFailure(
            RuntimeAuthoringWorkspaceException failure
    ) {
        return responses.fail(
                failure.code(), failure.phase(), failure.getMessage(),
                null, null, failure.path(),
                failure.safeToAutoRepair()
                        ? "Refresh workspace metadata and retry with the current revision."
                        : "Inspect workspace state and source eligibility before retrying.",
                failure.safeToAutoRepair());
    }

    private <T> RuntimeEnvelope<T> candidateFailure(
            CandidateQueryException failure
    ) {
        return responses.fail(
                failure.code().name(), failure.phase(), failure.getMessage(),
                null, null, failure.resource(),
                "Inspect candidate query diagnostics and retry.", false);
    }

    private RuntimeAuthoringWorkspacePublicationService publicationService() {
        if (publications == null) {
            throw new RuntimeAuthoringWorkspaceException(
                    "WORKSPACE_STATE_INVALID", "workspaces.publish.preflight",
                    "Workspace publication is unavailable.", null, false);
        }
        return publications;
    }

    private static AuthoringWorkspaceState parseState(String state) {
        if (!StringUtils.hasText(state)) {
            return null;
        }
        try {
            return AuthoringWorkspaceState.valueOf(
                    state.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidState) {
            throw new RuntimeAuthoringWorkspaceException(
                    "WORKSPACE_INVALID_REQUEST", "workspaces.list",
                    "Workspace state filter is invalid.", null, true);
        }
    }
}
