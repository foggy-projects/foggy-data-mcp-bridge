package com.foggyframework.analytics.runtime.core.query;

/** Executes a governed QuerySpec using an adapter-owned resolved authority. */
@FunctionalInterface
public interface QueryExecutor<A> {

    QueryExecutionResult execute(QueryExecutionContext<A> context);
}
