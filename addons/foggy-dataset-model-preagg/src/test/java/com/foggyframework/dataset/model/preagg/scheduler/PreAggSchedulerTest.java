package com.foggyframework.dataset.model.preagg.scheduler;

import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshContext;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshResult;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshService;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreAggSchedulerTest {

    @Test
    void concurrentRefreshesPublishTaskStateInExecutionOrder() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        LocalDate firstWatermark = LocalDate.of(2026, 7, 15);
        LocalDate secondWatermark = LocalDate.of(2026, 7, 16);
        LocalDateTime firstEnd = LocalDateTime.of(2026, 7, 15, 2, 0);
        LocalDateTime secondEnd = LocalDateTime.of(2026, 7, 16, 2, 0);
        when(fixture.refreshService().refresh(
                any(PreAggregation.class), any(TableModel.class), any(DataSource.class),
                any(PreAggRefreshContext.class))).thenAnswer(invocation -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        firstEntered.countDown();
                        assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                    } else {
                        secondEntered.countDown();
                    }
                    LocalDateTime end = call == 1 ? firstEnd : secondEnd;
                    PreAggRefreshResult result = PreAggRefreshResult.success(
                            "FULL", call, end.minusMinutes(1), end);
                    result.setNewWatermark(call == 1 ? firstWatermark : secondWatermark);
                    return result;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PreAggRefreshResult> first = executor.submit(
                    () -> fixture.scheduler().triggerRefresh("sales", "daily_sales", false));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<PreAggRefreshResult> second = executor.submit(() -> {
                secondAttempting.countDown();
                return fixture.scheduler().triggerRefresh("sales", "daily_sales", false);
            });
            assertTrue(secondAttempting.await(5, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS),
                    "a later task execution must wait through status publication");
            releaseFirst.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess());
            assertTrue(second.get(5, TimeUnit.SECONDS).isSuccess());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        PreAggScheduler.ScheduledTaskInfo snapshot =
                fixture.scheduler().getTaskStatus("sales", "daily_sales");
        assertEquals(secondEnd, snapshot.getLastRefreshTime());
        assertEquals(secondWatermark, snapshot.getLastWatermark());
        assertNull(snapshot.getFuture(), "status snapshots must not expose the live task handle");
        assertTrue(snapshot.isRunning(), "snapshot should retain the captured running state");
        snapshot.setLastWatermark(firstWatermark);
        snapshot.setFuture(mock(ScheduledFuture.class));
        snapshot.getFuture().cancel(false);
        assertEquals(secondWatermark, fixture.scheduler()
                .getTaskStatus("sales", "daily_sales").getLastWatermark(),
                "callers must not mutate the scheduler's published state");
        verify(fixture.scheduledFuture(), never()).cancel(false);
    }

    @Test
    void failedRefreshDoesNotAdvanceLastSuccessfulState() {
        Fixture fixture = fixture();
        PreAggRefreshService refreshService = fixture.refreshService();
        PreAggScheduler scheduler = fixture.scheduler();

        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 16, 1, 0);
        PreAggRefreshResult failure = PreAggRefreshResult.failure(
                "INCREMENTAL", "boom", new IllegalStateException("boom"), failedAt);
        when(refreshService.refresh(
                any(PreAggregation.class), any(TableModel.class), any(DataSource.class),
                any(PreAggRefreshContext.class))).thenReturn(failure);

        assertSame(failure, scheduler.triggerRefresh("sales", "daily_sales", false));
        failure.setErrorMessage("forged-return");
        PreAggScheduler.ScheduledTaskInfo afterFailure =
                scheduler.getTaskStatus("sales", "daily_sales");
        assertNotSame(failure, afterFailure.getLastResult());
        assertEquals("boom", afterFailure.getLastResult().getErrorMessage(),
                "mutating the trigger result must not alter stored scheduler state");
        assertNull(afterFailure.getLastRefreshTime());
        assertNull(afterFailure.getLastWatermark());
        afterFailure.getLastResult().setErrorMessage("forged");
        assertEquals("boom", scheduler.getTaskStatus("sales", "daily_sales")
                .getLastResult().getErrorMessage());

        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 16, 2, 0);
        LocalDate watermark = LocalDate.of(2026, 7, 16);
        PreAggRefreshResult success = PreAggRefreshResult.success(
                "FULL", 3, completedAt.minusMinutes(1), completedAt);
        success.setNewWatermark(watermark);
        when(refreshService.refresh(
                any(PreAggregation.class), any(TableModel.class), any(DataSource.class),
                any(PreAggRefreshContext.class))).thenReturn(success);

        assertSame(success, scheduler.triggerRefresh("sales", "daily_sales", false));
        success.setAffectedRows(888);
        success.setNewWatermark(LocalDate.of(1998, 1, 1));
        PreAggScheduler.ScheduledTaskInfo afterSuccess =
                scheduler.getTaskStatus("sales", "daily_sales");
        assertNotSame(success, afterSuccess.getLastResult());
        assertEquals(3, afterSuccess.getLastResult().getAffectedRows());
        assertEquals(completedAt, afterSuccess.getLastRefreshTime());
        assertEquals(watermark, afterSuccess.getLastWatermark());
        assertEquals(watermark, afterSuccess.getLastResult().getNewWatermark());
        afterSuccess.getLastResult().setAffectedRows(999);
        afterSuccess.getLastResult().setNewWatermark(LocalDate.of(1999, 1, 1));
        PreAggScheduler.ScheduledTaskInfo unchanged =
                scheduler.getTaskStatus("sales", "daily_sales");
        assertEquals(3, unchanged.getLastResult().getAffectedRows());
        assertEquals(watermark, unchanged.getLastResult().getNewWatermark());
    }

    private Fixture fixture() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        doReturn(scheduledFuture).when(taskScheduler).schedule(
                any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
        PreAggRefreshService refreshService = mock(PreAggRefreshService.class);
        PreAggScheduler scheduler = new PreAggScheduler(taskScheduler, refreshService);
        TableModel model = mock(TableModel.class);
        DataSource dataSource = mock(DataSource.class);
        PreAggregation preAgg = mock(PreAggregation.class);
        PreAggRefreshDef refreshDef = new PreAggRefreshDef();
        refreshDef.setSchedule("0 0 2 * * *");
        when(preAgg.isEnabled()).thenReturn(true);
        when(preAgg.getName()).thenReturn("daily_sales");
        when(preAgg.getRefreshConfig()).thenReturn(refreshDef);
        when(model.getPreAggregations()).thenReturn(List.of(preAgg));
        scheduler.registerModel("sales", model, dataSource);
        scheduler.registerPreAggregation("sales", preAgg);
        return new Fixture(scheduler, refreshService, scheduledFuture);
    }

    private record Fixture(PreAggScheduler scheduler,
                           PreAggRefreshService refreshService,
                           ScheduledFuture<?> scheduledFuture) {
    }
}
