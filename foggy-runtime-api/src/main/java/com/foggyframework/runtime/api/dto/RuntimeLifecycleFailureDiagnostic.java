package com.foggyframework.runtime.api.dto;

/** One sanitized lifecycle failure diagnostic. */
public record RuntimeLifecycleFailureDiagnostic(
        String target,
        String phase,
        String message,
        String suggestedNextAction
) {
    public RuntimeLifecycleFailureDiagnostic {
        target = RuntimeLifecycleSanitizer.sanitizeTarget(target);
        phase = RuntimeLifecycleSanitizer.requireNonBlank(phase, "phase");
        message = RuntimeLifecycleSanitizer.sanitizeMessage(message);
        suggestedNextAction = suggestedNextAction == null
                ? null
                : RuntimeLifecycleSanitizer.sanitizeMessage(
                suggestedNextAction);
    }
}
