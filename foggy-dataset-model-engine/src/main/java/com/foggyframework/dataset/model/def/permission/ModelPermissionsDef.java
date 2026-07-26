package com.foggyframework.dataset.model.def.permission;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

import java.util.Locale;

/**
 * Query-model level action authorization declaration.
 *
 * <p>Omitted declarations and {@code mode: public} are equivalent. Resolver
 * mode requires one resolver returning an explicit allow decision.</p>
 */
@Data
public class ModelPermissionsDef {

    String mode;

    FsscriptFunction resolver;

    public Mode resolvedMode() {
        if (mode == null || mode.isBlank()) {
            if (resolver != null) {
                throw new IllegalArgumentException("modelPermissions.mode is required when resolver is configured");
            }
            return Mode.PUBLIC;
        }
        final Mode resolved;
        try {
            resolved = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "modelPermissions.mode must be 'public' or 'resolver'", ex);
        }
        if (resolved == Mode.PUBLIC && resolver != null) {
            throw new IllegalArgumentException("modelPermissions.resolver is not allowed in public mode");
        }
        if (resolved == Mode.RESOLVER && resolver == null) {
            throw new IllegalArgumentException("modelPermissions.resolver is required in resolver mode");
        }
        return resolved;
    }

    public enum Mode {
        PUBLIC,
        RESOLVER
    }
}
