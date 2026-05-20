package com.foggyframework.dataset.mcp.experience;

import java.util.Locale;

public enum ExperienceRecipeStatus {
    NONE("none"),
    DRAFT("draft"),
    CANDIDATE("candidate"),
    VALIDATED("validated"),
    DEPRECATED("deprecated"),
    REJECTED("rejected");

    private final String wireValue;

    ExperienceRecipeStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ExperienceRecipeStatus fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ExperienceRecipeStatus status : values()) {
            if (status.wireValue.equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported experience recipe status: " + value);
    }
}
