package com.foggyframework.analytics.function.fap;

/** FAP wire identities supported by the optional Analytics adapter. */
public final class FapAnalyticsContract {

    public static final String SERVICE_PROVIDER_CONTRACT_VERSION =
            "fap.service-provider.v1alpha1";
    public static final String CALLBACK_REQUEST_TYPE =
            "PROVIDER_FUNCTION_CALLBACK";
    public static final String CALLBACK_RESULT_TYPE =
            "PROVIDER_FUNCTION_CALLBACK_RESULT";
    public static final String SERVICE_PROVIDER_SCHEMA_ID =
            "https://schemas.foggy.dev/agent-platform/service-provider/"
                    + "v1alpha1/service-provider.schema.json";

    private FapAnalyticsContract() {
    }
}
