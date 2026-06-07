package com.foggyframework.runtime.api.dto;

import java.util.List;

public record TableIndexInfo(
        String name,
        Boolean unique,
        List<String> columns
) {
}
