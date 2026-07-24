package com.foggyframework.dataset.model.web;

import com.foggyframework.dataset.model.api.backend.BackendDescriptor;

import java.util.List;
import java.util.Objects;

/** Stable HTTP representation that does not expose provider implementation classes. */
public record BackendProviderView(String backendId, List<String> capabilities) {

    public BackendProviderView {
        Objects.requireNonNull(backendId, "backendId must not be null");
        capabilities = List.copyOf(Objects.requireNonNull(
                capabilities, "capabilities must not be null"));
    }

    static BackendProviderView from(BackendDescriptor descriptor) {
        return new BackendProviderView(
                descriptor.backendId().value(),
                descriptor.capabilities().stream()
                        .map(Enum::name)
                        .sorted()
                        .toList());
    }
}
