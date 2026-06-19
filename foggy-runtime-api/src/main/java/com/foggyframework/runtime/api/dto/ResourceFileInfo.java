package com.foggyframework.runtime.api.dto;

public record ResourceFileInfo(
        String path,
        String type,
        Long size,
        String sha256,
        String content,
        Boolean writable
) {
}
