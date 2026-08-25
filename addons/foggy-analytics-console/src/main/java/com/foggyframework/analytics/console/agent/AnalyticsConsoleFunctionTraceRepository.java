package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/** Stores raw payloads for Analytics Functions executed by this Console only. */
public interface AnalyticsConsoleFunctionTraceRepository {

    void save(FunctionTrace trace);

    List<FunctionTrace> findByTurn(String conversationId, String askInvocationRef);

    static AnalyticsConsoleFunctionTraceRepository none() {
        return NoopHolder.INSTANCE;
    }

    record FunctionTrace(
            String conversationId,
            String askRequestId,
            String askInvocationRef,
            String functionInvocationId,
            String functionRef,
            JsonNode arguments,
            JsonNode result,
            int httpStatus) {

        public FunctionTrace {
            conversationId = required(conversationId, "conversationId");
            askRequestId = required(askRequestId, "askRequestId");
            askInvocationRef = required(askInvocationRef, "askInvocationRef");
            functionInvocationId = required(functionInvocationId, "functionInvocationId");
            functionRef = required(functionRef, "functionRef");
            arguments = object(arguments, "arguments");
            result = object(result, "result");
            if (httpStatus < 100 || httpStatus > 599) {
                throw new IllegalArgumentException("httpStatus is invalid");
            }
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank() || !value.equals(value.trim())) {
                throw new IllegalArgumentException(field + " must be non-blank and trimmed");
            }
            return value;
        }

        private static JsonNode object(JsonNode value, String field) {
            Objects.requireNonNull(value, field);
            if (!value.isObject()) {
                throw new IllegalArgumentException(field + " must be a JSON object");
            }
            return value.deepCopy();
        }
    }

    final class NoopHolder {
        private static final AnalyticsConsoleFunctionTraceRepository INSTANCE =
                new AnalyticsConsoleFunctionTraceRepository() {
                    @Override
                    public void save(FunctionTrace trace) {
                        Objects.requireNonNull(trace, "trace");
                    }

                    @Override
                    public List<FunctionTrace> findByTurn(
                            String conversationId,
                            String askInvocationRef) {
                        return List.of();
                    }
                };

        private NoopHolder() {
        }
    }
}
