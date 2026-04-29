package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable descriptor for a single method on an object facade.
 *
 * <p>Mirrors Python {@code MethodDescriptor} frozen dataclass.</p>
 *
 * @since 8.4.0
 */
public final class MethodDescriptor {

    private final String name;
    private final List<Map<String, Object>> argsSchema;
    private final String returnType;
    private final String sideEffect;
    private final String authScope;
    private final int timeoutMs;
    private final String auditTag;

    public MethodDescriptor(
            String name,
            List<Map<String, Object>> argsSchema,
            String returnType,
            String sideEffect,
            String authScope,
            int timeoutMs,
            String auditTag) {

        FunctionDescriptor.validateName(name, "Method");
        FunctionDescriptor.validateSideEffect(sideEffect);
        FunctionDescriptor.validateReturnType(returnType);

        if (authScope == null || authScope.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Method '" + name + "': authScope must not be empty.");
        }
        if (timeoutMs <= 0) {
            throw new CapabilityException.InvalidDescriptor(
                    "Method '" + name + "': timeoutMs must be positive; got " + timeoutMs + ".");
        }
        if (auditTag == null || auditTag.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Method '" + name + "': auditTag must not be empty.");
        }
        if (argsSchema == null) {
            throw new CapabilityException.InvalidDescriptor(
                    "Method '" + name + "': argsSchema must not be null.");
        }

        this.name = name;
        this.argsSchema = Collections.unmodifiableList(List.copyOf(argsSchema));
        this.returnType = returnType;
        this.sideEffect = sideEffect;
        this.authScope = authScope;
        this.timeoutMs = timeoutMs;
        this.auditTag = auditTag;
    }

    public String getName()                         { return name; }
    public List<Map<String, Object>> getArgsSchema() { return argsSchema; }
    public String getReturnType()                   { return returnType; }
    public String getSideEffect()                   { return sideEffect; }
    public String getAuthScope()                    { return authScope; }
    public int getTimeoutMs()                       { return timeoutMs; }
    public String getAuditTag()                     { return auditTag; }
}
