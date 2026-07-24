package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlExecutionPort;
import com.foggyframework.dataset.db.model.semantic.port.ComposeSqlGeneration;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * M7 unit tests for {@link ComposeRuntimeHolder}.
 *
 * @since 8.2.0.beta
 */
@DisplayName("ComposeRuntimeHolder · ThreadLocal Deque 单元测试")
@TestMethodOrder(OrderAnnotation.class)
class ComposeRuntimeHolderTest {

    private static ComposeQueryContext dummyCtx() {
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                .namespace("ns1")
                .traceId("trace-1")
                .authorityResolver(mock(AuthorityResolver.class))
                .build();
    }

    private static ComposeRuntimeBundle dummyBundle() {
        return ComposeRuntimeBundle.builder()
                .ctx(dummyCtx())
                .semanticService(mock(SemanticQueryServiceV3.class))
                .dialect("mysql")
                .build();
    }

    @AfterEach
    void cleanup() {
        ComposeRuntimeHolder.clearForTesting();
    }

    @Test
    @Order(1)
    @DisplayName("currentBundle() returns null when empty")
    void emptyDeque_returnsNull() {
        assertNull(ComposeRuntimeHolder.currentBundle());
    }

    @Test
    void bundleAcceptsIndependentPlanningAndExecutionPorts() {
        ComposeSemanticPlanningPort planningPort = (model, request, context) ->
                new ComposeSqlGeneration("select 1", List.of(), List.of(), java.util.Map.of());
        ComposeSqlExecutionPort executionPort = (sql, params, routeModel) -> List.of();

        ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                .ctx(dummyCtx())
                .planningPort(planningPort)
                .executionPort(executionPort)
                .build();

        assertNull(bundle.semanticService());
        assertSame(planningPort, bundle.planningPort());
        assertSame(executionPort, bundle.executionPort());
    }

    @Test
    @Order(2)
    @DisplayName("push/pop round-trip succeeds")
    void pushPop_roundTrip() {
        ComposeRuntimeBundle b = dummyBundle();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(b);
        assertSame(b, ComposeRuntimeHolder.currentBundle());
        ComposeRuntimeHolder.popBundle(token);
        assertNull(ComposeRuntimeHolder.currentBundle());
    }

    @Test
    @Order(3)
    @DisplayName("nested push/pop preserves outer bundle")
    void nestedPushPop() {
        ComposeRuntimeBundle outer = dummyBundle();
        ComposeRuntimeBundle inner = dummyBundle();
        ComposeRuntimeHolder.Token outerToken = ComposeRuntimeHolder.setBundle(outer);
        assertSame(outer, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.Token innerToken = ComposeRuntimeHolder.setBundle(inner);
        assertSame(inner, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(innerToken);
        assertSame(outer, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(outerToken);
        assertNull(ComposeRuntimeHolder.currentBundle());
    }

    @Test
    @Order(4)
    @DisplayName("setBundle(null) throws IAE")
    void setBundle_null_throwsIAE() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeRuntimeHolder.setBundle(null));
    }

    @Test
    @Order(5)
    @DisplayName("popBundle over-pop throws ISE")
    void overPop_throwsISE() {
        ComposeRuntimeBundle b = dummyBundle();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(b);
        ComposeRuntimeHolder.popBundle(token);
        // Stack is now empty, popping again should fail
        assertThrows(IllegalStateException.class,
                () -> ComposeRuntimeHolder.popBundle(token));
    }

    @Test
    @Order(6)
    @DisplayName("multi-thread isolation: each thread has independent stack")
    void multiThread_isolation() throws Exception {
        ComposeRuntimeBundle main = dummyBundle();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(main);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ComposeRuntimeBundle> childSeen = new AtomicReference<>();

        Thread child = new Thread(() -> {
            childSeen.set(ComposeRuntimeHolder.currentBundle());
            latch.countDown();
        });
        child.start();
        latch.await();

        // Child thread should see null (ThreadLocal isolation)
        assertNull(childSeen.get());
        // Main thread still sees its bundle
        assertSame(main, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(token);
    }

    @Test
    @Order(7)
    @DisplayName("triple nesting works correctly")
    void tripleNesting() {
        ComposeRuntimeBundle b1 = dummyBundle();
        ComposeRuntimeBundle b2 = dummyBundle();
        ComposeRuntimeBundle b3 = dummyBundle();

        ComposeRuntimeHolder.Token t1 = ComposeRuntimeHolder.setBundle(b1);
        ComposeRuntimeHolder.Token t2 = ComposeRuntimeHolder.setBundle(b2);
        ComposeRuntimeHolder.Token t3 = ComposeRuntimeHolder.setBundle(b3);

        assertSame(b3, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(t3);
        assertSame(b2, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(t2);
        assertSame(b1, ComposeRuntimeHolder.currentBundle());

        ComposeRuntimeHolder.popBundle(t1);
        assertNull(ComposeRuntimeHolder.currentBundle());
    }
}
