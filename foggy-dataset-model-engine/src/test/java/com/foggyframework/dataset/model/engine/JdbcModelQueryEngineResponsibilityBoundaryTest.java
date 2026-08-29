package com.foggyframework.dataset.model.engine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcModelQueryEngineResponsibilityBoundaryTest {

    private static final Set<String> EXTRACTED_METHODS = Set.of(
            "buildTotalDataAggregatePlan",
            "prepareResultStagePreparation",
            "renderAlgebraicTotalData",
            "renderSharedResultStageTotal",
            "renderSingleStageAlgebraicTotal",
            "buildTotalFinalProjection",
            "addStateColumns"
    );

    @Test
    void totalAndResultStageResponsibilitiesShouldLiveInDedicatedCollaborators() {
        assertDoesNotThrow(() -> Class.forName(
                "com.foggyframework.dataset.model.engine.total.TotalDataAggregatePlanFactory"));
        assertDoesNotThrow(() -> Class.forName(
                "com.foggyframework.dataset.model.engine.total.AlgebraicTotalRenderer"));
        assertDoesNotThrow(() -> Class.forName(
                "com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparationFactory"));

        Set<String> engineMethods = Stream.of(JdbcModelQueryEngine.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertTrue(EXTRACTED_METHODS.stream().noneMatch(engineMethods::contains),
                () -> "JdbcModelQueryEngine still owns extracted responsibilities: "
                        + EXTRACTED_METHODS.stream().filter(engineMethods::contains).toList());
    }
}
