package com.foggyframework.dataset.model.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable query request DTO.
 *
 * <p>{@code query} is the JSON-like query-model DSL. Keeping this boundary in
 * JDK collection/value types prevents public consumers from depending on the
 * mutable engine request implementation.</p>
 *
 * @since 9.3.5
 */
public final class QueryFacadeRequest {

    private final Map<String, Object> query;
    private final Integer page;
    private final Integer pageSize;
    private final Integer start;
    private final Integer limit;
    private final String authorization;
    private final String namespace;
    private final boolean namespaceProvided;

    private QueryFacadeRequest(Builder builder) {
        Objects.requireNonNull(builder.query, "query must not be null");
        this.query = Collections.unmodifiableMap(new LinkedHashMap<>(builder.query));
        this.page = builder.page;
        this.pageSize = builder.pageSize;
        this.start = builder.start;
        this.limit = builder.limit;
        this.authorization = builder.authorization;
        this.namespace = builder.namespace;
        this.namespaceProvided = builder.namespaceProvided;
    }

    public static Builder builder(Map<String, Object> query) {
        return new Builder(query);
    }

    public Map<String, Object> getQuery() { return query; }
    public Integer getPage() { return page; }
    public Integer getPageSize() { return pageSize; }
    public Integer getStart() { return start; }
    public Integer getLimit() { return limit; }
    public String getAuthorization() { return authorization; }
    public String getNamespace() { return namespace; }
    public boolean isNamespaceProvided() { return namespaceProvided; }

    public static final class Builder {
        private final Map<String, Object> query;
        private Integer page;
        private Integer pageSize;
        private Integer start;
        private Integer limit;
        private String authorization;
        private String namespace;
        private boolean namespaceProvided;

        private Builder(Map<String, Object> query) {
            this.query = query;
        }

        public Builder page(Integer page) { this.page = page; return this; }
        public Builder pageSize(Integer pageSize) { this.pageSize = pageSize; return this; }
        public Builder start(Integer start) { this.start = start; return this; }
        public Builder limit(Integer limit) { this.limit = limit; return this; }
        public Builder authorization(String authorization) { this.authorization = authorization; return this; }

        /**
         * Selects an explicit namespace. A null/blank value means the default
         * namespace and remains distinct from omitting this method, which
         * inherits the current namespace scope.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            this.namespaceProvided = true;
            return this;
        }

        public QueryFacadeRequest build() { return new QueryFacadeRequest(this); }
    }
}
