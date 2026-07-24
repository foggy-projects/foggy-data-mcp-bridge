package com.foggyframework.dataset.model.lifecycle.port;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/** Model-owned port implemented by Runtime/MCP datasource registries. */
public interface DatasourceBindingResolver {

    ResolvedDatasourceBinding resolveBinding(String name);

    default ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
        return null;
    }

    /**
     * Compares a previously captured logical identity with this resolver's
     * current registry view. Implementations must not probe a physical
     * connection to answer this question.
     */
    default BindingCurrentness currentness(DatasourceBindingIdentity identity) {
        return BindingCurrentness.UNKNOWN;
    }

    /**
     * Runs the final catalog publication while every supplied binding identity
     * is still current.
     *
     * <p>An implementation that issues cacheable binding identities must
     * serialize this callback with the logical mutation that replaces or
     * removes those identities. A check followed by an unlocked callback is
     * not sufficient: it would allow a rebind to commit between validation and
     * the catalog swap. The conservative default supports only an empty set so
     * older, untracked resolvers remain compatible without creating a false
     * atomicity guarantee.</p>
     */
    default <T> T publishIfCurrent(
            Collection<DatasourceBindingIdentity> identities,
            Supplier<T> publication
    ) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(publication, "publication");
        if (identities.isEmpty()) {
            return publication.get();
        }
        throw new IllegalStateException(
                "DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE");
    }
}
