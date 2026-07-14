package com.foggyframework.dataset.db.model.lifecycle.port;

import com.foggyframework.dataset.db.model.lifecycle.identity.SourceRevision;

import java.util.function.Supplier;

/**
 * Atomic guard for the externally committed source registry view.
 *
 * <p>The callback must run in the same critical section as the final revision
 * comparison. Implementations therefore cannot implement this contract as an
 * unlocked {@code currentSourceRevision()} check followed by a callback.</p>
 */
public interface CommittedSourceRevisionGuard extends SourceRevisionProvider {

    <T> T publishIfCurrent(
            String namespace,
            SourceRevision expected,
            Supplier<T> publication
    );
}
