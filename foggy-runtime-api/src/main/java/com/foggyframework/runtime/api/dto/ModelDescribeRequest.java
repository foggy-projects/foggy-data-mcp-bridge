package com.foggyframework.runtime.api.dto;

import java.util.List;

public record ModelDescribeRequest(
        String format,
        String namespace,
        List<String> fields,
        List<Integer> levels,
        Boolean includeExamples
) {
}
