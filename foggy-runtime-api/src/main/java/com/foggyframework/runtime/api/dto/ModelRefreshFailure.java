package com.foggyframework.runtime.api.dto;

public record ModelRefreshFailure(
        String model,
        String message
) {
}
