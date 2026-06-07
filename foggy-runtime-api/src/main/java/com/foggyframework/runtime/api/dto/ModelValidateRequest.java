package com.foggyframework.runtime.api.dto;

public record ModelValidateRequest(
        String path,
        String namespace,
        Boolean watch,
        Boolean clearExisting,
        Boolean includeStackTrace
) {
}
