package com.foggyframework.dataset.db.model.def.permission;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Dynamic field permission rule for TM/QM field visibility.
 */
@Data
public class FieldPermissionRuleDef {

    /**
     * Controlled predicate map evaluated against namespace and security context.
     * Empty or null means the rule always matches.
     */
    Map<String, Object> when;

    /**
     * QM/TM field names affected by this rule. Dimension suffixes such as
     * {@code $id} and {@code $caption} are normalized to the base field.
     */
    List<String> fields;
}
