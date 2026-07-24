package com.foggyframework.dataset.model.engine.compose.normalization;

import com.foggyframework.dataset.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.model.engine.compose.plan.DerivedQueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.model.engine.compose.plan.JoinType;
import com.foggyframework.dataset.model.engine.compose.plan.PlanColumnRef;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.model.plugins.pipeline.LoopDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanNormalizePipelineTest {

    @Test
    void noRulesReturnOriginalPlanWithoutLoop() {
        QueryPlan base = base("Order");
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of());

        PlanNormalizeResult result = pipeline.normalize(base);

        assertSame(base, result.originalPlan());
        assertSame(base, result.normalizedPlan());
        assertFalse(result.changed());
        assertEquals(0, result.loopCount());
        assertEquals(0, result.loopTrace().size());
    }

    @Test
    void maxLoopCountZeroDisablesRules() {
        QueryPlan base = base("Order");
        CountingRule rule = new CountingRule(LoopDecision.changed("changed"));
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        PlanNormalizeResult result = pipeline.normalize(base,
                PlanNormalizeOptions.builder().maxLoopCount(0).build());

        assertSame(base, result.normalizedPlan());
        assertEquals(0, rule.count);
        assertEquals(0, result.loopTrace().size());
    }

    @Test
    void unchangedRuleStopsAfterOnePass() {
        QueryPlan base = base("Order");
        CountingRule rule = new CountingRule(LoopDecision.unchanged("stable"));
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        PlanNormalizeResult result = pipeline.normalize(base);

        assertSame(base, result.normalizedPlan());
        assertFalse(result.changed());
        assertEquals(1, result.loopCount());
        assertEquals(1, rule.count);
        assertEquals("stable", result.loopTrace().get(0).getReason());
    }

    @Test
    void changedThenUnchangedStopsWithTrace() {
        QueryPlan base = base("Order");
        QueryPlan replacement = base("NormalizedOrder");
        ReplacingRule rule = new ReplacingRule(replacement);
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        PlanNormalizeResult result = pipeline.normalize(base);

        assertSame(replacement, result.normalizedPlan());
        assertTrue(result.changed());
        assertEquals(2, result.loopCount());
        assertEquals(2, rule.count);
        assertEquals(2, result.loopTrace().size());
        assertEquals("replaced", result.loopTrace().get(0).getReason());
        assertEquals("stable", result.loopTrace().get(1).getReason());
    }

    @Test
    void explicitStopReturnsResultAndReason() {
        QueryPlan base = base("Order");
        CountingRule rule = new CountingRule(LoopDecision.stop("done"));
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        PlanNormalizeResult result = pipeline.normalize(base);

        assertSame(base, result.normalizedPlan());
        assertEquals(1, result.loopCount());
        assertEquals("done", result.stopReason());
        assertEquals(1, result.loopTrace().size());
    }

    @Test
    void failDecisionFailsClosed() {
        QueryPlan base = base("Order");
        CountingRule rule = new CountingRule(LoopDecision.fail("bad rule"));
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> pipeline.normalize(base));

        assertEquals("Plan normalization failed at rule CountingRule: bad rule", ex.getMessage());
    }

    @Test
    void changedEveryPassExceedsMaxLoopCount() {
        QueryPlan base = base("Order");
        CountingRule rule = new CountingRule(LoopDecision.changed("still changing"));
        PlanNormalizePipeline pipeline = new PlanNormalizePipeline(List.of(rule));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> pipeline.normalize(base, PlanNormalizeOptions.builder().maxLoopCount(2).build()));

        assertEquals("Plan normalization exceeded maxLoopCount=2", ex.getMessage());
        assertEquals(2, rule.count);
    }

    @Test
    void removeEmptyDerivedWrapperAtRoot() {
        QueryPlan base = base("Order");
        QueryPlan wrapped = DerivedQueryPlan.builder()
                .source(DerivedQueryPlan.builder().source(base).build())
                .build();

        PlanNormalizeResult result = PlanNormalizePipeline.defaults().normalize(wrapped);

        assertSame(base, result.normalizedPlan());
        assertTrue(result.changed());
        assertEquals(2, result.loopCount());
        assertEquals("removed empty derived wrapper", result.loopTrace().get(0).getReason());
        assertEquals("no empty derived wrapper", result.loopTrace().get(1).getReason());
    }

    @Test
    void nonEmptyDerivedWrapperIsPreserved() {
        QueryPlan base = base("Order");
        QueryPlan wrapped = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .build();

        PlanNormalizeResult result = PlanNormalizePipeline.defaults().normalize(wrapped);

        assertSame(wrapped, result.normalizedPlan());
        assertFalse(result.changed());
        assertEquals(1, result.loopCount());
    }

    @Test
    void nonEmptyDerivedSourceWrapperIsPreservedForPlanQualifiedIdentity() {
        QueryPlan base = base("Order");
        QueryPlan sourceWrapper = DerivedQueryPlan.builder()
                .source(base)
                .columns(List.of("id"))
                .build();
        QueryPlan planQualifiedOuter = DerivedQueryPlan.builder()
                .source(sourceWrapper)
                .columns(List.of(new PlanColumnRef(sourceWrapper, "id")))
                .build();

        PlanNormalizeResult result = PlanNormalizePipeline.defaults().normalize(planQualifiedOuter);

        assertSame(planQualifiedOuter, result.normalizedPlan());
        assertFalse(result.changed());
    }

    @Test
    void removeEmptyDerivedWrapperInsideJoinAndUnionBranches() {
        QueryPlan left = base("LeftOrder");
        QueryPlan right = base("RightOrder");
        QueryPlan wrappedLeft = DerivedQueryPlan.builder().source(left).build();
        QueryPlan wrappedRight = DerivedQueryPlan.builder().source(right).build();
        JoinPlan join = JoinPlan.builder()
                .left(wrappedLeft)
                .right(wrappedRight)
                .type(JoinType.INNER)
                .on(List.of(JoinOn.of("id", "=", "id")))
                .build();
        UnionPlan root = UnionPlan.builder()
                .left(DerivedQueryPlan.builder().source(join).build())
                .right(DerivedQueryPlan.builder().source(left).build())
                .build();

        PlanNormalizeResult result = PlanNormalizePipeline.defaults().normalize(root);

        UnionPlan normalizedUnion = assertInstanceOf(UnionPlan.class, result.normalizedPlan());
        JoinPlan normalizedJoin = assertInstanceOf(JoinPlan.class, normalizedUnion.left());
        assertSame(left, normalizedJoin.left());
        assertSame(right, normalizedJoin.right());
        assertSame(left, normalizedUnion.right());
        assertTrue(result.changed());
    }

    private static BaseModelPlan base(String model) {
        return BaseModelPlan.builder()
                .model(model)
                .columns(List.of("id"))
                .build();
    }

    private static final class CountingRule implements PlanNormalizeRule {

        private final LoopDecision decision;
        private int count;

        private CountingRule(LoopDecision decision) {
            this.decision = decision;
        }

        @Override
        public LoopDecision apply(PlanNormalizeContext ctx) {
            count++;
            return decision;
        }
    }

    private static final class ReplacingRule implements PlanNormalizeRule {

        private final QueryPlan replacement;
        private int count;

        private ReplacingRule(QueryPlan replacement) {
            this.replacement = replacement;
        }

        @Override
        public LoopDecision apply(PlanNormalizeContext ctx) {
            count++;
            if (ctx.getPlan() == replacement) {
                return LoopDecision.unchanged("stable");
            }
            ctx.setPlan(replacement);
            return LoopDecision.changed("replaced");
        }
    }
}
