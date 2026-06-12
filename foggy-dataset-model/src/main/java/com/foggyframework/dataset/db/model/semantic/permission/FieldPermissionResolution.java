package com.foggyframework.dataset.db.model.semantic.permission;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

/**
 * Effective field permission resolution for one QM request/metadata context.
 */
@Data
@AllArgsConstructor
public class FieldPermissionResolution {

    /**
     * Effective QM field allowlist. Null means no whitelist restriction.
     * Entries are normalized base field names, matching FieldAccessPermissionStep.
     */
    private Set<String> effectiveFieldAccess;

    /**
     * Runtime deniedColumns mapped to normalized QM field names.
     */
    private Set<String> deniedQmFields;
}
