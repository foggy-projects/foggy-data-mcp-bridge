package com.foggyframework.runtime.api.dto;

public record AccessCheckResponse(
        boolean authenticated,
        String authScope,
        String runtimeApiVersion
) {
}
