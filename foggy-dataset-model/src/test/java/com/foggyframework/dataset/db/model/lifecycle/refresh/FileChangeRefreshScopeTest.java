package com.foggyframework.dataset.db.model.lifecycle.refresh;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.dataset.db.model.engine.query_model.DbModelFileChangeHandler;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelLoaderImpl;
import com.foggyframework.dataset.db.model.impl.loader.TableModelLoaderManagerImpl;
import com.foggyframework.fsscript.loadder.FsscriptRemoveEvent;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinitionSpace;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** File/import mutation must converge through scoped atomic refresh. */
class FileChangeRefreshScopeTest {

    private static final String NAMESPACE = "tenant-file-change";
    private static final String SOURCE_REVISION = "source:file-change:1";
    private static final String AFFECTED_RESOURCE = "/models/ChangedModel.tm";
    private static final String COORDINATOR =
            "com.foggyframework.dataset.db.model.lifecycle.refresh.CatalogRefreshCoordinator";

    @Test
    void fileMutationEventMustCarryItsCommittedSourceRevision() {
        FsscriptRemoveEvent event = knownScopeEvent();
        Object revisions = invokeEventAccessor(event, "getCommittedSourceRevisions");

        assertEquals(Map.of(NAMESPACE, SOURCE_REVISION), revisions);
    }

    @Test
    void knownFileMutationMustExposeItsExactAffectedNamespace() {
        FsscriptRemoveEvent event = knownScopeEvent();

        assertEquals(Boolean.TRUE, invokeEventAccessor(event, "isScopeKnown"));
        assertEquals(Set.of(NAMESPACE),
                invokeEventAccessor(event, "getAffectedNamespaces"));
    }

    @Test
    void unknownFileMutationScopeMustBeExplicitInsteadOfMasqueradingAsGlobalClear() {
        FsscriptRemoveEvent event = new FsscriptRemoveEvent(List.of());

        assertEquals(Boolean.FALSE, invokeEventAccessor(event, "isScopeKnown"));
        assertEquals(Set.of(), invokeEventAccessor(event, "getAffectedNamespaces"));
        assertEquals(Map.of(),
                invokeEventAccessor(event, "getCommittedSourceRevisions"));
    }

    @Test
    void fileHandlerMustCallTheCoordinatorOnceAndNeverClearLiveLoaders() {
        QueryModelLoaderImpl queryLoader = mock(QueryModelLoaderImpl.class);
        TableModelLoaderManagerImpl tableLoader = mock(TableModelLoaderManagerImpl.class);
        HandlerFixture fixture = newHandler(queryLoader, tableLoader);

        fixture.handler().onApplicationEvent(knownScopeEvent());

        assertAll(
                () -> verify(tableLoader, never()).clearAll(),
                () -> verify(tableLoader, never()).clearByNamespace(org.mockito.ArgumentMatchers.any()),
                () -> verify(queryLoader, never()).clearAll(),
                () -> verify(queryLoader, never()).clearByNamespace(org.mockito.ArgumentMatchers.any())
        );
        assertNotNull(fixture.coordinator(),
                "DbModelFileChangeHandler must depend on CatalogRefreshCoordinator");

        List<Invocation> refreshCalls = mockingDetails(fixture.coordinator())
                .getInvocations()
                .stream()
                .filter(invocation -> "refresh".equals(invocation.getMethod().getName()))
                .toList();
        assertEquals(1, refreshCalls.size(),
                "one committed file event must invoke one core refresh");

        Object request = refreshCalls.get(0).getArgument(0);
        assertAll(
                () -> assertEquals(NAMESPACE, invokeNoArg(request, "namespace")),
                () -> assertEquals("MODELS", ((Enum<?>) invokeNoArg(request, "scope")).name()),
                () -> assertEquals("FILE", ((Enum<?>) invokeNoArg(request, "trigger")).name())
        );
    }

    private static FsscriptRemoveEvent knownScopeEvent() {
        BundleDefinition bundleDefinition = mock(BundleDefinition.class);
        when(bundleDefinition.getNamespace()).thenReturn(NAMESPACE);
        Bundle bundle = mock(Bundle.class);
        when(bundle.getDefinition()).thenReturn(bundleDefinition);
        FsscriptClosureDefinitionSpace space = mock(FsscriptClosureDefinitionSpace.class);
        when(space.getBundle()).thenReturn(bundle);
        FsscriptClosureDefinition closure = mock(FsscriptClosureDefinition.class);
        when(closure.getFsscriptClosureDefinitionSpace()).thenReturn(space);
        Fsscript fsscript = mock(Fsscript.class);
        when(fsscript.getPath()).thenReturn(AFFECTED_RESOURCE);
        when(fsscript.getFsscriptClosureDefinition()).thenReturn(closure);
        return new FsscriptRemoveEvent(
                List.of(fsscript),
                true,
                Set.of(NAMESPACE),
                Map.of(NAMESPACE, SOURCE_REVISION),
                List.of(AFFECTED_RESOURCE)
        );
    }

    private static HandlerFixture newHandler(
            QueryModelLoaderImpl queryLoader,
            TableModelLoaderManagerImpl tableLoader
    ) {
        Class<?> coordinatorType = loadOptional(COORDINATOR);
        Object coordinator = coordinatorType == null ? null : mock(coordinatorType);

        for (Constructor<?> constructor : DbModelFileChangeHandler.class.getConstructors()) {
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                    .map(type -> argumentFor(type, queryLoader, tableLoader,
                            coordinatorType, coordinator))
                    .toArray();
            if (Arrays.stream(arguments).anyMatch(MissingArgument.class::isInstance)) {
                continue;
            }
            try {
                return new HandlerFixture(
                        (DbModelFileChangeHandler) constructor.newInstance(arguments),
                        coordinator
                );
            } catch (InstantiationException | IllegalAccessException
                     | InvocationTargetException e) {
                throw new AssertionError("Unable to create DbModelFileChangeHandler", e);
            }
        }
        throw new AssertionError("No supported DbModelFileChangeHandler constructor found");
    }

    private static Object argumentFor(
            Class<?> type,
            QueryModelLoaderImpl queryLoader,
            TableModelLoaderManagerImpl tableLoader,
            Class<?> coordinatorType,
            Object coordinator
    ) {
        if (type.isInstance(queryLoader)) {
            return queryLoader;
        }
        if (type.isInstance(tableLoader)) {
            return tableLoader;
        }
        if (coordinatorType != null && type.equals(coordinatorType)) {
            return coordinator;
        }
        return MissingArgument.INSTANCE;
    }

    private static Object invokeEventAccessor(Object event, String methodName) {
        return invokeNoArg(event, methodName, "Committed file event accessor is missing: ");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        return invokeNoArg(target, methodName, "Required accessor is missing: ");
    }

    private static Object invokeNoArg(
            Object target,
            String methodName,
            String missingPrefix
    ) {
        Method method;
        try {
            method = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(missingPrefix + methodName, e);
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Unable to invoke " + methodName, e);
        }
    }

    private static Class<?> loadOptional(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (LinkageError e) {
            throw new AssertionError("Refresh coordinator could not be linked", e);
        }
    }

    private enum MissingArgument {
        INSTANCE
    }

    private record HandlerFixture(
            DbModelFileChangeHandler handler,
            Object coordinator
    ) {
    }
}
