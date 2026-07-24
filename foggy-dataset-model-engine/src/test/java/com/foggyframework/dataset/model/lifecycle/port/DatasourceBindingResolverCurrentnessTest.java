package com.foggyframework.dataset.model.lifecycle.port;

import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourceBindingResolverCurrentnessTest {

    @Test
    void compatibilityDefaultMustRemainUnknownWithoutAdapterAuthority() {
        DatasourceBindingResolver resolver = name -> null;
        DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                "logical-binding",
                "logical-backend",
                new DatasourceBindingGeneration("generation-one")
        );

        assertEquals(BindingCurrentness.UNKNOWN, resolver.currentness(identity));
        assertEquals(BindingCurrentness.UNKNOWN, resolver.currentness(null));
    }

    @Test
    void compatibilityPublicationGuardMustFailClosedForTrackedBindings() {
        DatasourceBindingResolver resolver = name -> null;
        DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                "logical-binding",
                "logical-backend",
                new DatasourceBindingGeneration("generation-one")
        );
        AtomicBoolean publicationCalled = new AtomicBoolean();

        assertEquals("published", resolver.publishIfCurrent(
                List.of(), () -> "published"));
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> resolver.publishIfCurrent(List.of(identity), () -> {
                    publicationCalled.set(true);
                    return "unsafe";
                }));

        assertEquals("DATASOURCE_BINDING_PUBLICATION_GUARD_UNAVAILABLE",
                failure.getMessage());
        assertFalse(publicationCalled.get());
    }
}
