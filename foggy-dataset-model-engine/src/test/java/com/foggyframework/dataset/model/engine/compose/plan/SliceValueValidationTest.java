package com.foggyframework.dataset.model.engine.compose.plan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compose slice.value fail-closed validation")
class SliceValueValidationTest {

    private BaseModelPlan base() {
        return BaseModelPlan.builder()
                .model("SaleOrderQM")
                .columns(List.of("partner$id", "partner$caption"))
                .build();
    }

    private Map<String, Object> slice(String op, Object value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("field", "partner$id");
        entry.put("op", op);
        entry.put("value", value);
        return entry;
    }

    private void assertSliceValueUnsupported(Executable executable) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(ex.getMessage().contains(QueryPlan.SLICE_VALUE_UNSUPPORTED_CODE));
        assertFalse(ex.getMessage().contains("unhashable type"));
    }

    @Test
    @DisplayName("plan.query slice.value accepts QueryPlan for in/not in")
    void planQueryAcceptsQueryPlanValue() {
        for (String op : List.of("in", "not in")) {
            BaseModelPlan prior = base();
            BaseModelPlan current = base();
            DerivedQueryPlan derived = current.query(QueryOptions.builder()
                    .columns(List.of("partner$id", "partner$caption"))
                    .slice(List.of(slice(op, prior)))
                    .build());
            assertEquals(List.of(slice(op, prior)), derived.slice());
        }
    }

    @Test
    @DisplayName("plan.query slice.value accepts explicit subquery(plan, field) for in/not in")
    void planQueryAcceptsExplicitSubqueryValue() {
        for (String op : List.of("in", "not in")) {
            BaseModelPlan prior = base();
            BaseModelPlan current = base();
            PlanSubquery rhs = Dsl.subquery(prior, "partner$id");
            DerivedQueryPlan derived = current.query(QueryOptions.builder()
                    .columns(List.of("partner$id", "partner$caption"))
                    .slice(List.of(slice(op, rhs)))
                    .build());
            assertEquals(List.of(slice(op, rhs)), derived.slice());
        }
    }

    @Test
    @DisplayName("plan.query slice.value rejects QueryPlan for non-IN operators")
    void planQueryRejectsQueryPlanForNonInOperators() {
        BaseModelPlan prior = base();
        BaseModelPlan current = base();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> current.query(QueryOptions.builder()
                        .columns(List.of("partner$id"))
                        .slice(List.of(slice("=", prior)))
                        .build()));
        assertTrue(ex.getMessage().contains(QueryPlan.SUBQUERY_VALUE_UNSUPPORTED_CODE));
        assertFalse(ex.getMessage().contains("unhashable type"));
    }

    @Test
    @DisplayName("plan.query slice.value rejects dict/object-like values for in/not in")
    void planQueryRejectsObjectValues() {
        for (String op : List.of("in", "not in")) {
            BaseModelPlan current = base();
            assertSliceValueUnsupported(() -> current.query(QueryOptions.builder()
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, Map.of("unexpected", "shape"))))
                    .build()));

            assertSliceValueUnsupported(() -> current.query(QueryOptions.builder()
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, new Object())))
                    .build()));
        }
    }

    @Test
    @DisplayName("plan.query slice.value rejects object-like list elements for in/not in")
    void planQueryRejectsObjectListElements() {
        for (String op : List.of("in", "not in")) {
            BaseModelPlan current = base();
            assertSliceValueUnsupported(() -> current.query(QueryOptions.builder()
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, List.of(Map.of("bad", "shape")))))
                    .build()));

            BaseModelPlan prior = base();
            assertSliceValueUnsupported(() -> current.query(QueryOptions.builder()
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, List.of(prior))))
                    .build()));
        }
    }

    @Test
    @DisplayName("base Dsl.from slice.value rejects object-like values")
    void baseDslFromRejectsObjectValue() {
        assertSliceValueUnsupported(() -> Dsl.from(Dsl.FromOptions.builder()
                .model("SaleOrderQM")
                .columns(List.of("partner$id"))
                .slice(List.of(slice("=", new Object())))
                .build()));
    }

    @Test
    @DisplayName("base Dsl.from slice.value accepts QueryPlan/subquery for in/not in")
    void baseDslFromAcceptsPlanSubqueryValuesForInOperators() {
        for (String op : List.of("in", "not in")) {
            BaseModelPlan prior = base();
            QueryPlan implicit = Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, prior)))
                    .build());
            assertInstanceOf(BaseModelPlan.class, implicit);
            assertEquals(List.of(slice(op, prior)), ((BaseModelPlan) implicit).slice());

            PlanSubquery explicit = Dsl.subquery(prior, "partner$id");
            QueryPlan withField = Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM")
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, explicit)))
                    .build());
            assertInstanceOf(BaseModelPlan.class, withField);
            assertEquals(List.of(slice(op, explicit)), ((BaseModelPlan) withField).slice());
        }
    }

    @Test
    @DisplayName("base Dsl.from slice.value rejects QueryPlan/subquery for non-IN operators")
    void baseDslFromRejectsPlanSubqueryValuesForNonInOperators() {
        BaseModelPlan prior = base();

        IllegalArgumentException implicit = assertThrows(IllegalArgumentException.class,
                () -> Dsl.from(Dsl.FromOptions.builder()
                        .model("SaleOrderQM")
                        .columns(List.of("partner$id"))
                        .slice(List.of(slice("=", prior)))
                        .build()));
        assertTrue(implicit.getMessage().contains(QueryPlan.SUBQUERY_VALUE_UNSUPPORTED_CODE));

        IllegalArgumentException explicit = assertThrows(IllegalArgumentException.class,
                () -> Dsl.from(Dsl.FromOptions.builder()
                        .model("SaleOrderQM")
                        .columns(List.of("partner$id"))
                        .slice(List.of(slice("=", Dsl.subquery(prior, "partner$id"))))
                        .build()));
        assertTrue(explicit.getMessage().contains(QueryPlan.SUBQUERY_VALUE_UNSUPPORTED_CODE));
    }

    @Test
    @DisplayName("scalar list values remain valid for in/not in")
    void scalarListValuesRemainValid() {
        BaseModelPlan current = base();
        for (String op : List.of("in", "not in")) {
            DerivedQueryPlan derived = current.query(QueryOptions.builder()
                    .columns(List.of("partner$id"))
                    .slice(List.of(slice(op, List.of(1, 2, 3))))
                    .build());
            assertEquals(List.of(slice(op, List.of(1, 2, 3))), derived.slice());
        }
    }
}
