package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionJsonValues;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQuery;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict semantic Function parser kept separate from the protocol outcome adapter. */
final class FapAnalyticsSemanticRequestMapper {

    private static final Set<String> MODEL_ARGUMENTS = Set.of(
            "namespace", "modelName", "expectedModelRevision");
    private static final Set<String> QUERY_ARGUMENTS = Set.of(
            "namespace", "modelName", "expectedModelRevision", "query");
    private static final Set<String> QUERY_FIELDS = Set.of(
            "columns", "filters", "groupBy", "orderBy", "start", "limit",
            "returnTotal", "distinct");
    private static final Set<String> FILTER_FIELDS = Set.of(
            "field", "operator", "value");
    private static final Set<String> GROUP_FIELDS = Set.of(
            "field", "aggregation");
    private static final Set<String> ORDER_FIELDS = Set.of(
            "field", "direction");
    private static final AnalyticsFunctionAuthority VALIDATION_AUTHORITY =
            new AnalyticsFunctionAuthority("fap-adapter", "input-validation");

    private final FapAnalyticsAuthorityResolver authorityResolver;

    FapAnalyticsSemanticRequestMapper(
            FapAnalyticsAuthorityResolver authorityResolver) {
        this.authorityResolver = Objects.requireNonNull(
                authorityResolver, "authorityResolver");
    }

    AnalyticsSemanticModelFunctionRequest model(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        try {
            requireKeys(invocation.arguments(), MODEL_ARGUMENTS);
            String namespace = requiredString(invocation.arguments(), "namespace");
            String modelName = requiredString(invocation.arguments(), "modelName");
            String revision = requiredString(
                    invocation.arguments(), "expectedModelRevision");
            new AnalyticsSemanticModelFunctionRequest(
                    namespace,
                    modelName,
                    revision,
                    VALIDATION_AUTHORITY,
                    context(invocation));
            return new AnalyticsSemanticModelFunctionRequest(
                    namespace,
                    modelName,
                    revision,
                    authority(invocation, operation),
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    AnalyticsSemanticQueryFunctionRequest query(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        try {
            requireKeys(invocation.arguments(), QUERY_ARGUMENTS);
            String namespace = requiredString(invocation.arguments(), "namespace");
            String modelName = requiredString(invocation.arguments(), "modelName");
            String revision = requiredString(
                    invocation.arguments(), "expectedModelRevision");
            AnalyticsSemanticQuery query = query(invocation.arguments().get("query"));
            new AnalyticsSemanticQueryFunctionRequest(
                    namespace,
                    modelName,
                    revision,
                    query,
                    VALIDATION_AUTHORITY,
                    context(invocation));
            return new AnalyticsSemanticQueryFunctionRequest(
                    namespace,
                    modelName,
                    revision,
                    query,
                    authority(invocation, operation),
                    context(invocation));
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private AnalyticsFunctionAuthority authority(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        try {
            AnalyticsFunctionAuthority authority = authorityResolver.resolve(
                    invocation.caller(), operation);
            if (authority == null) {
                throw new AuthorityUnavailable();
            }
            return authority;
        } catch (AuthorityUnavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException unavailable) {
            throw new AuthorityUnavailable();
        }
    }

    private static AnalyticsSemanticQuery query(Object value) {
        Map<String, Object> query = object(value);
        requireKeys(query, QUERY_FIELDS);
        return new AnalyticsSemanticQuery(
                stringList(query.get("columns"), true),
                objectList(query.get("filters")).stream()
                        .map(filter -> {
                            requireKeys(filter, FILTER_FIELDS);
                            return new AnalyticsSemanticQuery.Filter(
                                    requiredString(filter, "field"),
                                    requiredString(filter, "operator"),
                                    filter.get("value"));
                        })
                        .toList(),
                objectList(query.get("groupBy")).stream()
                        .map(group -> {
                            requireKeys(group, GROUP_FIELDS);
                            return new AnalyticsSemanticQuery.Group(
                                    requiredString(group, "field"),
                                    optionalString(group, "aggregation"));
                        })
                        .toList(),
                objectList(query.get("orderBy")).stream()
                        .map(order -> {
                            requireKeys(order, ORDER_FIELDS);
                            return new AnalyticsSemanticQuery.Order(
                                    requiredString(order, "field"),
                                    requiredString(order, "direction"));
                        })
                        .toList(),
                integer(query.get("start"), 0),
                integer(query.get("limit"), 100),
                bool(query.get("returnTotal"), false),
                bool(query.get("distinct"), false));
    }

    private static void requireKeys(Map<String, Object> value, Set<String> allowed) {
        if (!allowed.containsAll(value.keySet())) {
            throw new ArgumentsInvalid();
        }
    }

    private static String requiredString(Map<String, Object> value, String name) {
        Object candidate = value.get(name);
        if (!(candidate instanceof String text)) {
            throw new ArgumentsInvalid();
        }
        return text;
    }

    private static String optionalString(Map<String, Object> value, String name) {
        Object candidate = value.get(name);
        if (candidate == null) {
            return null;
        }
        if (!(candidate instanceof String text)) {
            throw new ArgumentsInvalid();
        }
        return text;
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ArgumentsInvalid();
        }
        try {
            return AnalyticsFunctionJsonValues.normalizeObject("query", map);
        } catch (IllegalArgumentException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private static List<Map<String, Object>> objectList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ArgumentsInvalid();
        }
        return list.stream().map(FapAnalyticsSemanticRequestMapper::object).toList();
    }

    private static List<String> stringList(Object value, boolean required) {
        if (value == null) {
            if (required) {
                throw new ArgumentsInvalid();
            }
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ArgumentsInvalid();
        }
        return list.stream().map(item -> {
            if (!(item instanceof String text)) {
                throw new ArgumentsInvalid();
            }
            return text;
        }).toList();
    }

    private static int integer(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new ArgumentsInvalid();
        }
        try {
            return new BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    private static boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean result)) {
            throw new ArgumentsInvalid();
        }
        return result;
    }

    private static AnalyticsFunctionRequestContext context(
            FapAnalyticsFunctionInvocation invocation) {
        return new AnalyticsFunctionRequestContext(
                invocation.requestId(), invocation.functionInvocationId());
    }

    static final class ArgumentsInvalid extends RuntimeException {
    }

    static final class AuthorityUnavailable extends RuntimeException {
    }
}
