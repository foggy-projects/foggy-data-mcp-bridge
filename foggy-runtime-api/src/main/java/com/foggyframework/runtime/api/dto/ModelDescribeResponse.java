package com.foggyframework.runtime.api.dto;

import java.util.Map;

public record ModelDescribeResponse(
        String format,
        String content,
        Map<String, Object> data
) {
}
