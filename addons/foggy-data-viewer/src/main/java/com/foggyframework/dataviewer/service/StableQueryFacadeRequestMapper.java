package com.foggyframework.dataviewer.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;

import java.util.Map;
import java.util.Objects;

/** Internal mapper from the viewer's retained request model to model-api DTOs. */
public final class StableQueryFacadeRequestMapper {

    private static final ObjectMapper QUERY_MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private static final TypeReference<Map<String, Object>> QUERY_MAP = new TypeReference<>() { };

    private StableQueryFacadeRequestMapper() {
    }

    public static QueryFacadeRequest from(PagingRequest<?> request, String namespace) {
        return requestBuilder(request).namespace(namespace).build();
    }

    public static QueryFacadeRequest from(
            PagingRequest<?> request,
            String authorization,
            String namespace
    ) {
        return requestBuilder(request)
                .authorization(authorization)
                .namespace(namespace)
                .build();
    }

    private static QueryFacadeRequest.Builder requestBuilder(PagingRequest<?> request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getParam(), "request.param must not be null");
        Map<String, Object> query = QUERY_MAPPER.convertValue(request.getParam(), QUERY_MAP);
        return QueryFacadeRequest.builder(query)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .start(request.getStart())
                .limit(request.getLimit());
    }
}
