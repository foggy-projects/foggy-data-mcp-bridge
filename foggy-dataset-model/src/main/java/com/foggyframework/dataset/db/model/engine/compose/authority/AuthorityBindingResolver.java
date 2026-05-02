package com.foggyframework.dataset.db.model.engine.compose.authority;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Request-scoped resolver for host-pushed remote compose authority bindings.
 *
 * <p>The envelope is produced by foggy-odoo-bridge-pro and injected as a
 * host-private MCP argument. This resolver validates the envelope and converts
 * each per-model binding into the native compose {@link ModelBinding}
 * contract. Any malformed or divergent authority input fails closed.</p>
 */
public final class AuthorityBindingResolver implements AuthorityResolver {

    public static final String VERSION = "foggy.compose.authority-binding.v1";
    public static final String ISSUER_ODOO_BRIDGE = "foggy-odoo-bridge-pro";
    public static final String ISSUER_TEST_FIXTURE = "test-fixture-issuer";

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            ISSUER_ODOO_BRIDGE,
            ISSUER_TEST_FIXTURE
    );

    private final Map<String, Object> envelope;
    private final String expectedNamespace;
    private final Map<String, Object> envelopePrincipal;
    private final Map<String, Object> bindings;

    public AuthorityBindingResolver(Object envelope, String expectedNamespace) {
        this.expectedNamespace = normalizeOptional(expectedNamespace);
        this.envelope = requireMap(envelope, "authority binding envelope", null);
        validateTopLevelEnvelope();
        this.envelopePrincipal = requireMap(this.envelope.get("principal"), "principal", null);
        Object rawBindings = this.envelope.get("bindings");
        this.bindings = requireMap(rawBindings, "bindings", null);
    }

    @Override
    public AuthorityResolution resolve(AuthorityRequest request) {
        if (request == null) {
            throw invalid("authority request is required", null);
        }
        validateRequestIdentity(request);

        Map<String, ModelBinding> resolved = new LinkedHashMap<>();
        for (String modelName : request.modelNames()) {
            Object rawBinding = bindings.get(modelName);
            if (rawBinding == null) {
                throw new AuthorityResolutionException(
                        AuthorityErrorCodes.MODEL_BINDING_MISSING,
                        "authority binding missing for requested model",
                        modelName,
                        AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
            }
            Map<String, Object> bindingMap = requireMap(rawBinding, "binding", modelName);
            resolved.put(modelName, parseModelBinding(modelName, bindingMap));
        }
        return AuthorityResolution.builder().bindings(resolved).build();
    }

    private void validateTopLevelEnvelope() {
        String version = requireNonBlankString(envelope.get("version"), "version", null);
        if (!VERSION.equals(version)) {
            throw invalid("unsupported authority binding version", null);
        }

        String issuer = requireNonBlankString(envelope.get("issuer"), "issuer", null);
        if (!ALLOWED_ISSUERS.contains(issuer)) {
            throw invalid("unsupported authority binding issuer", null);
        }

        String namespace = requireNonBlankString(envelope.get("namespace"), "namespace", null);
        if (expectedNamespace != null && !Objects.equals(expectedNamespace, namespace)) {
            throw invalid("authority binding namespace mismatch", null);
        }
    }

    private void validateRequestIdentity(AuthorityRequest request) {
        String envelopeNamespace = requireNonBlankString(envelope.get("namespace"), "namespace", null);
        if (!Objects.equals(envelopeNamespace, normalizeOptional(request.namespace()))) {
            throw invalid("request namespace differs from authority binding", null);
        }

        Principal requestPrincipal = request.principal();
        String bindingUserId = requireNonBlankString(readEither(envelopePrincipal, "userId", "user_id"),
                "principal.userId", null);
        if (!Objects.equals(bindingUserId, requestPrincipal.userId())) {
            throw principalMismatch("authority binding principal differs from request principal");
        }

        String principalTenant = normalizeOptional(readEither(envelopePrincipal, "tenantId", "tenant_id"));
        String envelopeTenant = normalizeOptional(readEither(envelope, "tenantId", "tenant_id"));
        String bindingTenant = principalTenant != null ? principalTenant : envelopeTenant;
        if (principalTenant != null && envelopeTenant != null && !Objects.equals(principalTenant, envelopeTenant)) {
            throw principalMismatch("authority binding tenant fields diverge");
        }
        if (bindingTenant != null && !Objects.equals(bindingTenant, normalizeOptional(requestPrincipal.tenantId()))) {
            throw principalMismatch("authority binding tenant differs from request principal");
        }
    }

    private ModelBinding parseModelBinding(String modelName, Map<String, Object> bindingMap) {
        return ModelBinding.builder()
                .fieldAccess(parseFieldAccess(bindingMap.get("fieldAccess"), modelName))
                .deniedColumns(parseDeniedColumns(bindingMap.get("deniedColumns"), modelName))
                .systemSlice(parseSystemSlice(bindingMap.get("systemSlice"), modelName))
                .build();
    }

    private List<String> parseFieldAccess(Object raw, String modelName) {
        if (raw == null) {
            return null;
        }
        List<?> list = requireList(raw, "fieldAccess", modelName);
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            String field = requireNonBlankString(item, "fieldAccess item", modelName);
            result.add(field);
        }
        return result;
    }

    private List<DeniedPhysicalColumn> parseDeniedColumns(Object raw, String modelName) {
        if (raw == null) {
            return List.of();
        }
        List<?> list = requireList(raw, "deniedColumns", modelName);
        List<DeniedPhysicalColumn> result = new ArrayList<>(list.size());
        for (Object item : list) {
            Map<String, Object> map = requireMap(item, "deniedColumns item", modelName);
            String schema = normalizeOptional(readEither(map, "schema", "schemaName"));
            String table = requireNonBlankString(map.get("table"), "deniedColumns.table", modelName);
            String column = requireNonBlankString(map.get("column"), "deniedColumns.column", modelName);
            result.add(new DeniedPhysicalColumn(schema, table, column));
        }
        return result;
    }

    private List<SliceRequestDef> parseSystemSlice(Object raw, String modelName) {
        if (raw == null) {
            return List.of();
        }
        List<?> list = requireList(raw, "systemSlice", modelName);
        List<SliceRequestDef> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(parseSlice(item, modelName));
        }
        return result;
    }

    private SliceRequestDef parseSlice(Object raw, String modelName) {
        CondRequestDef cond = parseCondition(raw, modelName);
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField(cond.getField());
        slice.setOp(cond.getOp());
        slice.setValue(cond.getValue());
        slice.setMaxDepth(cond.getMaxDepth());
        slice.setOr(cond.getOr());
        slice.setAnd(cond.getAnd());
        slice.setExpr(cond.getExpr());
        return slice;
    }

    private CondRequestDef parseCondition(Object raw, String modelName) {
        Map<String, Object> map = requireMap(raw, "systemSlice item", modelName);
        CondRequestDef cond = new CondRequestDef();
        if (map.containsKey("$or")) {
            cond.setOr(parseConditionList(map.get("$or"), modelName));
            return cond;
        }
        if (map.containsKey("$and")) {
            cond.setAnd(parseConditionList(map.get("$and"), modelName));
            return cond;
        }
        if (map.containsKey("$expr")) {
            cond.setExpr(requireNonBlankString(map.get("$expr"), "systemSlice.$expr", modelName));
            return cond;
        }

        cond.setField(requireNonBlankString(map.get("field"), "systemSlice.field", modelName));
        cond.setOp(requireNonBlankString(readEither(map, "op", "type"), "systemSlice.op", modelName));
        cond.setValue(map.get("value"));
        Object maxDepth = map.get("maxDepth");
        if (maxDepth != null) {
            if (!(maxDepth instanceof Number)) {
                throw invalid("systemSlice.maxDepth must be numeric", modelName);
            }
            cond.setMaxDepth(((Number) maxDepth).intValue());
        }
        return cond;
    }

    private List<CondRequestDef> parseConditionList(Object raw, String modelName) {
        List<?> list = requireList(raw, "systemSlice logical group", modelName);
        List<CondRequestDef> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(parseCondition(item, modelName));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object raw, String field, String modelName) {
        if (!(raw instanceof Map<?, ?>)) {
            throw invalidStatic(field + " must be an object", modelName);
        }
        return (Map<String, Object>) raw;
    }

    private static List<?> requireList(Object raw, String field, String modelName) {
        if (!(raw instanceof List<?>)) {
            throw invalidStatic(field + " must be an array", modelName);
        }
        return (List<?>) raw;
    }

    private static String requireNonBlankString(Object raw, String field, String modelName) {
        String value = normalizeOptional(raw);
        if (value == null) {
            throw invalidStatic(field + " must be a non-empty string", modelName);
        }
        return value;
    }

    private static Object readEither(Map<String, Object> map, String first, String second) {
        if (map.containsKey(first)) {
            return map.get(first);
        }
        return map.get(second);
    }

    private static String normalizeOptional(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private AuthorityResolutionException invalid(String message, String modelName) {
        return invalidStatic(message, modelName);
    }

    private static AuthorityResolutionException invalidStatic(String message, String modelName) {
        return new AuthorityResolutionException(
                AuthorityErrorCodes.INVALID_RESPONSE,
                message,
                modelName,
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
    }

    private AuthorityResolutionException principalMismatch(String message) {
        return new AuthorityResolutionException(
                AuthorityErrorCodes.PRINCIPAL_MISMATCH,
                message,
                null,
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
    }
}
