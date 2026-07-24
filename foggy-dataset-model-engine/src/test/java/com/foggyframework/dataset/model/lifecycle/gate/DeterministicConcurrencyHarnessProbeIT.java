package com.foggyframework.dataset.model.lifecycle.gate;

import com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport;
import com.foggyframework.dataset.model.lifecycle.support.DeterministicConcurrencyTestSupport.ControlledSingleFlight;
import com.foggyframework.dataset.model.spi.NamespaceContext;
import com.foggyframework.dataset.model.test.JdbcModelTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles("sqlite")
class DeterministicConcurrencyHarnessProbeIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void clearNamespace() {
        NamespaceContext.clear();
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void coordinatesSharedJdbcWinnerAndProvesWorkerCleanup() throws Exception {
        int callers = 2;
        ControlledSingleFlight<String, Integer> flight = new ControlledSingleFlight<>(callers - 1);
        Phaser start = new Phaser(callers + 1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        List<Future<Integer>> futures = new ArrayList<>();
        List<Future<String>> reuseChecks = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    assertNull(NamespaceContext.getNamespace());
                    NamespaceContext.setNamespace("v933-sqlite-probe");
                    try {
                        DeterministicConcurrencyTestSupport.rendezvous(start, "IT callers ready");
                        return flight.execute(
                                "select-one",
                                () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class)
                        );
                    } finally {
                        NamespaceContext.clear();
                    }
                }));
            }

            DeterministicConcurrencyTestSupport.rendezvous(start, "IT main release");
            flight.awaitWinnerAndWaiters();
            assertEquals(1, flight.buildCount());
            flight.releaseWinner();

            for (int i = 0; i < futures.size(); i++) {
                assertEquals(1, DeterministicConcurrencyTestSupport.get(futures.get(i), "IT caller " + i));
            }

            CyclicBarrier reuseBarrier = new CyclicBarrier(callers + 1);
            for (int i = 0; i < callers; i++) {
                reuseChecks.add(executor.submit(() -> {
                    String observed = NamespaceContext.getNamespace();
                    reuseBarrier.await(
                            DeterministicConcurrencyTestSupport.STEP_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );
                    return observed;
                }));
            }
            reuseBarrier.await(DeterministicConcurrencyTestSupport.STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (int i = 0; i < reuseChecks.size(); i++) {
                assertNull(DeterministicConcurrencyTestSupport.get(reuseChecks.get(i), "IT reuse " + i));
            }

            assertEquals(1, flight.buildCount());
            assertEquals(0, flight.inFlightCount());
            assertTrue(futures.stream().allMatch(future -> future.isDone() && !future.isCancelled()));
            assertTrue(reuseChecks.stream().allMatch(future -> future.isDone() && !future.isCancelled()));
        } finally {
            flight.releaseWinner();
            DeterministicConcurrencyTestSupport.cancelIncomplete(futures);
            DeterministicConcurrencyTestSupport.cancelIncomplete(reuseChecks);
            DeterministicConcurrencyTestSupport.shutdownAndAssertTerminated(executor, "IT probe");
        }

        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite"));
        }
        assertNull(NamespaceContext.getNamespace());
    }
}
