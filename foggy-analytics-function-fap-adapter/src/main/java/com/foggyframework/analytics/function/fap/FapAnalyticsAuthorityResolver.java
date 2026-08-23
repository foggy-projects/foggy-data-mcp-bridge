package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;

/** Host-owned bridge from an authenticated FAP Subject to one opaque Analytics authority. */
@FunctionalInterface
public interface FapAnalyticsAuthorityResolver {

    AnalyticsFunctionAuthority resolve(
            FapAnalyticsFunctionInvocation.Caller caller,
            String analyticsOperation);
}
