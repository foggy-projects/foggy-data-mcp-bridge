package com.foggyframework.dataset.db.model.engine.compose.authority;

import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AuthorityBindingResolver · Odoo remote binding adapter")
class AuthorityBindingResolverTest {

    @Test
    @DisplayName("valid binding parses native ModelBinding")
    void validBindingParsesNativeModelBinding() {
        AuthorityResolution resolution = resolver(validEnvelope()).resolve(request("u1", "t1", "odoo", "SalesQM"));

        ModelBinding binding = resolution.bindings().get("SalesQM");
        assertEquals(List.of("amount"), binding.fieldAccess());
        assertEquals(1, binding.deniedColumns().size());
        assertNull(binding.deniedColumns().get(0).getSchema());
        assertEquals("fact_sales", binding.deniedColumns().get(0).getTable());
        assertEquals("secret_amount", binding.deniedColumns().get(0).getColumn());
        assertEquals(1, binding.systemSlice().size());
        assertEquals("customer_key", binding.systemSlice().get(0).getField());
        assertEquals("=", binding.systemSlice().get(0).getOp());
        assertEquals(42, binding.systemSlice().get(0).getValue());
    }

    @Test
    @DisplayName("missing model binding fails closed")
    void missingModelBindingFailsClosed() {
        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(validEnvelope()).resolve(request("u1", "t1", "odoo", "OtherQM")));
        assertEquals(AuthorityErrorCodes.MODEL_BINDING_MISSING, ex.code());
        assertEquals("OtherQM", ex.modelInvolved());
    }

    @Test
    @DisplayName("principal mismatch fails closed")
    void principalMismatchFailsClosed() {
        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(validEnvelope()).resolve(request("u2", "t1", "odoo", "SalesQM")));
        assertEquals(AuthorityErrorCodes.PRINCIPAL_MISMATCH, ex.code());
    }

    @Test
    @DisplayName("tenant mismatch fails closed")
    void tenantMismatchFailsClosed() {
        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(validEnvelope()).resolve(request("u1", "t2", "odoo", "SalesQM")));
        assertEquals(AuthorityErrorCodes.PRINCIPAL_MISMATCH, ex.code());
    }

    @Test
    @DisplayName("namespace mismatch fails closed")
    void namespaceMismatchFailsClosed() {
        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(validEnvelope()).resolve(request("u1", "t1", "default", "SalesQM")));
        assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
    }

    @Test
    @DisplayName("malformed deniedColumns array fails closed")
    void malformedDeniedColumnsFailsClosed() {
        Map<String, Object> envelope = validEnvelope();
        binding(envelope).put("deniedColumns", Map.of("table", "fact_sales", "column", "secret_amount"));

        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(envelope).resolve(request("u1", "t1", "odoo", "SalesQM")));
        assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
    }

    @Test
    @DisplayName("empty deniedColumns table or column fails closed")
    void emptyDeniedColumnFailsClosed() {
        Map<String, Object> envelope = validEnvelope();
        binding(envelope).put("deniedColumns", List.of(Map.of("table", "fact_sales", "column", "   ")));

        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(envelope).resolve(request("u1", "t1", "odoo", "SalesQM")));
        assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
    }

    @Test
    @DisplayName("invalid issuer fails closed at construction")
    void invalidIssuerFailsClosed() {
        Map<String, Object> envelope = validEnvelope();
        envelope.put("issuer", "unknown");

        AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                () -> resolver(envelope));
        assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> binding(Map<String, Object> envelope) {
        return (Map<String, Object>) ((Map<String, Object>) envelope.get("bindings")).get("SalesQM");
    }

    private static AuthorityBindingResolver resolver(Map<String, Object> envelope) {
        return new AuthorityBindingResolver(envelope, "odoo");
    }

    private static AuthorityRequest request(String userId, String tenantId, String namespace, String model) {
        return AuthorityRequest.builder()
                .principal(Principal.builder().userId(userId).tenantId(tenantId).roles(List.of("analyst")).build())
                .namespace(namespace)
                .traceId("trace-1")
                .models(List.of(ModelQuery.builder().model(model).tables(List.of("fact_sales")).build()))
                .build();
    }

    private static Map<String, Object> validEnvelope() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("fieldAccess", List.of("amount"));
        binding.put("deniedColumns", List.of(Map.of(
                "schema", " ",
                "table", " fact_sales ",
                "column", " secret_amount "
        )));
        binding.put("systemSlice", List.of(Map.of(
                "field", "customer_key",
                "op", "=",
                "value", 42
        )));

        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("SalesQM", binding);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", AuthorityBindingResolver.VERSION);
        envelope.put("issuer", AuthorityBindingResolver.ISSUER_TEST_FIXTURE);
        envelope.put("namespace", "odoo");
        envelope.put("tenantId", "t1");
        envelope.put("principal", Map.of("userId", "u1", "tenantId", "t1"));
        envelope.put("bindings", bindings);
        return envelope;
    }
}
