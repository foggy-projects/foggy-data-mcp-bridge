package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.support.SimpleQueryObject;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeResolvedBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.aop.framework.ProxyFactory;

import javax.sql.DataSource;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeNamedDataSourceResolverBindingTest {

    @TempDir
    Path tempDir;

    private ManagedDataSourcePoolManager manager;

    @AfterEach
    void closeManager() {
        if (manager != null) {
            manager.destroy();
        }
    }

    @Test
    void strongResolverPublishesOpaqueTrackedIdentityAndPinnedHandle() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(tempDir.resolve("registry.json").toString());
        manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory()
        );
        manager.start();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RuntimeDatasourceRegistryService registry = new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                manager
        );
        RuntimeNamedDataSourceResolver resolver = new RuntimeNamedDataSourceResolver(registry, beanFactory);
        RuntimeDatasourceRecord first = registry.save(registry.newRecord(
                "sales",
                "h2",
                "jdbc:h2:mem:strong_binding_first;DB_CLOSE_DELAY=-1",
                "credential-user-934",
                null,
                null,
                true
        ));

        ResolvedDatasourceBinding firstBinding = resolver.resolveBinding("sales");

        assertThat(firstBinding).isNotNull();
        assertThat(firstBinding.cacheable()).isTrue();
        assertThat(firstBinding.identity().bindingKey()).isEqualTo("runtime:named:sales");
        assertThat(firstBinding.identity().backendId()).isEqualTo("runtime-registry:sales");
        assertThat(firstBinding.identity().generation().value()).isEqualTo(first.bindingGeneration());
        assertThat(firstBinding.identity().toString())
                .doesNotContain("jdbc:", "strong_binding_first", "credential-user-934");
        assertThat(resolver.currentness(firstBinding.identity()))
                .isEqualTo(BindingCurrentness.CURRENT);

        RuntimeDatasourceRecord second = registry.save(registry.newRecord(
                "sales",
                "h2",
                "jdbc:h2:mem:strong_binding_second;DB_CLOSE_DELAY=-1",
                "credential-user-934",
                null,
                null,
                true
        ));
        ResolvedDatasourceBinding secondBinding = resolver.resolveBinding("sales");

        assertThat(secondBinding.identity()).isNotEqualTo(firstBinding.identity());
        assertThat(secondBinding.identity().generation().value()).isEqualTo(second.bindingGeneration());
        assertThat(secondBinding.dataSource()).isNotSameAs(firstBinding.dataSource());
        assertThat(resolver.currentness(firstBinding.identity()))
                .isEqualTo(BindingCurrentness.STALE);
        assertThat(resolver.currentness(secondBinding.identity()))
                .isEqualTo(BindingCurrentness.CURRENT);
        assertThat(resolver.currentness(new DatasourceBindingIdentity(
                "foreign-binding",
                "foreign-backend",
                new DatasourceBindingGeneration("foreign-generation"))))
                .isEqualTo(BindingCurrentness.UNKNOWN);
        assertThatThrownBy(() -> firstBinding.dataSource().getConnection())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("DATASOURCE_BINDING_REVOKED");

        RuntimeDatasourceRegistryService incompleteRegistry =
                mock(RuntimeDatasourceRegistryService.class);
        DataSource incompleteDataSource = mock(DataSource.class);
        RuntimeNamedDataSourceResolver incompleteResolver = new RuntimeNamedDataSourceResolver(
                incompleteRegistry, new StaticListableBeanFactory());
        List<RuntimeResolvedBinding> incompleteBindings = List.of(
                new RuntimeResolvedBinding(
                        "missing-binding-key", incompleteDataSource, " ",
                        "runtime-registry:missing-binding-key", "generation-1", true),
                new RuntimeResolvedBinding(
                        "missing-backend-id", incompleteDataSource,
                        "runtime:named:missing-backend-id", " ", "generation-2", true),
                new RuntimeResolvedBinding(
                        "missing-generation", incompleteDataSource,
                        "runtime:named:missing-generation",
                        "runtime-registry:missing-generation", " ", true)
        );
        for (RuntimeResolvedBinding incomplete : incompleteBindings) {
            when(incompleteRegistry.resolveRuntimeBinding(incomplete.name()))
                    .thenReturn(Optional.of(incomplete));

            ResolvedDatasourceBinding untracked =
                    incompleteResolver.resolveBinding(incomplete.name());

            assertThat(untracked).isNotNull();
            assertThat(untracked.dataSource()).isSameAs(incompleteDataSource);
            assertThat(untracked.identity()).isNull();
            assertThat(untracked.cacheable()).isFalse();
        }
    }

    @Test
    void namespaceDefaultCurrentnessTracksOnlyTheCommittedLogicalBinding() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(tempDir.resolve("namespace-registry.json").toString());
        manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory()
        );
        manager.start();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RuntimeDatasourceRegistryService registry = new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                manager
        );
        RuntimeNamedDataSourceResolver resolver = new RuntimeNamedDataSourceResolver(registry, beanFactory);
        registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:namespace_sales;DB_CLOSE_DELAY=-1",
                "runtime-user", null, null, true));
        registry.save(registry.newRecord(
                "analytics", "h2", "jdbc:h2:mem:namespace_analytics;DB_CLOSE_DELAY=-1",
                "runtime-user", null, null, true));
        registry.bindNamespace("tenant-a", "sales");
        ResolvedDatasourceBinding first = resolver.resolveDefaultBinding("tenant-a");

        assertThat(resolver.currentness(first.identity())).isEqualTo(BindingCurrentness.CURRENT);

        registry.bindNamespace("tenant-a", "analytics");
        ResolvedDatasourceBinding second = resolver.resolveDefaultBinding("tenant-a");

        assertThat(resolver.currentness(first.identity())).isEqualTo(BindingCurrentness.STALE);
        assertThat(resolver.currentness(second.identity())).isEqualTo(BindingCurrentness.CURRENT);

        registry.remove("analytics");
        assertThat(resolver.currentness(second.identity())).isEqualTo(BindingCurrentness.STALE);
    }

    @Test
    void processLocalDefaultDelegatesOnlyToAnExplicitlyOptedInResolver() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(
                tempDir.resolve("process-local-registry.json").toString());
        manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory()
        );
        manager.start();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        DataSource processLocal = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:process_local_default;DB_CLOSE_DELAY=-1");
        ResolvedDatasourceBinding expected = ResolvedDatasourceBinding.tracked(
                processLocal,
                new DatasourceBindingIdentity(
                        "process-local:default",
                        "test-process-local",
                        new DatasourceBindingGeneration("test-generation")));
        beanFactory.addBean("processLocalResolver",
                (ProcessLocalDefaultDataSourceResolver) () -> expected);
        RuntimeDatasourceRegistryService registry = new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                manager
        );
        RuntimeNamedDataSourceResolver resolver = new RuntimeNamedDataSourceResolver(registry, beanFactory);

        assertThat(resolver.resolveProcessLocalDefaultBinding()).isSameAs(expected);
    }

    @Test
    void foreignRuntimeLikeIdentityDelegatesWithoutSelfProxyRecursion() {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(
                tempDir.resolve("foreign-binding-registry.json").toString());
        manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory()
        );
        manager.start();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RuntimeDatasourceRegistryService registry = new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                manager
        );
        AtomicReference<RuntimeNamedDataSourceResolver> composite = new AtomicReference<>();
        AtomicInteger guardedPublications = new AtomicInteger();
        AtomicInteger secondGuardedPublications = new AtomicInteger();
        DatasourceBindingIdentity foreignIdentity = new DatasourceBindingIdentity(
                "runtime:named:sales",
                "foreign-runtime-like-backend",
                new DatasourceBindingGeneration("foreign-generation"));
        DatasourceBindingIdentity secondForeignIdentity = new DatasourceBindingIdentity(
                "second:binding",
                "second-backend",
                new DatasourceBindingGeneration("second-generation"));
        DatasourceBindingResolver recursiveDecorator = new DatasourceBindingResolver() {
            @Override
            public ResolvedDatasourceBinding resolveBinding(String name) {
                return composite.get().resolveBinding(name);
            }

            @Override
            public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
                assertThat(composite.get().currentness(identity))
                        .isEqualTo(BindingCurrentness.UNKNOWN);
                return foreignIdentity.equals(identity)
                        ? BindingCurrentness.CURRENT
                        : BindingCurrentness.UNKNOWN;
            }

            @Override
            public <T> T publishIfCurrent(
                    Collection<DatasourceBindingIdentity> identities,
                    Supplier<T> publication
            ) {
                assertThat(identities).containsExactly(foreignIdentity);
                guardedPublications.incrementAndGet();
                return publication.get();
            }
        };
        beanFactory.addBean("recursiveDecorator", recursiveDecorator);
        beanFactory.addBean("secondLeaf", new DatasourceBindingResolver() {
            @Override
            public ResolvedDatasourceBinding resolveBinding(String name) {
                return null;
            }

            @Override
            public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
                return secondForeignIdentity.equals(identity)
                        ? BindingCurrentness.CURRENT
                        : BindingCurrentness.UNKNOWN;
            }

            @Override
            public <T> T publishIfCurrent(
                    Collection<DatasourceBindingIdentity> identities,
                    Supplier<T> publication
            ) {
                assertThat(identities).containsExactly(secondForeignIdentity);
                secondGuardedPublications.incrementAndGet();
                return publication.get();
            }
        });
        RuntimeNamedDataSourceResolver resolver =
                new RuntimeNamedDataSourceResolver(registry, beanFactory);
        composite.set(resolver);
        ProxyFactory proxyFactory = new ProxyFactory(resolver);
        proxyFactory.setProxyTargetClass(true);
        beanFactory.addBean("runtimeResolverProxy", proxyFactory.getProxy());

        assertThat(resolver.resolveBinding("missing")).isNull();
        assertThat(resolver.resolveProcessLocalDefaultBinding()).isNull();
        assertThat(resolver.currentness(foreignIdentity))
                .isEqualTo(BindingCurrentness.CURRENT);
        assertThat(resolver.currentness(secondForeignIdentity))
                .isEqualTo(BindingCurrentness.CURRENT);
        assertThat(resolver.publishIfCurrent(
                List.of(foreignIdentity, secondForeignIdentity), () -> "published"))
                .isEqualTo("published");
        assertThat(guardedPublications).hasValue(1);
        assertThat(secondGuardedPublications).hasValue(1);
    }

    @Test
    void publicationGuardSerializesTheCallbackWithAConcurrentRebind() throws Exception {
        FoggyRuntimeApiProperties properties = new FoggyRuntimeApiProperties();
        properties.getDatasourceRegistry().setPath(
                tempDir.resolve("publication-guard-registry.json").toString());
        manager = new ManagedDataSourcePoolManager(
                properties,
                new HikariManagedDataSourcePoolFactory()
        );
        manager.start();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RuntimeDatasourceRegistryService registry = new RuntimeDatasourceRegistryService(
                properties,
                beanFactory.getBeanProvider(DataSource.class),
                new ObjectMapper().findAndRegisterModules(),
                manager
        );
        RuntimeNamedDataSourceResolver resolver = new RuntimeNamedDataSourceResolver(registry, beanFactory);
        registry.save(registry.newRecord(
                "sales", "h2", "jdbc:h2:mem:guard_g1;DB_CLOSE_DELAY=-1",
                "runtime-user", null, null, true));
        ResolvedDatasourceBinding generationOne = resolver.resolveBinding("sales");

        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicReference<Thread> mutationThread = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> guardedPublication = null;
        Future<RuntimeDatasourceRecord> rebind = null;
        try {
            guardedPublication = executor.submit(() -> resolver.publishIfCurrent(
                    List.of(generationOne.identity()),
                    () -> {
                        callbackThread.set(Thread.currentThread());
                        callbackEntered.countDown();
                        awaitLatch(releaseCallback, "release publication callback");
                        return "published-g1";
                    }));
            assertThat(callbackEntered.await(5, TimeUnit.SECONDS)).isTrue();

            rebind = executor.submit(() -> {
                mutationThread.set(Thread.currentThread());
                mutationStarted.countDown();
                return registry.save(registry.newRecord(
                        "sales", "h2", "jdbc:h2:mem:guard_g2;DB_CLOSE_DELAY=-1",
                        "runtime-user", null, null, true));
            });
            assertThat(mutationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitBlockedOn(mutationThread.get(), callbackThread.get());
            assertThat(rebind).isNotDone();

            releaseCallback.countDown();
            assertThat(guardedPublication.get(5, TimeUnit.SECONDS)).isEqualTo("published-g1");
            RuntimeDatasourceRecord generationTwo = rebind.get(5, TimeUnit.SECONDS);

            assertThat(generationTwo.bindingGeneration())
                    .isNotEqualTo(generationOne.identity().generation().value());
            assertThat(resolver.currentness(generationOne.identity()))
                    .isEqualTo(BindingCurrentness.STALE);
            assertThat(resolver.currentness(resolver.resolveBinding("sales").identity()))
                    .isEqualTo(BindingCurrentness.CURRENT);

            // Preserve the frozen testcase identity while stabilizing the QueryModel DCL branch.
            assertMergedJoinGraphDoubleCheckUnderContention(executor);
        } finally {
            releaseCallback.countDown();
            if (guardedPublication != null) {
                guardedPublication.cancel(true);
            }
            if (rebind != null) {
                rebind.cancel(true);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void awaitLatch(CountDownLatch latch, String description) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to " + description);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to " + description, e);
        }
    }

    private static void awaitBlockedOn(Thread contender, Thread owner) {
        awaitBlockedOn(contender, owner, null, "publication guard");
    }

    private static void assertMergedJoinGraphDoubleCheckUnderContention(
            ExecutorService executor
    ) throws Exception {
        CountDownLatch buildEntered = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        CountDownLatch secondCallerStarted = new CountDownLatch(1);
        AtomicInteger buildCount = new AtomicInteger();
        AtomicReference<Thread> firstCallerThread = new AtomicReference<>();
        AtomicReference<Thread> secondCallerThread = new AtomicReference<>();

        QueryObject root = SimpleQueryObject.of("runtime_binding", "runtime_binding", null);
        JoinGraph sourceGraph = new JoinGraph(root);
        TableModel tableModel = mock(TableModel.class);
        when(tableModel.getQueryObject()).thenReturn(root);
        when(tableModel.getAlias()).thenReturn(root.getAlias());
        when(tableModel.getJoinGraph()).thenAnswer(invocation -> {
            buildCount.incrementAndGet();
            buildEntered.countDown();
            awaitLatch(releaseBuild, "release merged JoinGraph build");
            return sourceGraph;
        });
        QueryModelSupport support = new JdbcQueryModelImpl(List.of(tableModel), null, null, null);

        Future<JoinGraph> firstResult = null;
        Future<JoinGraph> secondResult = null;
        try {
            firstResult = executor.submit(() -> {
                firstCallerThread.set(Thread.currentThread());
                return support.getMergedJoinGraph();
            });
            awaitLatch(buildEntered, "enter merged JoinGraph build");

            secondResult = executor.submit(() -> {
                secondCallerThread.set(Thread.currentThread());
                secondCallerStarted.countDown();
                return support.getMergedJoinGraph();
            });
            awaitLatch(secondCallerStarted, "start second merged JoinGraph caller");
            awaitBlockedOn(
                    secondCallerThread.get(), firstCallerThread.get(), support,
                    "QueryModelSupport monitor");
            assertThat(secondResult).isNotDone();

            releaseBuild.countDown();
            JoinGraph firstGraph = firstResult.get(5, TimeUnit.SECONDS);
            JoinGraph secondGraph = secondResult.get(5, TimeUnit.SECONDS);
            assertThat(secondGraph).isSameAs(firstGraph);
            assertThat(firstGraph.getRoot()).isSameAs(root);
            assertThat(buildCount).hasValue(1);
        } finally {
            releaseBuild.countDown();
            if (firstResult != null && !firstResult.isDone()) {
                firstResult.cancel(true);
            }
            if (secondResult != null && !secondResult.isDone()) {
                secondResult.cancel(true);
            }
        }
    }

    private static void awaitBlockedOn(
            Thread contender,
            Thread owner,
            Object expectedMonitor,
            String description
    ) {
        if (contender == null || owner == null) {
            throw new AssertionError("Concurrent caller threads were not captured for " + description);
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            ThreadInfo info = ManagementFactory.getThreadMXBean()
                    .getThreadInfo(contender.getId());
            LockInfo lockInfo = info == null ? null : info.getLockInfo();
            boolean sameMonitor = expectedMonitor == null
                    || (lockInfo != null
                    && lockInfo.getIdentityHashCode() == System.identityHashCode(expectedMonitor));
            if (info != null
                    && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockOwnerId() == owner.getId()
                    && sameMonitor) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Concurrent caller did not block on " + description);
    }
}
