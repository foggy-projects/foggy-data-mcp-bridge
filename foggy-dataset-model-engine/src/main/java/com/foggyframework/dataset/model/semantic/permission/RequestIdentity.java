package com.foggyframework.dataset.model.semantic.permission;

import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Immutable data-plane identity. The runtime deliberately treats
 * Authorization as opaque and never interprets customer IAM claims.
 */
public record RequestIdentity(Kind kind, String authorization) {

    public enum Kind {
        ANONYMOUS,
        OPAQUE_SUBJECT
    }

    public RequestIdentity {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.ANONYMOUS) {
            authorization = null;
        } else if (!StringUtils.hasText(authorization)) {
            throw new IllegalArgumentException("OPAQUE_SUBJECT requires a non-blank authorization value");
        }
    }

    public static RequestIdentity fromAuthorization(String authorization) {
        return StringUtils.hasText(authorization)
                ? new RequestIdentity(Kind.OPAQUE_SUBJECT, authorization)
                : anonymous();
    }

    public static RequestIdentity anonymous() {
        return new RequestIdentity(Kind.ANONYMOUS, null);
    }

    public boolean isAnonymous() {
        return kind == Kind.ANONYMOUS;
    }
}
