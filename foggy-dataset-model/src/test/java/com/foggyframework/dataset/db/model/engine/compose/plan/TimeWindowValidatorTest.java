package com.foggyframework.dataset.db.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeWindowValidator}.
 *
 * @since 8.3.0.beta
 */
@DisplayName("TimeWindowValidator")
class TimeWindowValidatorTest {

    // Fixtures — include dimension properties so grain field strict validation passes
    private static final Set<String> ALL_FIELDS = Set.of(
            "salesDate$id", "salesDate$year", "salesDate$quarter", "salesDate$month",
            "salesDate$week", "salesDate$dayOfYear", "salesDate$dayOfWeek",
            "product$id", "salesAmount", "costAmount");
    private static final Set<String> TIME_FIELDS = Set.of("salesDate$id");
    private static final Set<String> MEASURES = Set.of("salesAmount", "costAmount");

    private String validate(TimeWindowDef tw) {
        return TimeWindowValidator.validate(tw, ALL_FIELDS, TIME_FIELDS, MEASURES);
    }

    // ==================================================================
    // Happy path
    // ==================================================================

    @Nested
    @DisplayName("Happy paths")
    class HappyPaths {

        @Test
        @DisplayName("yoy + month passes")
        void yoyMonth() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);
            assertNull(validate(tw));
        }

        @Test
        @DisplayName("rolling_7d + day passes")
        void rolling7dDay() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_7d", "[)",
                    List.of("-30D", "now"),
                    List.of("salesAmount"), "avg");
            assertNull(validate(tw));
        }

        @Test
        @DisplayName("ytd + month passes")
        void ytdMonth() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "ytd", "[)",
                    List.of("2024-01-01", "now"),
                    null, null);
            assertNull(validate(tw));
        }
    }

    // ==================================================================
    // Error codes
    // ==================================================================

    @Nested
    @DisplayName("Error codes")
    class ErrorCodes {

        @Test
        @DisplayName("FIELD_NOT_FOUND")
        void fieldNotFound() {
            TimeWindowDef tw = new TimeWindowDef(
                    "nonExistentField", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.FIELD_NOT_FOUND, validate(tw));
        }

        @Test
        @DisplayName("FIELD_NOT_TIME — field exists but is not a time field")
        void fieldNotTime() {
            TimeWindowDef tw = new TimeWindowDef(
                    "product$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.FIELD_NOT_TIME, validate(tw));
        }

        @Test
        @DisplayName("GRAIN_INCOMPATIBLE — yoy × day")
        void grainIncompatYoyDay() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.GRAIN_INCOMPATIBLE, validate(tw));
        }

        @Test
        @DisplayName("GRAIN_INCOMPATIBLE — mom × week")
        void grainIncompatMomWeek() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "week", "mom", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.GRAIN_INCOMPATIBLE, validate(tw));
        }

        @Test
        @DisplayName("GRAIN_INCOMPATIBLE — rolling_7d × month")
        void grainIncompatRollingMonth() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "rolling_7d", "[)",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.GRAIN_INCOMPATIBLE, validate(tw));
        }

        @Test
        @DisplayName("RANGE_INVALID — (]")
        void rangeInvalid() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "(]",
                    List.of("2024-01-01", "2025-01-01"), null, null);
            assertEquals(TimeWindowValidator.RANGE_INVALID, validate(tw));
        }

        @Test
        @DisplayName("VALUE_PARSE_FAILED — wrong number of values")
        void valueSizeMismatch() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01"), null, null);
            assertEquals(TimeWindowValidator.VALUE_PARSE_FAILED, validate(tw));
        }

        @Test
        @DisplayName("VALUE_PARSE_FAILED — unparseable value")
        void valueUnparseable() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("not-a-date", "also-not-a-date"), null, null);
            assertEquals(TimeWindowValidator.VALUE_PARSE_FAILED, validate(tw));
        }

        @Test
        @DisplayName("TARGET_NOT_AGGREGATE — unknown metric")
        void targetNotAggregate() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("nonExistentMetric"), null);
            assertEquals(TimeWindowValidator.TARGET_NOT_AGGREGATE, validate(tw));
        }

        @Test
        @DisplayName("AGG_INVALID — unsupported rolling aggregator")
        void aggInvalid() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "day", "rolling_7d", "[)",
                    List.of("-30D", "now"),
                    List.of("salesAmount"), "median");
            assertEquals(TimeWindowValidator.AGG_INVALID, validate(tw));
        }

        @Test
        @DisplayName("GRAIN_FIELD_NOT_FOUND — model missing required grain property")
        void grainFieldNotFound() {
            // Use a fields set that does NOT include salesDate$month
            Set<String> limitedFields = Set.of("salesDate$id", "salesDate$year", "salesAmount");
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);
            assertEquals(TimeWindowValidator.GRAIN_FIELD_NOT_FOUND,
                    TimeWindowValidator.validate(tw, limitedFields, TIME_FIELDS, MEASURES));
        }
    }

    // ==================================================================
    // Calculated field interaction (8.5.0 contract)
    // ==================================================================

    @Nested
    @DisplayName("CalculatedField interaction")
    class CalculatedFieldInteraction {

        @Test
        @DisplayName("TARGET_CALCULATED_FIELD_UNSUPPORTED — targetMetrics references calc field")
        void targetMetricsRejectsCalculatedField() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("unitPrice"), null);

            var calcField = new com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef(
                    "unitPrice", "salesAmount / quantity");

            Set<String> calcFieldNames = Set.of("unitPrice");
            Set<String> twOutputCols = Set.of("salesDate$year", "salesDate$month", "salesAmount",
                    "salesAmount__prior", "salesAmount__diff", "salesAmount__ratio");

            assertEquals(TimeWindowValidator.TARGET_CALCULATED_FIELD_UNSUPPORTED,
                    TimeWindowValidator.validateCalculatedFieldInteraction(
                            tw, calcFieldNames, List.of(calcField), twOutputCols));
        }

        @Test
        @DisplayName("POST_CALC_FIELD_AGG_UNSUPPORTED — post calc field with agg")
        void postCalcFieldAggRejected() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            var calcField = new com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef();
            calcField.setName("avgRatio");
            calcField.setExpression("salesAmount__ratio");
            calcField.setAgg("AVG");

            Set<String> calcFieldNames = Set.of("avgRatio");
            Set<String> twOutputCols = Set.of("salesAmount", "salesAmount__ratio");

            assertEquals(TimeWindowValidator.POST_CALC_FIELD_AGG_UNSUPPORTED,
                    TimeWindowValidator.validateCalculatedFieldInteraction(
                            tw, calcFieldNames, List.of(calcField), twOutputCols));
        }

        @Test
        @DisplayName("POST_CALC_FIELD_WINDOW_UNSUPPORTED — post calc field with partitionBy")
        void postCalcFieldWindowRejected() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            var calcField = new com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef();
            calcField.setName("rankByRatio");
            calcField.setExpression("salesAmount__ratio");
            calcField.setPartitionBy(List.of("product$category"));

            Set<String> calcFieldNames = Set.of("rankByRatio");
            Set<String> twOutputCols = Set.of("salesAmount", "salesAmount__ratio", "product$category");

            assertEquals(TimeWindowValidator.POST_CALC_FIELD_WINDOW_UNSUPPORTED,
                    TimeWindowValidator.validateCalculatedFieldInteraction(
                            tw, calcFieldNames, List.of(calcField), twOutputCols));
        }

        @Test
        @DisplayName("POST_CALC_FIELD_NOT_FOUND — post calc field references unknown column")
        void postCalcFieldNotFoundRejected() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            var calcField = new com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef(
                    "badCalc", "nonExistentColumn * 100");

            Set<String> calcFieldNames = Set.of("badCalc");
            Set<String> twOutputCols = Set.of("salesAmount", "salesAmount__ratio");

            assertEquals(TimeWindowValidator.POST_CALC_FIELD_NOT_FOUND,
                    TimeWindowValidator.validateCalculatedFieldInteraction(
                            tw, calcFieldNames, List.of(calcField), twOutputCols));
        }

        @Test
        @DisplayName("Scalar post calc field passes validation")
        void postCalcFieldScalarAccepted() {
            TimeWindowDef tw = new TimeWindowDef(
                    "salesDate$id", "month", "yoy", "[)",
                    List.of("2024-01-01", "2025-01-01"),
                    List.of("salesAmount"), null);

            var calcField = new com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef(
                    "growthPercent", "salesAmount__ratio * 100");

            Set<String> calcFieldNames = Set.of("growthPercent");
            Set<String> twOutputCols = Set.of("salesDate$year", "salesDate$month", "salesAmount",
                    "salesAmount__prior", "salesAmount__diff", "salesAmount__ratio");

            assertNull(TimeWindowValidator.validateCalculatedFieldInteraction(
                    tw, calcFieldNames, List.of(calcField), twOutputCols));
        }
    }
}
