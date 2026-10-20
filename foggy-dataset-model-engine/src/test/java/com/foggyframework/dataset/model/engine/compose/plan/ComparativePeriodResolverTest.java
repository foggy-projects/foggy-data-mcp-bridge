package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ComparativePeriodResolverTest {
    private final Clock clock = Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private TimeWindowDef window(String comparison, String grain, String range, String start, String end) {
        return TimeWindowDef.fromMap(Map.of("field", "createdAt", "comparison", comparison,
                "grain", grain, "range", range, "value", List.of(start, end)));
    }

    @Test void monthlyClosedRangeIncludesLastNaturalDayAndCrossesYear() {
        var resolved = ComparativePeriodResolver.resolve(window("mom", "month", "[]", "2012-01-01", "2012-12-31"), clock);
        assertEquals(new ComparativePeriodResolver.Range(LocalDate.of(2012, 1, 1), LocalDate.of(2013, 1, 1)), resolved.current().get(0));
        assertEquals(new ComparativePeriodResolver.Range(LocalDate.of(2011, 12, 1), LocalDate.of(2012, 12, 1)), resolved.prior().get(0));
    }

    @Test void isoWeek53WithoutPriorCorrespondenceIsEmpty() {
        var w = window("yoy", "week", "[)", "2020-12-28", "2021-01-04");
        assertNull(ComparativePeriodResolver.prior(LocalDate.of(2020, 12, 28), w));
        var range = ComparativePeriodResolver.resolve(w, clock).prior().get(0);
        assertEquals(range.start(), range.endExclusive());
    }

    @Test void isoWeekOneUsesWeekYearInsteadOfCalendarYear() {
        var w = window("yoy", "week", "[)", "2019-12-30", "2020-01-06");
        assertEquals(LocalDate.of(2018, 12, 31), ComparativePeriodResolver.prior(LocalDate.of(2019, 12, 30), w));
    }

    @Test void rejectsPartialMonthInsteadOfRounding() {
        var error = assertThrows(IllegalArgumentException.class, () -> ComparativePeriodResolver.resolve(
                window("mom", "month", "[)", "2025-01-02", "2025-02-01"), clock));
        assertTrue(error.getMessage().startsWith("TIMEWINDOW_PARTIAL_PERIOD_UNSUPPORTED"));
    }

    @Test void quarterlyAndLeapYearRangesAreCalendarAligned() {
        var result = ComparativePeriodResolver.resolve(window("yoy", "quarter", "[)", "2024-01-01", "2024-04-01"), clock);
        assertEquals(LocalDate.of(2023, 4, 1), result.prior().get(0).endExclusive());
        assertEquals(LocalDate.of(2024, 1, 1), ComparativePeriodResolver.floor(LocalDate.of(2024, 2, 29), "year"));
    }
}
