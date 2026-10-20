package com.foggyframework.dataset.model.engine.compose.plan;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Calendar arithmetic shared by comparison ranges and period identity. */
public final class ComparativePeriodResolver {
    private ComparativePeriodResolver() {}

    public record Range(LocalDate start, LocalDate endExclusive) {}
    public record Resolved(List<Range> current, List<Range> prior) {
        public Resolved {
            current = List.copyOf(current);
            prior = List.copyOf(prior);
        }
    }

    public static Resolved resolve(TimeWindowDef window, Clock clock) {
        if (window.value() == null || window.value().isEmpty()) {
            return new Resolved(List.of(), List.of());
        }
        if (window.value().size() != 2) throw invalid("expected two date boundaries");
        LocalDate today = LocalDate.now(clock);
        LocalDate start = date(window.value().get(0), today);
        LocalDate end = date(window.value().get(1), today);
        if ("[]".equals(window.range())) end = end.plusDays(1);
        else if (!"[)".equals(window.range())) throw invalid("unsupported range");
        if (end.isBefore(start)) throw invalid("end precedes start");
        if (!floor(start, window.grain()).equals(start) || !floor(end, window.grain()).equals(end)) {
            throw new IllegalArgumentException("TIMEWINDOW_PARTIAL_PERIOD_UNSUPPORTED: value must align to complete "
                    + window.grain() + " periods; retain slice for independent source filtering");
        }
        Range current = new Range(start, end);
        if (start.equals(end)) return new Resolved(List.of(current), List.of(current));
        if (!"week".equals(window.grain()) || !"yoy".equals(window.comparison())) {
            return new Resolved(List.of(current), List.of(new Range(prior(start, window), prior(end, window))));
        }
        List<Range> priorRanges = new ArrayList<>();
        if (java.time.temporal.ChronoUnit.WEEKS.between(start, end) > 10000) throw invalid("period range exceeds 10000 weeks");
        for (LocalDate period = start; period.isBefore(end); period = period.plusWeeks(1)) {
            LocalDate mapped = prior(period, window);
            if (mapped == null) continue; // ISO week 53 does not exist in every week-year.
            if (!priorRanges.isEmpty() && priorRanges.get(priorRanges.size() - 1).endExclusive().equals(mapped)) {
                Range previous = priorRanges.remove(priorRanges.size() - 1);
                priorRanges.add(new Range(previous.start(), mapped.plusWeeks(1)));
            } else priorRanges.add(new Range(mapped, mapped.plusWeeks(1)));
        }
        // A nonempty current range can have no corresponding ISO week. An empty predicate is required,
        // rather than interpreting an empty range list as an unrestricted prior branch.
        if (priorRanges.isEmpty()) priorRanges.add(new Range(start, start));
        return new Resolved(List.of(current), priorRanges);
    }

    public static LocalDate floor(LocalDate date, String grain) {
        return switch (grain) {
            case "day" -> date;
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            case "quarter" -> date.withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
            case "year" -> date.withDayOfYear(1);
            default -> throw invalid("unknown grain: " + grain);
        };
    }

    public static LocalDate prior(LocalDate period, TimeWindowDef window) {
        if ("mom".equals(window.comparison())) return period.minusMonths(1);
        if ("wow".equals(window.comparison())) return period.minusWeeks(1);
        if (!"yoy".equals(window.comparison())) throw invalid("not a comparative mode");
        if (!"week".equals(window.grain())) return period.minusYears(1);
        int year = period.get(IsoFields.WEEK_BASED_YEAR) - 1;
        int week = period.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        LocalDate mapped = floor(LocalDate.of(year, 1, 4), "week").plusWeeks(week - 1);
        return mapped.get(IsoFields.WEEK_BASED_YEAR) == year ? mapped : null;
    }

    private static LocalDate date(String text, LocalDate today) {
        RelativeDateParser.DateExpr parsed = RelativeDateParser.parse(text);
        return switch (parsed.type()) {
            case ABSOLUTE -> parsed.absoluteDate();
            case NOW -> today;
            case RELATIVE -> switch (parsed.offsetUnit()) {
                case DAY -> today.plusDays(parsed.offsetAmount());
                case WEEK -> today.plusWeeks(parsed.offsetAmount());
                case MONTH -> today.plusMonths(parsed.offsetAmount());
                case QUARTER -> today.plusMonths(3L * parsed.offsetAmount());
                case YEAR -> today.plusYears(parsed.offsetAmount());
            };
        };
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("TIMEWINDOW_RANGE_INVALID: " + detail);
    }
}
