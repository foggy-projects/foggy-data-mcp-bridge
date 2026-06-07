package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelRefreshRequest(
        String namespace,
        List<String> models
) {
}
