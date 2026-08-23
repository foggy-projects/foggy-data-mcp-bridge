package com.foggyframework.analytics.function.contract;

import java.util.Objects;

/** Transport-neutral function outcome used unchanged by embedded and HTTP clients. */
public record AnalyticsFunctionEnvelope<T>(
        boolean success,
        String engine,
        String functionContractVersion,
        String analyticsRuntimeApiVersion,
        String schemaVersion,
        T data,
        AnalyticsFunctionContext context,
        AnalyticsFunctionError error) {

    public AnalyticsFunctionEnvelope {
        engine = AnalyticsFunctionValues.requireText("engine", engine);
        functionContractVersion = AnalyticsFunctionValues.requireText(
                "functionContractVersion", functionContractVersion);
        analyticsRuntimeApiVersion = AnalyticsFunctionValues.requireText(
                "analyticsRuntimeApiVersion", analyticsRuntimeApiVersion);
        schemaVersion = AnalyticsFunctionValues.requireText(
                "schemaVersion", schemaVersion);
        context = Objects.requireNonNull(context, "context");
        if (success) {
            Objects.requireNonNull(data, "successful function data");
            if (error != null) {
                throw new IllegalArgumentException(
                        "successful function outcome must not contain an error");
            }
        } else {
            if (data != null) {
                throw new IllegalArgumentException(
                        "failed function outcome must not contain data");
            }
            Objects.requireNonNull(error, "failed function error");
        }
    }

    public static <T> AnalyticsFunctionEnvelope<T> ok(
            String runtimeApiVersion,
            String schemaVersion,
            T data,
            AnalyticsFunctionContext context) {
        return new AnalyticsFunctionEnvelope<>(
                true,
                AnalyticsFunctionContract.ENGINE,
                AnalyticsFunctionContract.VERSION,
                runtimeApiVersion,
                schemaVersion,
                data,
                context,
                null);
    }

    public static <T> AnalyticsFunctionEnvelope<T> fail(
            String runtimeApiVersion,
            String schemaVersion,
            AnalyticsFunctionError error,
            AnalyticsFunctionContext context) {
        return new AnalyticsFunctionEnvelope<>(
                false,
                AnalyticsFunctionContract.ENGINE,
                AnalyticsFunctionContract.VERSION,
                runtimeApiVersion,
                schemaVersion,
                null,
                context,
                error);
    }
}
