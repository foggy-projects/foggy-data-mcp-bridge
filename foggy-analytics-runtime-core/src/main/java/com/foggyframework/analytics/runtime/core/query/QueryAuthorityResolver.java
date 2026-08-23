package com.foggyframework.analytics.runtime.core.query;

/** Resolves an opaque authority binding into an adapter-owned execution context. */
@FunctionalInterface
public interface QueryAuthorityResolver<A> {

    A resolve(QueryAuthorityRequest request);
}
