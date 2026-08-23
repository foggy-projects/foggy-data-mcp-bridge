package com.foggyframework.analytics.function.fap;

import java.util.LinkedHashMap;
import java.util.Map;

/** Provider callback outcome; the product HTTP layer owns status/body serialization. */
public sealed interface FapAnalyticsFunctionOutcome
        permits FapAnalyticsFunctionOutcome.Success,
                FapAnalyticsFunctionOutcome.Failure {

    String requestId();

    String functionInvocationId();

    int recommendedHttpStatus();

    Map<String, Object> callbackBody();

    /** Exact successful ProviderFunctionCallbackResponse projection. */
    record Success(
            String requestId,
            String functionInvocationId,
            Map<String, Object> result,
            String resultDigest) implements FapAnalyticsFunctionOutcome {

        public Success {
            requestId = FapAnalyticsValues.opaqueId("requestId", requestId);
            functionInvocationId = FapAnalyticsValues.opaqueId(
                    "functionInvocationId", functionInvocationId);
            result = FapAnalyticsValues.object("result", result);
            resultDigest = FapAnalyticsValues.digest("resultDigest", resultDigest);
            if (!resultDigest.equals(FapCanonicalDigests.json(result))) {
                throw new IllegalArgumentException(
                        "resultDigest does not match the callback result");
            }
        }

        public static Success create(
                String requestId,
                String functionInvocationId,
                Map<String, Object> result) {
            Map<String, Object> normalized = FapAnalyticsValues.object(
                    "result", result);
            return new Success(
                    requestId,
                    functionInvocationId,
                    normalized,
                    FapCanonicalDigests.json(normalized));
        }

        @Override
        public int recommendedHttpStatus() {
            return 200;
        }

        @Override
        public Map<String, Object> callbackBody() {
            Map<String, Object> meta = Map.of(
                    "contractVersion",
                    FapAnalyticsContract.SERVICE_PROVIDER_CONTRACT_VERSION,
                    "requestId",
                    requestId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", FapAnalyticsContract.CALLBACK_RESULT_TYPE);
            body.put("meta", meta);
            body.put("functionInvocationId", functionInvocationId);
            body.put("result", result);
            body.put("resultDigest", resultDigest);
            return FapAnalyticsValues.object("callbackBody", body);
        }
    }

    /** Safe non-2xx callback projection; FAP records only its allowlisted code. */
    record Failure(
            String requestId,
            String functionInvocationId,
            String code,
            String message,
            boolean retryable,
            int recommendedHttpStatus) implements FapAnalyticsFunctionOutcome {

        public Failure {
            requestId = FapAnalyticsValues.opaqueId("requestId", requestId);
            functionInvocationId = FapAnalyticsValues.opaqueId(
                    "functionInvocationId", functionInvocationId);
            code = FapAnalyticsValues.safeErrorCode(code);
            message = FapAnalyticsValues.text("message", message, 2_000);
            if (recommendedHttpStatus < 400 || recommendedHttpStatus > 599) {
                throw new IllegalArgumentException(
                        "recommendedHttpStatus must be a non-2xx error status");
            }
        }

        @Override
        public Map<String, Object> callbackBody() {
            return Map.of("code", code, "message", message);
        }
    }
}
