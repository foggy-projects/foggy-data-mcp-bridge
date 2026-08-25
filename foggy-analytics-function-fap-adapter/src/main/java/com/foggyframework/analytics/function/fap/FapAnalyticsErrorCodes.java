package com.foggyframework.analytics.function.fap;

/** Adapter-owned codes accepted by FAP's safe provider callback error projection. */
public final class FapAnalyticsErrorCodes {

    /** FAP-owned pre-effect rejection code consumed by the model repair loop. */
    public static final String FUNCTION_ARGUMENT_INVALID =
            "FUNCTION_ARGUMENT_INVALID";

    public static final String CONTRACT_UNSUPPORTED =
            "ANALYTICS_FAP_CONTRACT_UNSUPPORTED";
    public static final String FUNCTION_UNKNOWN =
            "ANALYTICS_FAP_FUNCTION_UNKNOWN";
    public static final String ARGUMENTS_INVALID =
            "ANALYTICS_FAP_ARGUMENTS_INVALID";
    public static final String AUTHORITY_UNAVAILABLE =
            "ANALYTICS_FAP_AUTHORITY_UNAVAILABLE";
    public static final String PROTOCOL_ERROR =
            "ANALYTICS_FAP_PROTOCOL_ERROR";
    public static final String ADAPTER_INTERNAL =
            "ANALYTICS_FAP_ADAPTER_INTERNAL";

    private FapAnalyticsErrorCodes() {
    }
}
