package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.def.permission.ModelPermissionsDef;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Executes and validates query-model permission resolvers without interpreting
 * the opaque data-plane identity.
 */
@Component
public class ModelPermissionService {

    public PermissionDecision evaluate(
            QueryModel queryModel,
            String namespace,
            PermissionAction action,
            RequestIdentity identity,
            PermissionEvaluationSession session
    ) {
        Objects.requireNonNull(queryModel, "queryModel");
        Objects.requireNonNull(action, "action");
        RequestIdentity safeIdentity = identity != null ? identity : RequestIdentity.anonymous();
        PermissionEvaluationSession safeSession = session != null
                ? session
                : new PermissionEvaluationSession();
        String resource = queryModel.getName();
        return safeSession.getOrEvaluate(namespace, resource, action,
                () -> evaluateUncached(queryModel, namespace, action, safeIdentity, safeSession));
    }

    private PermissionDecision evaluateUncached(
            QueryModel queryModel,
            String namespace,
            PermissionAction action,
            RequestIdentity identity,
            PermissionEvaluationSession session
    ) {
        ModelPermissionsDef definition = queryModel.getModelPermissions();
        if (definition == null || definition.resolvedMode() == ModelPermissionsDef.Mode.PUBLIC) {
            return PermissionDecision.publicAllow();
        }

        Map<String, Object> resolverContext = new LinkedHashMap<>();
        resolverContext.put("identity", Map.of("kind", identity.kind().name()));
        resolverContext.put("authorization", identity.authorization());
        resolverContext.put("namespace", namespace);
        resolverContext.put("model", queryModel.getName());
        resolverContext.put("resource", queryModel.getName());
        resolverContext.put("action", action.name());
        resolverContext.put("traceId", session.getTraceId());
        resolverContext.put("predicate", new PermissionPredicateBuilder());

        final Object raw;
        try {
            raw = definition.getResolver().threadSafeAccept(resolverContext);
        } catch (RuntimeException ex) {
            throw ModelPermissionException.invalid(ex);
        }

        try {
            PermissionDecision decision = parseDecision(raw);
            if (decision.isExpired(Instant.now())) {
                throw new IllegalArgumentException("permission decision is already expired");
            }
            return decision;
        } catch (ModelPermissionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ModelPermissionException.invalid(ex);
        }
    }

    private PermissionDecision parseDecision(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("permission resolver must return an object");
        }
        Object allowValue = map.get("allow");
        if (!(allowValue instanceof Boolean allow)) {
            throw new IllegalArgumentException("permission resolver result requires boolean allow");
        }
        Map<String, Object> attributes = parseAttributes(map.get("attributes"));
        List<PermissionPredicate> rowPredicates = parsePredicates(map.get("rowPredicates"));
        return new PermissionDecision(
                allow,
                attributes,
                rowPredicates,
                optionalString(map.get("decisionId")),
                optionalString(map.get("policyVersion")),
                parseInstant(map.get("expiresAt")),
                optionalString(map.get("providerFingerprint")),
                false
        );
    }

    private Map<String, Object> parseAttributes(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("permission decision attributes must be an object");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("permission decision attribute key must not be null");
            }
            attributes.put(String.valueOf(entry.getKey()), freeze(entry.getValue()));
        }
        return attributes;
    }

    private List<PermissionPredicate> parsePredicates(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("permission decision rowPredicates must be an array");
        }
        List<PermissionPredicate> predicates = new ArrayList<>();
        for (Object item : collection) {
            PermissionPredicate predicate;
            if (item instanceof PermissionPredicate typed) {
                predicate = typed;
            } else if (item instanceof Map<?, ?> map) {
                predicate = parsePredicateMap(map);
            } else {
                throw new IllegalArgumentException("rowPredicates entries must be typed predicates");
            }
            if (!predicate.isProvable()) {
                throw new IllegalArgumentException("resolver row predicate must be provable");
            }
            predicates.add(predicate);
        }
        return predicates;
    }

    private PermissionPredicate parsePredicateMap(Map<?, ?> map) {
        String field = requiredString(map.get("field"), "row predicate field");
        String operator = requiredString(
                map.containsKey("operator") ? map.get("operator") : map.get("op"),
                "row predicate operator"
        );
        String originValue = optionalString(map.get("origin"));
        PermissionPredicate.Origin origin = originValue == null
                ? PermissionPredicate.Origin.QM_MODEL_PERMISSION
                : PermissionPredicate.Origin.valueOf(originValue.toUpperCase(Locale.ROOT));
        String proofValue = optionalString(map.get("proofStatus"));
        PermissionPredicate.ProofStatus proofStatus = proofValue == null
                ? PermissionPredicate.ProofStatus.PROVABLE
                : PermissionPredicate.ProofStatus.valueOf(proofValue.toUpperCase(Locale.ROOT));
        Set<String> referencedFields = toStringSet(map.get("referencedFields"));
        if (referencedFields.isEmpty()) {
            referencedFields = Set.of(field);
        }
        Object value = freeze(map.get("value"));
        String valueType = optionalString(map.get("valueType"));
        if (valueType == null) {
            valueType = value == null ? "NULL" : value.getClass().getSimpleName();
        }
        return new PermissionPredicate(
                origin,
                optionalString(map.get("binding")),
                field,
                operator,
                valueType,
                value,
                referencedFields,
                proofStatus
        );
    }

    private Instant parseInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("permission decision expiresAt must be ISO-8601 or epoch millis", ex);
        }
    }

    private Set<String> toStringSet(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException("referencedFields must be an array");
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object value : collection) {
            values.add(requiredString(value, "referenced field"));
        }
        return Set.copyOf(values);
    }

    private Object freeze(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), freeze(value)));
            return Collections.unmodifiableMap(result);
        }
        if (raw instanceof Collection<?> collection) {
            List<Object> result = collection.stream().map(this::freeze).toList();
            return Collections.unmodifiableList(new ArrayList<>(result));
        }
        return raw;
    }

    private String optionalString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String requiredString(Object value, String name) {
        String text = optionalString(value);
        if (text == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
