package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.spi.DbAggregation;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggAverageStateContractTest {

    @Test
    void derivesVersionedAverageStateColumnsFromConfiguredMeasureColumn() {
        PreAggregation preAgg = mock(PreAggregation.class);
        when(preAgg.getMeasureAggregations()).thenReturn(
                Map.of("averageAmount", DbAggregation.AVG));
        when(preAgg.getMeasureColumnNames()).thenReturn(
                Map.of("averageAmount", "average_amount_avg"));

        PreAggMeasureStateContract.MeasureState state =
                PreAggMeasureStateContract.resolve(preAgg, "averageAmount");

        assertEquals("average_amount_avg__sum", state.sumColumn());
        assertEquals("average_amount_avg__count", state.countColumn());
    }

    @Test
    void refusesMissingConfiguredColumnInsteadOfGuessingPhysicalSchema() {
        PreAggregation preAgg = mock(PreAggregation.class);
        when(preAgg.getMeasureAggregations()).thenReturn(
                Map.of("averageAmount", DbAggregation.AVG));
        when(preAgg.getMeasureColumnNames()).thenReturn(Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> PreAggMeasureStateContract.resolve(preAgg, "averageAmount"));
    }
}
