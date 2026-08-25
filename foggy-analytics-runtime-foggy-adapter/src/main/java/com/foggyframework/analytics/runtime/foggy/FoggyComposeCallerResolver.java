package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.dataset.model.semantic.port.ComposeCaller;

/** Host-owned bridge from opaque Analytics authority to trusted Compose identity. */
@FunctionalInterface
public interface FoggyComposeCallerResolver {

    ComposeCaller resolve(FoggyComposeAuthorityRequest request);
}
