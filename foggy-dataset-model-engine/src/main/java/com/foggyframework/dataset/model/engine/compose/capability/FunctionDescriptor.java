package com.foggyframework.dataset.model.engine.compose.capability;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable descriptor for a controlled function registration.
 *
 * <p>Validated on construction; throws {@link CapabilityException.InvalidDescriptor}
 * or {@link CapabilityException.SideEffectDenied} on invalid fields.</p>
 *
 * <p>Mirrors Python {@code FunctionDescriptor} frozen dataclass.</p>
 *
 * @since 8.4.0
 */
public final class FunctionDescriptor {

    // ---------------------------------------------------------------
    // Validation constants (match Python descriptors.py)
    // ---------------------------------------------------------------

    static final Pattern SAFE_NAME_RE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    static final Set<String> VALID_FUNCTION_KINDS = Set.of("sql_scalar", "pure_runtime");
    static final Set<String> VALID_SIDE_EFFECTS = Set.of("none");
    static final Set<String> VALID_ALLOWED_IN = Set.of("formula", "compose_column", "compose_runtime");
    static final Set<String> VALID_RETURN_TYPES = Set.of(
            "string", "int", "float", "bool", "date", "datetime",
            "dict", "list", "null"
    );

    /** Reserved names that cannot be overridden by capabilities. */
    static final Set<String> RESERVED_NAMES = Set.of(
            // Script globals
            "from", "dsl", "Query", "params",
            // Language escapes
            "eval", "exec", "import", "require",
            "__import__", "__builtins__",
            // QueryPlan methods
            "select", "where", "group_by", "order_by",
            "join", "union", "to_sql", "execute",
            // fsscript builtins
            "JSON", "parseInt", "parseFloat", "toString",
            "String", "Number", "Boolean",
            "isNaN", "isFinite", "Array", "Object", "Function",
            "typeof",
            // Additional safety
            "self", "cls", "None", "True", "False"
    );

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    private final String name;
    private final String kind;
    private final List<Map<String, Object>> argsSchema;
    private final String returnType;
    private final boolean deterministic;
    private final String sideEffect;
    private final List<String> allowedIn;
    private final String auditTag;
    private final List<String> dialects;

    // ---------------------------------------------------------------
    // Constructor (with validation)
    // ---------------------------------------------------------------

    public FunctionDescriptor(
            String name,
            String kind,
            List<Map<String, Object>> argsSchema,
            String returnType,
            boolean deterministic,
            String sideEffect,
            List<String> allowedIn,
            String auditTag,
            List<String> dialects) {

        validateName(name, "Function");

        if (!VALID_FUNCTION_KINDS.contains(kind)) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function kind must be one of " + VALID_FUNCTION_KINDS + "; got '" + kind + "'.");
        }

        validateSideEffect(sideEffect);
        validateReturnType(returnType);

        if (allowedIn == null || allowedIn.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "': allowedIn must not be empty.");
        }
        for (String surface : allowedIn) {
            if (!VALID_ALLOWED_IN.contains(surface)) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + name + "': allowedIn value '" + surface
                                + "' is not recognized. Allowed: " + VALID_ALLOWED_IN + ".");
            }
        }

        if (auditTag == null || auditTag.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "': auditTag must not be empty.");
        }

        if ("sql_scalar".equals(kind)) {
            if (dialects == null || dialects.isEmpty()) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + name + "': sql_scalar functions must declare at least one dialect.");
            }
        }

        if (argsSchema == null) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "': argsSchema must not be null.");
        }
        for (int i = 0; i < argsSchema.size(); i++) {
            Map<String, Object> arg = argsSchema.get(i);
            if (arg == null) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + name + "': argsSchema[" + i + "] must not be null.");
            }
            if (!arg.containsKey("name")) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + name + "': argsSchema[" + i + "] missing 'name'.");
            }
            if (!arg.containsKey("type")) {
                throw new CapabilityException.InvalidDescriptor(
                        "Function '" + name + "': argsSchema[" + i + "] missing 'type'.");
            }
        }

        this.name = name;
        this.kind = kind;
        this.argsSchema = Collections.unmodifiableList(List.copyOf(argsSchema));
        this.returnType = returnType;
        this.deterministic = deterministic;
        this.sideEffect = sideEffect;
        this.allowedIn = Collections.unmodifiableList(List.copyOf(allowedIn));
        this.auditTag = auditTag;
        this.dialects = dialects == null ? List.of() : Collections.unmodifiableList(List.copyOf(dialects));
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------

    public String getName()                         { return name; }
    public String getKind()                         { return kind; }
    public List<Map<String, Object>> getArgsSchema() { return argsSchema; }
    public String getReturnType()                   { return returnType; }
    public boolean isDeterministic()                { return deterministic; }
    public String getSideEffect()                   { return sideEffect; }
    public List<String> getAllowedIn()               { return allowedIn; }
    public String getAuditTag()                     { return auditTag; }
    public List<String> getDialects()               { return dialects; }

    // ---------------------------------------------------------------
    // Shared validation helpers
    // ---------------------------------------------------------------

    static void validateName(String name, String label) {
        if (name == null || name.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    label + " name must not be empty.");
        }
        if (!SAFE_NAME_RE.matcher(name).matches()) {
            throw new CapabilityException.InvalidDescriptor(
                    label + " name '" + name + "' contains unsafe characters. "
                            + "Only letters, digits, and underscores are allowed, "
                            + "and it must start with a letter.");
        }
        if (RESERVED_NAMES.contains(name)) {
            throw new CapabilityException.InvalidDescriptor(
                    label + " name '" + name + "' is reserved and cannot be used.");
        }
        if (name.startsWith("__")) {
            throw new CapabilityException.InvalidDescriptor(
                    label + " name '" + name + "' must not start with double underscore.");
        }
    }

    static void validateSideEffect(String sideEffect) {
        if (!VALID_SIDE_EFFECTS.contains(sideEffect)) {
            throw new CapabilityException.SideEffectDenied(
                    "side_effect must be 'none' in v1.7; got '" + sideEffect + "'.");
        }
    }

    static void validateReturnType(String returnType) {
        if (!VALID_RETURN_TYPES.contains(returnType)) {
            throw new CapabilityException.InvalidDescriptor(
                    "return_type '" + returnType + "' is not a recognized safe type. "
                            + "Allowed: " + VALID_RETURN_TYPES + ".");
        }
    }
}
