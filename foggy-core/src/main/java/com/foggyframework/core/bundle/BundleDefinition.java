package com.foggyframework.core.bundle;

public interface BundleDefinition {
    String getPackageName();

    String getName();

    default Class<?> getDefinitionClass() {
        return getClass();
    }
}
