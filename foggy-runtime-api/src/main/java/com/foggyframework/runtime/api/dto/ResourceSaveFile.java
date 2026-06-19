package com.foggyframework.runtime.api.dto;

public record ResourceSaveFile(
        String path,
        String content,
        String baseSha256
) {
}
