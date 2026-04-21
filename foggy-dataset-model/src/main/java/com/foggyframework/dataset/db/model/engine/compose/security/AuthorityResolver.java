package com.foggyframework.dataset.db.model.engine.compose.security;

/**
 * AuthorityResolver — SPI hosts implement to bind per-model authority for a
 * Compose Query script invocation.
 *
 * <p>Host implementations live outside this module:
 * <ul>
 *   <li>{@code foggy-odoo-bridge-pro} will provide
 *       {@code OdooEmbeddedAuthorityResolver(env)} that loops over
 *       {@code compute_query_governance_with_result} per model (v1.6 REQ-001).</li>
 *   <li>The in-module {@code HttpAuthorityResolver} (delivered post-8.2.0.beta)
 *       will cover the remote-callback mode.</li>
 * </ul></p>
 *
 * <p><b>Fail-closed contract</b> (hosts MUST honour):
 * <ol>
 *   <li>Return an {@link AuthorityResolution} whose bindings key-set equals
 *       {@code request.modelNames()} as a set. Missing any key is a contract
 *       violation; callers raise {@link AuthorityResolutionException} with
 *       {@link AuthorityErrorCodes#MODEL_BINDING_MISSING}.</li>
 *   <li>On any internal failure (ir.rule evaluation error, upstream 5xx,
 *       principal mismatch, model-not-mapped, etc.), throw
 *       {@link AuthorityResolutionException} with the appropriate code.
 *       Do NOT return a partial {@code AuthorityResolution}.</li>
 *   <li>Error messages MUST be sanitised — no raw physical column names, no
 *       raw {@code ir.rule.domain_force} text, no other users' identifiers.</li>
 * </ol></p>
 *
 * @since 8.2.0.beta
 */
@FunctionalInterface
public interface AuthorityResolver {

    /**
     * Resolve per-model authority bindings for the given request.
     *
     * @param request batch request with non-empty {@code models}; even a
     *                single-model call sends {@code models=[one]}.
     * @return resolution with {@code bindings} keyed by QM model name, one
     *         entry per input model.
     * @throws AuthorityResolutionException on any contract violation or
     *                                      upstream failure.
     */
    AuthorityResolution resolve(AuthorityRequest request);
}
