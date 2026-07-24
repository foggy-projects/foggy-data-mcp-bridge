package com.foggyframework.dataset.model.lifecycle.gate;

import com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport;
import com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.ControlledSingleFlight;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicConcurrencyHarnessProbeTest {

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void coordinatesOneWinnerAllWaitersAndCleansResources() {
        int callers = 8;
        Object builtValue = new Object();
        ControlledSingleFlight<String, Object> flight = new ControlledSingleFlight<>(callers - 1);
        Phaser start = new Phaser(callers + 1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        List<Future<Object>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    DeterministicConcurrencyTestSupport.rendezvous(start, "unit callers ready");
                    return flight.execute("same-key", () -> builtValue);
                }));
            }

            DeterministicConcurrencyTestSupport.rendezvous(start, "unit main release");
            flight.awaitWinnerAndWaiters();
            assertEquals(1, flight.buildCount(), "release前只能有一个winner");
            flight.releaseWinner();

            for (int i = 0; i < futures.size(); i++) {
                assertSame(builtValue, DeterministicConcurrencyTestSupport.get(futures.get(i), "unit caller " + i));
            }

            assertEquals(1, flight.buildCount());
            assertEquals(0, flight.inFlightCount());
            assertTrue(futures.stream().allMatch(future -> future.isDone() && !future.isCancelled()));
        } finally {
            flight.releaseWinner();
            DeterministicConcurrencyTestSupport.cancelIncomplete(futures);
            DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated(executor, "unit probe");
        }

        ExecutorService reuseExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<Void> first = reuseExecutor.submit(() -> {
                assertNull(NamespaceContext.getNamespace());
                NamespaceContext.setNamespace("v933-probe");
                try {
                    assertEquals("v933-probe", NamespaceContext.getNamespace());
                    return null;
                } finally {
                    NamespaceContext.clear();
                }
            });
            DeterministicConcurrencyTestSupport.get(first, "namespace first task");

            Future<String> second = reuseExecutor.submit(NamespaceContext::getNamespace);
            assertNull(DeterministicConcurrencyTestSupport.get(second, "namespace reused task"));
        } finally {
            DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated(reuseExecutor, "namespace reuse probe");
        }
        assertNull(NamespaceContext.getNamespace());

        ControlledSingleFlight<String, Object> interruptedFlight = new ControlledSingleFlight<>(0);
        ExecutorService interruptedExecutor = Executors.newSingleThreadExecutor();
        Future<Object> interruptedWinner = interruptedExecutor.submit(
                () -> interruptedFlight.execute("interrupted-key", Object::new)
        );
        try {
            interruptedFlight.awaitWinnerAndWaiters();
            assertTrue(interruptedWinner.cancel(true), "受控winner应在release前接受中断");
        } finally {
            interruptedFlight.releaseWinner();
            DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated(
                    interruptedExecutor,
                    "interrupted winner probe"
            );
        }
        assertTrue(interruptedWinner.isCancelled());
        assertEquals(0, interruptedFlight.inFlightCount(), "winner中断后不能残留in-flight");
    }
}
