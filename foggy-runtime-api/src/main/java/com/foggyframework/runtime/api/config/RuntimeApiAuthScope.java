package com.foggyframework.runtime.api.config;

public enum RuntimeApiAuthScope {

    MUTATIONS("mutations"),
    MANAGEMENT_ALL("management-all");

    private final String propertyValue;

    RuntimeApiAuthScope(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }
}
