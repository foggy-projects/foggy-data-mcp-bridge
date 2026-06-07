package com.foggyframework.runtime.api.dto;

import java.util.List;

public record TablePrimaryKeyInfo(
        String name,
        List<String> columns
) {
}
