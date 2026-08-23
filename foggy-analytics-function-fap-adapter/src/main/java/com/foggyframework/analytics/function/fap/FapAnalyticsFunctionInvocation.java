package com.foggyframework.analytics.function.fap;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal trusted projection extracted from a FAP provider callback.
 *
 * <p>Task, Execution, Conversation, Ask lifecycle and callback credentials are
 * deliberately absent. The product callback endpoint remains responsible for
 * authenticating and validating the complete FAP wire request before creating
 * this value.</p>
 */
public record FapAnalyticsFunctionInvocation(
        String serviceProviderContractVersion,
        String requestId,
        String functionInvocationId,
        String functionRef,
        Map<String, Object> arguments,
        String requestDigest,
        Caller caller) {

    public FapAnalyticsFunctionInvocation {
        serviceProviderContractVersion = FapAnalyticsValues.text(
                "serviceProviderContractVersion",
                serviceProviderContractVersion,
                128);
        requestId = FapAnalyticsValues.opaqueId("requestId", requestId);
        functionInvocationId = FapAnalyticsValues.opaqueId(
                "functionInvocationId", functionInvocationId);
        functionRef = FapAnalyticsValues.functionRef(functionRef);
        arguments = FapAnalyticsValues.object(
                "arguments", Objects.requireNonNull(arguments, "arguments"));
        requestDigest = FapAnalyticsValues.digest(
                "requestDigest", requestDigest);
        caller = Objects.requireNonNull(caller, "caller");
    }

    /** Subject projection used only to resolve an opaque product authority binding. */
    public record Caller(
            String providerRef,
            String tenantRef,
            String providerSubjectRef,
            String externalSubjectRef) {

        public Caller {
            providerRef = FapAnalyticsValues.opaqueId(
                    "caller.providerRef", providerRef);
            tenantRef = FapAnalyticsValues.opaqueId(
                    "caller.tenantRef", tenantRef);
            providerSubjectRef = FapAnalyticsValues.opaqueId(
                    "caller.providerSubjectRef", providerSubjectRef);
            externalSubjectRef = FapAnalyticsValues.opaqueId(
                    "caller.externalSubjectRef", externalSubjectRef);
        }
    }
}
