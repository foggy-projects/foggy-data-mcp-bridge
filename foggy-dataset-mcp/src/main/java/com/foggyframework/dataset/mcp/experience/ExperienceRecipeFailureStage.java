package com.foggyframework.dataset.mcp.experience;

import java.util.Locale;

public enum ExperienceRecipeFailureStage {
    NONE("none"),
    GATE_VALIDATION("gate_validation"),
    IDEMPOTENCY_REPLAY("idempotency_replay"),
    REGISTRY_UPDATE("registry_update"),
    REGISTRY_EVENT_APPEND("registry_event_append"),
    READ_PATH("read_path");

    private final String wireValue;

    ExperienceRecipeFailureStage(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ExperienceRecipeFailureStage fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ExperienceRecipeFailureStage stage : values()) {
            if (stage.wireValue.equals(normalized)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Unsupported experience recipe failure stage: " + value);
    }
}
