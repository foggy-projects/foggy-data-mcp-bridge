package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionJsonValues;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict parser for the MCP-compatible DSL and restricted Compose Function surfaces. */
final class FapAnalyticsAdvancedRequestMapper {

    private static final Set<String> QUERY_ARGUMENTS = Set.of(
            "namespace", "modelName", "mode", "payload");
    private static final Set<String> QUERY_PAYLOAD_FIELDS = Set.of(
            "route", "executable_plan", "executablePlan", "calculatedFields",
            "columns", "slice", "having", "orderBy", "groupBy", "start",
            "limit", "returnTotal", "distinct", "withSubtotals", "timeWindow",
            "pivot");
    private static final Set<String> COMPOSE_ARGUMENTS = Set.of(
            "namespace", "mode", "script", "params");
    private static final AnalyticsFunctionAuthority VALIDATION_AUTHORITY =
            new AnalyticsFunctionAuthority("fap-adapter", "input-validation");

    private final FapAnalyticsAuthorityResolver authorityResolver;

    FapAnalyticsAdvancedRequestMapper(FapAnalyticsAuthorityResolver authorityResolver) {
        this.authorityResolver = Objects.requireNonNull(
                authorityResolver, "authorityResolver");
    }

    AnalyticsQueryModelFunctionRequest queryModel(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        try {
            requireKeys(invocation.arguments(), QUERY_ARGUMENTS);
            Map<String, Object> payload = object("payload", invocation.arguments().get("payload"));
            requireKeys(payload, QUERY_PAYLOAD_FIELDS);
            AnalyticsQueryModelFunctionRequest validated =
                    new AnalyticsQueryModelFunctionRequest(
                            requiredString(invocation.arguments(), "namespace"),
                            requiredString(invocation.arguments(), "modelName"),
                            requiredString(invocation.arguments(), "mode"),
                            payload,
                            VALIDATION_AUTHORITY,
                            context(invocation));
            return new AnalyticsQueryModelFunctionRequest(
                    validated.namespace(),
                    validated.modelName(),
                    validated.mode(),
                    validated.payload(),
                    authority(invocation, operation),
                    validated.context());
        } catch (ArgumentsInvalid invalid) {
            throw invalid;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new ArgumentsInvalid();
        }
    }

    AnalyticsComposeFunctionRequest compose(
            FapAnalyticsFunctionInvocation invocation,
            String operation) {
        try {
            requireKeys(invocation.arguments(), COMPOSE_ARGUMENTS);
            Map<String, Object> params = invocation.arguments().get("params") == null
                    ? Map.of()
                    : object("params", invocation.arguments().get("params"));
            AnalyticsComposeFunctionRequest validated = new AnalyticsComposeFunctionRequest(
                    requiredString(invocation.arguments(), "namespace"),
                    requiredString(invocation.arguments(), "mode"),
                    requiredString(invocation.arguments(), "script"),
                    params,
                    VALIDATION_AUTHORITY,
                    context(invocation));
            return new AnalyticsComposeFunctionRequest(
                    validated.namespace(),
                    validated.mode(),
                    validated.script(),
                    validated.params(),
                    authority(invocation, operation),
                    validated.context());
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

    private static Map<String, Object> object(String field, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ArgumentsInvalid();
        }
        try {
            return AnalyticsFunctionJsonValues.normalizeObject(field, map);
        } catch (IllegalArgumentException invalid) {
            throw new ArgumentsInvalid();
        }
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
