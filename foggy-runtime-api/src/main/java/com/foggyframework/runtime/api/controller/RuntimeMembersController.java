package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

/**
 * Stable Runtime API adapter for the existing governed dimension-member query.
 *
 * <p>The legacy {@code /jdbc-model/dimension/v2/...} route remains available
 * for compatibility. This controller owns only the public Runtime envelope
 * and delegates the actual semantic/member permission execution to the engine
 * service.</p>
 */
@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeMembersController {

    private final RuntimeApiResponseFactory responses;
    private final JdbcService jdbcService;

    public RuntimeMembersController(
            RuntimeApiResponseFactory responses,
            JdbcService jdbcService
    ) {
        this.responses = responses;
        this.jdbcService = jdbcService;
    }

    @PostMapping(RuntimeApiRoutes.V1.MEMBER_LIST)
    public RuntimeEnvelope<PagingResultImpl<DbDataItem>> listMembers(
            @PathVariable String model,
            @PathVariable String dimension,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String normalizedModel = blankToNull(model);
        String normalizedDimension = blankToNull(dimension);
        if (normalizedModel == null || normalizedDimension == null) {
            return responses.fail(
                    "INVALID_REQUEST",
                    "members.list",
                    "Missing required model or dimension path variable.",
                    normalizedModel,
                    null,
                    null,
                    "Provide both a QM model and dimension name.",
                    false
            );
        }

        try {
            DimensionDataQueryForm form = new DimensionDataQueryForm(
                    normalizedModel, normalizedDimension);
            PagingRequest<DimensionDataQueryForm> request =
                    PagingRequest.buildPagingRequest(form);
            return responses.ok(jdbcService.queryDimensionData(
                    request, authorization, namespace));
        } catch (ModelPermissionException e) {
            return responses.fail(
                    e.getCode(),
                    "members.list",
                    e.getMessage(),
                    normalizedModel,
                    normalizedDimension,
                    null,
                    "Verify data-plane authorization and retry.",
                    false
            );
        } catch (IllegalArgumentException e) {
            return responses.fail(
                    "INVALID_REQUEST",
                    "members.list",
                    "The member query request is invalid.",
                    normalizedModel,
                    normalizedDimension,
                    null,
                    "Verify the model and dimension names, then retry.",
                    false
            );
        } catch (Exception e) {
            return responses.fail(
                    "MEMBER_QUERY_FAILED",
                    "members.list",
                    "The member query could not be completed.",
                    normalizedModel,
                    normalizedDimension,
                    null,
                    "Verify the model, namespace, datasource, and authorization, then retry.",
                    false
            );
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
