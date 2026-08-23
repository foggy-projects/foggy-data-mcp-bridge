package com.foggyframework.analytics.function.sdk;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;

/** Entry point for transport-specific Analytics Function clients. */
public final class AnalyticsFunctionClients {

    private AnalyticsFunctionClients() {
    }

    public static AnalyticsFunctionClient embedded(AnalyticsFunctionEndpoint endpoint) {
        return new EmbeddedAnalyticsFunctionClient(endpoint);
    }
}
