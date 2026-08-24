package com.foggyframework.analytics.console.agent;

import com.foggyframework.analytics.console.security.AnalyticsConsoleSubject;
import com.foggyframework.analytics.function.fap.FapAnalyticsFunctionInvocation;

/** Server-side resolver for opaque FAP credentials/workspaces and callback Subjects. */
public interface AnalyticsConsoleFapBindingResolver {

    OutboundBinding resolve(AnalyticsConsoleSubject subject);

    AnalyticsConsoleSubject resolveCaller(FapAnalyticsFunctionInvocation.Caller caller);

    record OutboundBinding(
            String authorization,
            String workspaceRef,
            String modelConfigRef,
            String modelVariantId) {

        public OutboundBinding {
            authorization = required(authorization, "authorization");
            workspaceRef = required(workspaceRef, "workspaceRef");
            modelConfigRef = required(modelConfigRef, "modelConfigRef");
            modelVariantId = required(modelVariantId, "modelVariantId");
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank() || !value.equals(value.trim())) {
                throw new IllegalArgumentException(field + " must be non-blank and trimmed");
            }
            return value;
        }
    }
}
