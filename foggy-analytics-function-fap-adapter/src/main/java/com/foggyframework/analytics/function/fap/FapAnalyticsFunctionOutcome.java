package com.foggyframework.analytics.function.fap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

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

    /** Safe non-2xx callback projection with optional allowlisted pre-effect repair details. */
    record Failure(
            String requestId,
            String functionInvocationId,
            String code,
            String message,
            boolean retryable,
            int recommendedHttpStatus,
            RepairDetails repairDetails) implements FapAnalyticsFunctionOutcome {

        public Failure(
                String requestId,
                String functionInvocationId,
                String code,
                String message,
                boolean retryable,
                int recommendedHttpStatus) {
            this(
                    requestId,
                    functionInvocationId,
                    code,
                    message,
                    retryable,
                    recommendedHttpStatus,
                    null);
        }

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
            if (repairDetails != null
                    && (!FapAnalyticsErrorCodes.FUNCTION_ARGUMENT_INVALID.equals(code)
                        || retryable
                        || recommendedHttpStatus != 422)) {
                throw new IllegalArgumentException(
                        "repair details require a non-retryable FUNCTION_ARGUMENT_INVALID / 422");
            }
        }

        @Override
        public Map<String, Object> callbackBody() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", code);
            body.put("message", message);
            if (repairDetails != null) {
                body.putAll(repairDetails.callbackFields());
            }
            return FapAnalyticsValues.object("callbackBody", body);
        }
    }

    /** Strict FAP pre-effect repair projection; raw values and validator messages are excluded. */
    record RepairDetails(
            String functionRef,
            String schemaDigest,
            List<Violation> violations,
            boolean violationsTruncated) {

        public RepairDetails {
            functionRef = FapAnalyticsValues.functionRef(functionRef);
            schemaDigest = FapAnalyticsValues.digest("schemaDigest", schemaDigest);
            violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
            if (violations.isEmpty() || violations.size() > 12) {
                throw new IllegalArgumentException(
                        "repair violations must contain between 1 and 12 entries");
            }
        }

        Map<String, Object> callbackFields() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("modelRepairable", true);
            value.put("effectPhase", "PRE_EFFECT");
            value.put("functionRef", functionRef);
            value.put("schemaDigest", schemaDigest);
            value.put("violations", violations.stream()
                    .map(Violation::callbackValue)
                    .toList());
            if (violationsTruncated) {
                value.put("violationsTruncated", true);
            }
            return value;
        }
    }

    /** Safe structural hint accepted by FAP Runtime and returned as tool data to the model. */
    record Violation(String instancePath, String keyword, String messageKey) {

        private static final Pattern INSTANCE_PATH = Pattern.compile(
                "^(?:|/(?:[A-Za-z_][A-Za-z0-9_.:-]{0,63}|[0-9]{1,6}|\\*))*$");
        private static final Pattern KEYWORD = Pattern.compile(
                "^[A-Za-z][A-Za-z0-9_-]{0,63}$");
        private static final Pattern MESSAGE_KEY = Pattern.compile(
                "^[A-Z][A-Z0-9_]{0,63}$");

        public Violation {
            if (instancePath == null
                    || instancePath.length() > 256
                    || !INSTANCE_PATH.matcher(instancePath).matches()) {
                throw new IllegalArgumentException("repair instancePath is unsafe");
            }
            if (keyword == null || !KEYWORD.matcher(keyword).matches()) {
                throw new IllegalArgumentException("repair keyword is unsafe");
            }
            if (messageKey == null || !MESSAGE_KEY.matcher(messageKey).matches()) {
                throw new IllegalArgumentException("repair messageKey is unsafe");
            }
        }

        Map<String, String> callbackValue() {
            return Map.of(
                    "instancePath", instancePath,
                    "keyword", keyword,
                    "messageKey", messageKey);
        }
    }
}
