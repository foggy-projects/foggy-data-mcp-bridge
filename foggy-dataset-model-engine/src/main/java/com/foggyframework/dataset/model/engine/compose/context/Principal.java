package com.foggyframework.dataset.model.engine.compose.context;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Principal — identity information for a Compose Query execution.
 *
 * <p>Immutable value object. Intended to be constructed once by the MCP layer
 * from {@code ToolExecutionContext}/HTTP headers and threaded through every
 * {@link ComposeQueryContext} / {@code QueryPlan} in a script invocation.</p>
 *
 * <p>Cross-repo invariant: field names mirror Python
 * {@code foggy.dataset_model.engine.compose.context.Principal}; only casing
 * convention differs (camelCase vs snake_case).</p>
 *
 * <p>Intentional design choices:
 * <ul>
 *   <li>No Lombok — SPI boundary types are kept explicit so reviewers can
 *       see the exact contract without IDE help.</li>
 *   <li>Explicit Builder — guards against constructor-parameter drift as
 *       optional fields accumulate.</li>
 *   <li>{@code roles} stored as an unmodifiable copy; never null.</li>
 * </ul></p>
 *
 * @since 8.2.0.beta
 */
public final class Principal {

    private final String userId;
    private final String tenantId;
    private final List<String> roles;
    private final String deptId;
    private final String authorizationHint;
    private final String policySnapshotId;

    private Principal(Builder b) {
        if (b.userId == null || b.userId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Principal.userId must be non-empty");
        }
        this.userId = b.userId;
        this.tenantId = b.tenantId;
        // Defensive copy + unmodifiable; null input becomes empty list, never null.
        this.roles = b.roles == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(List.copyOf(b.roles));
        this.deptId = b.deptId;
        this.authorizationHint = b.authorizationHint;
        this.policySnapshotId = b.policySnapshotId;
    }

    public String userId() { return userId; }
    public String tenantId() { return tenantId; }
    public List<String> roles() { return roles; }
    public String deptId() { return deptId; }
    public String authorizationHint() { return authorizationHint; }
    public String policySnapshotId() { return policySnapshotId; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String userId;
        private String tenantId;
        private List<String> roles;
        private String deptId;
        private String authorizationHint;
        private String policySnapshotId;

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder roles(List<String> v) { this.roles = v; return this; }
        public Builder deptId(String v) { this.deptId = v; return this; }
        public Builder authorizationHint(String v) { this.authorizationHint = v; return this; }
        public Builder policySnapshotId(String v) { this.policySnapshotId = v; return this; }

        public Principal build() { return new Principal(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Principal)) return false;
        Principal p = (Principal) o;
        return Objects.equals(userId, p.userId)
                && Objects.equals(tenantId, p.tenantId)
                && Objects.equals(roles, p.roles)
                && Objects.equals(deptId, p.deptId)
                && Objects.equals(authorizationHint, p.authorizationHint)
                && Objects.equals(policySnapshotId, p.policySnapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId, roles, deptId,
                authorizationHint, policySnapshotId);
    }

    @Override
    public String toString() {
        // authorizationHint intentionally omitted from toString to avoid
        // leaking header values into logs.
        return "Principal{userId=" + userId
                + ", tenantId=" + tenantId
                + ", roles=" + roles
                + ", deptId=" + deptId
                + ", policySnapshotId=" + policySnapshotId
                + '}';
    }
}
