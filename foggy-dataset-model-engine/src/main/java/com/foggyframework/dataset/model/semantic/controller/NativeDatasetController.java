package com.foggyframework.dataset.model.semantic.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.NativeComposeQueryService;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import com.foggyframework.dataset.model.semantic.support.QueryInputWarnings;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Native HTTP endpoints for dataset query capabilities without MCP/JSON-RPC.
 */
@Slf4j
@Api(tags = "Dataset Native REST API")
@RestController
@RequestMapping("/semantic/v3/dataset")
@RequiredArgsConstructor
public class NativeDatasetController {

    private final SemanticQueryServiceV3 semanticQueryServiceV3;
    private final SemanticServiceV3 semanticServiceV3;
    private final NativeComposeQueryService nativeComposeQueryService;
    private final SemanticModelCatalogService catalogService;
    private final SemanticQueryPayloadMapper payloadMapper;
    private final DatasetProperties datasetProperties;

    @ApiOperation("执行单模型查询（MCP-free）")
    @PostMapping(value = "/query", produces = MediaType.APPLICATION_JSON_VALUE)
    public RX<SemanticQueryResponse> query(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace) {
        String model = stringValue(request != null ? request.get("model") : null);
        if (model == null || model.isBlank()) {
            return RX.failB("缺少必要参数: model");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = request != null && request.get("payload") instanceof Map<?, ?> payloadMap
                ? (Map<String, Object>) payloadMap
                : Collections.emptyMap();
        String mode = stringOr(request.get("mode"), "execute");

        SemanticQueryRequest queryRequest = payloadMapper.toQueryRequest(
                payload, datasetProperties.getQuery().getUnknownPropertyPolicy());
        SemanticRequestContext context = buildContext(request, namespace, authorization);
        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(model, queryRequest, mode, context);
        QueryInputWarnings.attach(response, queryRequest);
        return RX.ok(response);
    }

    @ApiOperation("执行复杂编排脚本（MCP-free）")
    @PostMapping(value = "/compose", produces = MediaType.APPLICATION_JSON_VALUE)
    public RX<Map<String, Object>> compose(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader Map<String, String> headers) {
        Map<String, Object> response = nativeComposeQueryService.execute(
                request != null ? request : Collections.emptyMap(),
                resolveNamespace(namespace, request),
                authorization,
                headers != null ? new LinkedHashMap<>(headers) : Collections.emptyMap());
        return RX.ok(response);
    }

    @ApiOperation("获取模型列表（MCP-free）")
    @PostMapping(value = {"/list_models", "/list-models"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public RX<Map<String, Object>> listModels(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace) {
        Map<String, Object> response = catalogService.buildCatalogResponse(
                request != null ? request : Collections.emptyMap(),
                resolveNamespace(namespace, request),
                authorization);
        return RX.ok(response);
    }

    @ApiOperation("获取单模型完整元数据（MCP-free）")
    @PostMapping(value = {"/describe_model_internal", "/describe-model-internal"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public RX<SemanticMetadataResponse> describeModel(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace) {
        String model = stringValue(request != null ? request.get("model") : null);
        if (model == null || model.isBlank()) {
            return RX.failB("缺少必要参数: model");
        }
        String format = stringOr(request != null ? request.get("format") : null, "json");

        SemanticMetadataRequest metadataRequest = new SemanticMetadataRequest();
        metadataRequest.setQmModels(Collections.singletonList(model));
        SemanticMetadataResponse response = semanticServiceV3.getMetadata(
                metadataRequest,
                format,
                buildContext(request != null ? request : Collections.emptyMap(), namespace, authorization)
        );
        return RX.ok(response);
    }

    @ApiOperation("获取模型列表（MCP-free GET）")
    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public RX<Map<String, Object>> models(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String namespace) {
        return RX.ok(catalogService.buildCatalogResponse(Collections.emptyMap(), resolveNamespace(namespace), authorization));
    }

    private SemanticRequestContext buildContext(Map<String, Object> request, String namespace, String authorization) {
        ModelResultContext.SecurityContext securityContext = authorization != null && !authorization.isBlank()
                ? ModelResultContext.SecurityContext.fromAuthorization(authorization)
                : null;
        Set<String> fieldAccess = payloadMapper.optionalStringSet(firstPresent(request, "visibleFields", "fieldAccess"));
        return SemanticRequestContext.of(
                resolveNamespace(namespace, request),
                securityContext,
                fieldAccess,
                payloadMapper.extractDeniedColumns(request),
                payloadMapper.extractSystemSlice(request)
        );
    }

    private String resolveNamespace(String namespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, namespace);
    }

    private String resolveNamespace(String headerNamespace, Map<String, Object> request) {
        String bodyNamespace = stringValue(request != null ? request.get("namespace") : null);
        String resolved = DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
        logNamespaceConflict(headerNamespace, bodyNamespace, resolved);
        return resolved;
    }

    private static void logNamespaceConflict(String headerNamespace, String bodyNamespace, String resolved) {
        String header = blankToNull(headerNamespace);
        String body = blankToNull(bodyNamespace);
        if (header != null && body != null && !header.equals(body)) {
            log.info("Dataset REST namespace conflict resolved by X-NS header: header={}, body={}, effective={}",
                    header, body, resolved);
        }
    }

    private static Object firstPresent(Map<String, Object> map, String first, String second) {
        if (map == null) {
            return null;
        }
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private static String stringOr(Object value, String fallback) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
