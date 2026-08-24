package com.foggyframework.analytics.console.security;

import jakarta.servlet.http.HttpServletRequest;

/** Host authentication adapter; browsers cannot submit roles or authority handles. */
@FunctionalInterface
public interface AnalyticsConsoleSubjectResolver {

    AnalyticsConsoleSubject resolve(HttpServletRequest request);
}
