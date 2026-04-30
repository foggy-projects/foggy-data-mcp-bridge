package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.util.Map;
import java.util.Set;

/**
 * Immutable runtime policy for a script execution.
 *
 * <p>The policy acts as a second gate after registration: even if a
 * capability is in the registry, it is only visible to the script if
 * the policy allows it.</p>
 *
 * <p>Default policy is empty: no capabilities are allowed.</p>
 *
 * <p>Mirrors Python {@code CapabilityPolicy} frozen dataclass.</p>
 *
 * @since 8.4.0
 */
public final class CapabilityPolicy {

    private static final CapabilityPolicy EMPTY = new CapabilityPolicy(
            Set.of(), Map.of(), Set.of(), false);

    private final Set<String> allowedFunctions;
    private final Map<String, Set<String>> allowedObjects;
    private final Set<String> allowedScopes;
    private final boolean allowScriptPause;

    public CapabilityPolicy(
            Set<String> allowedFunctions,
            Map<String, Set<String>> allowedObjects,
            Set<String> allowedScopes) {
        this(allowedFunctions, allowedObjects, allowedScopes, false);
    }

    public CapabilityPolicy(
            Set<String> allowedFunctions,
            Map<String, Set<String>> allowedObjects,
            Set<String> allowedScopes,
            boolean allowScriptPause) {
        this.allowedFunctions = allowedFunctions == null ? Set.of() : Set.copyOf(allowedFunctions);
        this.allowedObjects = allowedObjects == null ? Map.of() : Map.copyOf(allowedObjects);
        this.allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        this.allowScriptPause = allowScriptPause;
    }

    /** Return the default empty policy — no capabilities allowed. */
    public static CapabilityPolicy empty() {
        return EMPTY;
    }

    public boolean isFunctionAllowed(String name) {
        return allowedFunctions.contains(name);
    }

    public boolean isObjectAllowed(String objectName) {
        return allowedObjects.containsKey(objectName);
    }

    public boolean isMethodAllowed(String objectName, String methodName) {
        Set<String> methods = allowedObjects.get(objectName);
        if (methods == null) return false;
        return methods.contains(methodName);
    }

    public boolean isScopeAllowed(String scope) {
        return allowedScopes.contains(scope);
    }

    /** Whether the optional {@code runtime.pause(...)} script API is enabled. Default false. */
    public boolean isScriptPauseAllowed() {
        return allowScriptPause;
    }

    public Set<String> getAllowedFunctions() { return allowedFunctions; }
    public Map<String, Set<String>> getAllowedObjects() { return allowedObjects; }
    public Set<String> getAllowedScopes() { return allowedScopes; }
}
