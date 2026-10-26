package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.compilation.PlanHash;
import com.foggyframework.dataset.model.engine.compose.normalization.PlanNormalizePipeline;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException;
import com.foggyframework.dataset.model.engine.compose.schema.SchemaDerivation;
import com.foggyframework.dataset.model.engine.compose.security.ComposePlanAwarePermissionValidator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupWindowReferenceContractTest {
    @Test void existingStringGettersAndConstructorsRemainCompatible() {
        OverClause legacy = new OverClause(List.of("category"), List.of("-id"));
        assertEquals(List.of("category"), legacy.getPartitionBy());
        assertEquals(List.of("-id"), legacy.getOrderBy());
        assertTrue(legacy.partitionReferences().isEmpty()); assertTrue(legacy.orderReferences().isEmpty());
        assertTrue(OverClause.fromMap(Map.of()).getPartitionBy().isEmpty());
        Map<String,Object> absent = new HashMap<>(); absent.put("orderBy", null);
        assertTrue(OverClause.fromMap(absent).getOrderBy().isEmpty());
    }

    @Test void referencesRemainAvailableToSchemaDependenciesAndHash() {
        var base = BaseModelPlan.builder().model("InputQM").columns(List.of("cat","v")).build();
        var wrapper = DerivedQueryPlan.builder().source(base).columns(List.of("cat AS v","v AS renamed")).build();
        var original = new PlanColumnRef(base,"v");
        var current = new PlanColumnRef(wrapper,"v");
        var grouped = wrapper.fluentGroupBy(original,current);
        assertEquals(List.of("v","v"), grouped.groupBy()); // Existing Java getter type/shape retained.
        assertEquals(List.of("renamed","v"), grouped.resolvedGroupBy(p -> SchemaDerivation.derive(p).names()));
        assertSame(original, grouped.groupByReferences().get(0));
        assertSame(grouped, PlanNormalizePipeline.defaults().normalize(grouped).normalizedPlan());
        assertNotEquals(PlanHash.planHash(grouped), PlanHash.planHash(wrapper.fluentGroupBy(current,current)));
        assertEquals(List.of("v","renamed"), SchemaDerivation.derive(grouped).names());
        var over = OverClause.fromMap(Map.of("partitionBy",List.of(original),"orderBy",List.of(current)));
        var value = new PlanColumnRef(wrapper,"renamed");
        var window = new WindowColumn("LAG",value,List.of(1),over);
        assertEquals(List.of(value,original,current), ComposePlanAwarePermissionValidator.collectPlanRefs(window));
        assertSame(original, over.partitionReferences().get(0)); assertSame(current, over.orderReferences().get(0));
    }

    @Test void groupingCannotImportAFieldFromAnExternalOrOpaqueUnionProducer() {
        var left = BaseModelPlan.builder().model("InputQM").columns(List.of("id","v")).build();
        var right = BaseModelPlan.builder().model("InputQM").columns(List.of("id","v")).build();
        assertThrows(RuntimeException.class, () -> SchemaDerivation.derive(left.fluentGroupBy(new PlanColumnRef(right,"v"))));
        var union = left.union(right,true);
        assertThrows(RuntimeException.class, () -> SchemaDerivation.derive(union.fluentGroupBy(new PlanColumnRef(left,"v"))));
        assertEquals(List.of("id","v"), SchemaDerivation.derive(union.fluentGroupBy(new PlanColumnRef(union,"v"))).names());
    }

    @Test void malformedWindowKeyArraysHaveIndexedDiagnostics() {
        for (String key : List.of("partitionBy","orderBy")) {
            var container = assertThrows(ComposeParameterException.class, () -> OverClause.fromMap(Map.of(key,"id")));
            assertEquals("over."+key,container.details().get("path"));
            var element = assertThrows(ComposeParameterException.class, () -> OverClause.fromMap(Map.of(key,List.of("id",17))));
            assertEquals("over."+key+"[1]",element.details().get("path"));
        }
    }

    @Test void aTypedKeyDoesNotAuthorizeAMissingBareKeyWithTheSameName() {
        var base = BaseModelPlan.builder().model("InputQM").columns(List.of("id","amount")).build();
        var wrapper = DerivedQueryPlan.builder().source(base).columns(List.of("id AS renamed","amount AS value")).build();
        var typed = new PlanColumnRef(base,"id");
        var value = new PlanColumnRef(wrapper,"value");
        for (var config : List.of(Map.of("partitionBy",List.of(typed),"orderBy",List.of("id")),
                Map.of("partitionBy",List.of("id"),"orderBy",List.of(typed)))) {
            var window = new WindowColumn("LAG",value,List.of(1),OverClause.fromMap(new HashMap<>(config)));
            var plan = wrapper.fluentSelect(window.as("prior"));
            assertThrows(RuntimeException.class, () -> SchemaDerivation.derive(plan));
        }
    }
}
