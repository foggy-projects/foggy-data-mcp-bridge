package com.foggyframework.dataset.model.plugins.query_execution;

import com.foggyframework.dataset.model.engine.preagg.PreAggRewriteResult;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreAggRewriteStepExplainTest {

    @Test
    void keepsRawDirectRollupAndHybridRoutesDistinct() {
        PreAggregation preAggregation = mock(PreAggregation.class);
        when(preAggregation.getName()).thenReturn("sales_daily");

        assertThat(PreAggRewriteStep.explainRoute(
                PreAggRewriteResult.notApplied("PREAGG_NO_COMPATIBLE_CANDIDATE", "no match")))
                .isEqualTo("RAW");
        assertThat(PreAggRewriteStep.explainRoute(
                PreAggRewriteResult.applied(preAggregation, "select 1", List.of(), false)))
                .isEqualTo("PREAGG_DIRECT");
        assertThat(PreAggRewriteStep.explainRoute(
                PreAggRewriteResult.applied(preAggregation, "select 1", List.of(), true)))
                .isEqualTo("PREAGG_ROLLUP");
        assertThat(PreAggRewriteStep.explainRoute(
                PreAggRewriteResult.hybrid(
                        preAggregation, "select 1 union all select 2", List.of(), true,
                        LocalDate.of(2024, 1, 1))))
                .isEqualTo("PREAGG_HYBRID");
    }
}
