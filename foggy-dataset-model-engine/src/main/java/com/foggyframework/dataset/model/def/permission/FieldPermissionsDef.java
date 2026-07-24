package com.foggyframework.dataset.model.def.permission;

import lombok.Data;

import java.util.List;

/**
 * Declarative dynamic field permissions for table/query models.
 */
@Data
public class FieldPermissionsDef {

    /**
     * Whether fields are visible before matched rules are applied.
     * Null defaults to true for backward-compatible opt-in behavior.
     */
    Boolean defaultVisible;

    /**
     * Rules that add fields to the visible set when matched.
     */
    List<FieldPermissionRuleDef> visibleFields;

    /**
     * Rules that remove fields from the visible set when matched.
     * Hidden rules always win over visible rules.
     */
    List<FieldPermissionRuleDef> hiddenFields;

    public boolean isEmpty() {
        return defaultVisible == null
                && (visibleFields == null || visibleFields.isEmpty())
                && (hiddenFields == null || hiddenFields.isEmpty());
    }
}
