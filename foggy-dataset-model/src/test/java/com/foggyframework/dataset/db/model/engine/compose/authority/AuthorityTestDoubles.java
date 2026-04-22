package com.foggyframework.dataset.db.model.engine.compose.authority;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelQuery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test doubles for M5 Authority pipeline tests.
 *
 * <p>Mirrors the fakes in Python {@code tests/compose/authority/test_resolve_authority_for_plan.py}.
 * Package-private — shared across the 4 M5 test classes.</p>
 */
final class AuthorityTestDoubles {

    private AuthorityTestDoubles() { /* utility */ }

    // ------------------------------------------------------------------
    // AuthorityResolver fakes
    // ------------------------------------------------------------------

    /** Returns an empty {@link ModelBinding} for each requested model. Counts calls. */
    static final class EchoResolver implements AuthorityResolver {
        final List<AuthorityRequest> calls = new ArrayList<>();

        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            calls.add(request);
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (ModelQuery mq : request.models()) {
                bindings.put(mq.model(), ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        }
    }

    /** Raises {@link AuthorityResolutionException} — propagation test. */
    static final class RaisingResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            throw new AuthorityResolutionException(
                    AuthorityErrorCodes.IR_RULE_UNMAPPED_FIELD,
                    "ir.rule references unmapped QM field",
                    request.models().get(0).model(),
                    AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
        }
    }

    /** Raises a plain {@link RuntimeException} — must surface as UPSTREAM_FAILURE. */
    static final class BoomResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            throw new RuntimeException("kaboom");
        }
    }

    /** Returns null — INVALID_RESPONSE path. */
    static final class NullResponseResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            return null;
        }
    }

    /** Returns a binding set with an extra key — INVALID_RESPONSE. */
    static final class ExtraKeyResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (ModelQuery mq : request.models()) {
                bindings.put(mq.model(), ModelBinding.builder().build());
            }
            bindings.put("PhantomModel", ModelBinding.builder().build());
            return AuthorityResolution.builder().bindings(bindings).build();
        }
    }

    /** Returns a binding set that omits non-first models — MODEL_BINDING_MISSING. */
    static final class MissingKeyResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            bindings.put(request.models().get(0).model(), ModelBinding.builder().build());
            return AuthorityResolution.builder().bindings(bindings).build();
        }
    }

    /**
     * Returns an {@link AuthorityResolution} whose internal bindings map
     * has been swapped via reflection to contain a non-{@link ModelBinding}
     * value. Mirrors the Python test that uses {@code object.__setattr__}
     * to bypass the frozen dataclass ctor.
     *
     * <p>This is a defensive-check test: the pipeline must not assume the
     * resolver used the real ctor.</p>
     */
    static final class BadValueResolver implements AuthorityResolver {
        @Override
        public AuthorityResolution resolve(AuthorityRequest request) {
            // Build a valid AuthorityResolution first, then overwrite its
            // private 'bindings' field via reflection to inject a non-
            // ModelBinding value.
            AuthorityResolution res = AuthorityResolution.builder()
                    .bindings(Map.of(request.models().get(0).model(),
                            ModelBinding.builder().build()))
                    .build();
            try {
                Field f = AuthorityResolution.class.getDeclaredField("bindings");
                f.setAccessible(true);
                Map<String, Object> bad = new HashMap<>();
                bad.put(request.models().get(0).model(), "not a binding");
                f.set(res, Collections.unmodifiableMap(bad));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("reflective fake setup failed", e);
            }
            return res;
        }
    }

    // ------------------------------------------------------------------
    // ModelInfoProvider fakes
    // ------------------------------------------------------------------

    /** Returns a fixed table list per model — simulates a real JoinGraph lookup. */
    static final class StaticTableProvider implements ModelInfoProvider {
        private final Map<String, List<String>> mapping;

        StaticTableProvider(Map<String, List<String>> mapping) {
            this.mapping = mapping;
        }

        @Override
        public Optional<List<String>> getTablesForModel(String modelName, String namespace) {
            List<String> tables = mapping.get(modelName);
            return tables == null ? Optional.empty() : Optional.of(tables);
        }
    }

    /** Always returns {@link Optional#empty()} ("model unknown"). */
    static final class EmptyReturningProvider implements ModelInfoProvider {
        @Override
        public Optional<List<String>> getTablesForModel(String modelName, String namespace) {
            return Optional.empty();
        }
    }
}
