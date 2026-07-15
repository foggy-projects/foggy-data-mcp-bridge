package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggRefreshServiceTest {

    @Test
    void callerWatermarkCannotBootstrapIncrementalRefresh() {
        PreAggregation preAgg = mock(PreAggregation.class);
        when(preAgg.getName()).thenReturn("daily_sales");
        when(preAgg.getDataWatermark()).thenReturn(null);
        when(preAgg.getLastRefreshTime()).thenReturn(null);
        PreAggRefreshDef refreshDef = new PreAggRefreshDef();
        refreshDef.setStrategy("INCREMENTAL");
        refreshDef.setWatermarkColumn("eventDate$id");
        when(preAgg.getRefreshConfig()).thenReturn(refreshDef);

        AtomicInteger fullCalls = new AtomicInteger();
        AtomicInteger incrementalCalls = new AtomicInteger();
        PreAggRefreshService service = new PreAggRefreshService();
        service.registerStrategy(countingStrategy("FULL", fullCalls));
        service.registerStrategy(countingStrategy("INCREMENTAL", incrementalCalls));

        PreAggRefreshContext context = PreAggRefreshContext.of("sales", "daily_sales");
        context.setDialect(FDialect.MYSQL_DIALECT);
        context.setLastWatermark(LocalDate.of(2026, 7, 15));

        PreAggRefreshResult result = service.refresh(
                preAgg, mock(TableModel.class), mock(DataSource.class), context);

        assertTrue(result.isSuccess());
        assertEquals(1, fullCalls.get(),
                "no published runtime boundary must establish history with FULL");
        assertEquals(0, incrementalCalls.get(),
                "caller context must not manufacture incremental authority");
        assertEquals(null, context.getLastWatermark());
    }

    @Test
    void concurrentRefreshesPublishInCommitOrder() throws Exception {
        PreAggregation preAgg = mock(PreAggregation.class);
        AtomicReference<Object> watermark = new AtomicReference<>();
        AtomicReference<LocalDateTime> refreshTime = new AtomicReference<>();
        when(preAgg.getName()).thenReturn("daily_sales");
        when(preAgg.getDataWatermark()).thenAnswer(ignored -> watermark.get());
        doAnswer(invocation -> {
            watermark.set(invocation.getArgument(0));
            return null;
        }).when(preAgg).setDataWatermark(any());
        when(preAgg.getLastRefreshTime()).thenAnswer(ignored -> refreshTime.get());
        doAnswer(invocation -> {
            refreshTime.set(invocation.getArgument(0));
            return null;
        }).when(preAgg).setLastRefreshTime(any());

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        LocalDate firstBoundary = LocalDate.of(2026, 7, 15);
        LocalDate secondBoundary = LocalDate.of(2026, 7, 16);

        PreAggRefreshStrategy controlled = new PreAggRefreshStrategy() {
            @Override
            public String getStrategyName() {
                return "FULL";
            }

            @Override
            public boolean supports(PreAggregation ignored) {
                return true;
            }

            @Override
            public PreAggRefreshResult refresh(PreAggregation ignored,
                                               TableModel sourceModel,
                                               DataSource dataSource,
                                               PreAggRefreshContext context) {
                int invocation = sequence.incrementAndGet();
                int nowActive = active.incrementAndGet();
                maximumActive.accumulateAndGet(nowActive, Math::max);
                try {
                    if (invocation == 1) {
                        firstEntered.countDown();
                        if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("first refresh was not released");
                        }
                    } else {
                        secondEntered.countDown();
                    }
                    LocalDateTime end = LocalDateTime.now();
                    PreAggRefreshResult result = PreAggRefreshResult.success(
                            "FULL", invocation, end.minusNanos(1), end);
                    result.setNewWatermark(
                            invocation == 1 ? firstBoundary : secondBoundary);
                    return result;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return PreAggRefreshResult.failure(
                            "FULL", "interrupted", e, LocalDateTime.now());
                } finally {
                    active.decrementAndGet();
                }
            }
        };

        PreAggRefreshService service = new PreAggRefreshService();
        service.registerStrategy(controlled);
        TableModel sourceModel = mock(TableModel.class);
        DataSource dataSource = mock(DataSource.class);
        PreAggRefreshContext firstContext = fullContext();
        PreAggRefreshContext secondContext = fullContext();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PreAggRefreshResult> first = executor.submit(
                    () -> service.refresh(preAgg, sourceModel, dataSource, firstContext));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<PreAggRefreshResult> second = executor.submit(() -> {
                secondAttempting.countDown();
                return service.refresh(preAgg, sourceModel, dataSource, secondContext);
            });
            assertTrue(secondAttempting.await(5, TimeUnit.SECONDS));
            assertFalse(secondEntered.await(200, TimeUnit.MILLISECONDS),
                    "second strategy must not enter before the first commit publishes");

            releaseFirst.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess());
            assertTrue(second.get(5, TimeUnit.SECONDS).isSuccess());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, maximumActive.get());
        assertEquals(2, sequence.get());
        assertEquals(secondBoundary, watermark.get(),
                "the later committed refresh must remain published");
        assertTrue(refreshTime.get() != null);
    }

    private PreAggRefreshContext fullContext() {
        PreAggRefreshContext context = PreAggRefreshContext.of("sales", "daily_sales");
        context.setForceFullRefresh(true);
        context.setDialect(FDialect.MYSQL_DIALECT);
        return context;
    }

    private PreAggRefreshStrategy countingStrategy(String name, AtomicInteger calls) {
        return new PreAggRefreshStrategy() {
            @Override
            public String getStrategyName() {
                return name;
            }

            @Override
            public boolean supports(PreAggregation ignored) {
                return true;
            }

            @Override
            public PreAggRefreshResult refresh(PreAggregation ignored,
                                               TableModel sourceModel,
                                               DataSource dataSource,
                                               PreAggRefreshContext context) {
                calls.incrementAndGet();
                LocalDateTime end = LocalDateTime.now();
                return PreAggRefreshResult.success(name, 0, end.minusNanos(1), end);
            }
        };
    }
}
