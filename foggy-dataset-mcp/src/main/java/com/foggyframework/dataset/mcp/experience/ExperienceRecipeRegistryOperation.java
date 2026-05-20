package com.foggyframework.dataset.mcp.experience;

import java.util.Locale;

public enum ExperienceRecipeRegistryOperation {
    CREATE_DRAFT_STUB("create_draft_stub"),
    PROMOTE_DRAFT_TO_CANDIDATE("promote_draft_to_candidate"),
    PUBLISH_VALIDATED("publish_validated"),
    DEPRECATE_RECIPE("deprecate_recipe"),
    REJECT_CANDIDATE("reject_candidate"),
    ROLLBACK_VALIDATED_TO_CANDIDATE("rollback_validated_to_candidate"),
    SEARCH_DISCOVERABLE_RECIPES("search_discoverable_recipes");

    private final String wireValue;

    ExperienceRecipeRegistryOperation(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ExperienceRecipeRegistryOperation fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Experience recipe registry operation cannot be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ExperienceRecipeRegistryOperation operation : values()) {
            if (operation.wireValue.equals(normalized)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Unsupported experience recipe registry operation: " + value);
    }
}
