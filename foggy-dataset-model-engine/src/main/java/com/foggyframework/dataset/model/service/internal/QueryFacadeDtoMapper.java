package com.foggyframework.dataset.model.service.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import com.foggyframework.dataset.model.api.QueryFacadeResult;

import java.util.Map;
import java.util.Objects;

/** Engine-internal mapping between transport DTOs and stable query facade DTOs. */
public final class QueryFacadeDtoMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private static final TypeReference<Map<String, Object>> QUERY_MAP = new TypeReference<>() { };

    private QueryFacadeDtoMapper() {
    }

    public static QueryFacadeRequest toRequest(PagingRequest<DbQueryRequestDef> request) {
        return requestBuilder(request).build();
    }

    public static QueryFacadeRequest toRequest(PagingRequest<DbQueryRequestDef> request, String namespace) {
        return requestBuilder(request).namespace(namespace).build();
    }

    public static QueryFacadeRequest toRequest(
            PagingRequest<DbQueryRequestDef> request,
            String authorization,
            String namespace
    ) {
        return requestBuilder(request)
                .authorization(authorization)
                .namespace(namespace)
                .build();
    }

    public static PagingRequest<DbQueryRequestDef> toLegacyRequest(QueryFacadeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PagingRequest<DbQueryRequestDef> legacy = new PagingRequest<>();
        legacy.setPage(request.getPage());
        legacy.setPageSize(request.getPageSize());
        legacy.setStart(request.getStart());
        legacy.setLimit(request.getLimit());
        legacy.setParam(OBJECT_MAPPER.convertValue(request.getQuery(), DbQueryRequestDef.class));
        return legacy;
    }

    @SuppressWarnings("unchecked")
    public static QueryFacadeResult toResult(PagingResultImpl<?> result) {
        Objects.requireNonNull(result, "result must not be null");
        return new QueryFacadeResult(
                result.getTotal(),
                result.isHasNext(),
                result.getStart(),
                result.getLimit(),
                (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) result.getItems(),
                result.getTotalData()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static PagingResultImpl toLegacyResult(QueryFacadeResult result) {
        Objects.requireNonNull(result, "result must not be null");
        PagingResultImpl legacy = new PagingResultImpl();
        legacy.setTotal(result.getTotal());
        legacy.setHasNext(result.isHasNext());
        legacy.setStart(result.getStart());
        legacy.setLimit(result.getLimit());
        legacy.setItems((java.util.List) result.getItems());
        legacy.setTotalData(result.getTotalData());
        return legacy;
    }

    private static QueryFacadeRequest.Builder requestBuilder(PagingRequest<DbQueryRequestDef> request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getParam(), "request.param must not be null");
        Map<String, Object> query = OBJECT_MAPPER.convertValue(request.getParam(), QUERY_MAP);
        return QueryFacadeRequest.builder(query)
                .page(request.getPage())
                .pageSize(request.getPageSize())
                .start(request.getStart())
                .limit(request.getLimit());
    }
}
