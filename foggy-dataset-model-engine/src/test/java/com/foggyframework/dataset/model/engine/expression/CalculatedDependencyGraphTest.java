package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import com.foggyframework.dataset.model.engine.query_model.QueryModelSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalculatedDependencyGraphTest {
    private QueryModelSupport model(CalculatedFieldDef... fields) {
        var model = mock(QueryModelSupport.class, CALLS_REAL_METHODS);
        model.setPredefinedCalculatedFields(new ArrayList<>(List.of(fields)));
        return model;
    }
    @Test void modelGraphIsReusedAndDetectsMutableDefinitions() {
        var field = new CalculatedFieldDef("calc", "amount * 2");
        var model = model(field);
        var first = model.getCalculatedDependencyGraph();
        assertSame(first, model.getCalculatedDependencyGraph());
        field.setAlias("changedOutput");
        assertSame(first, model.getCalculatedDependencyGraph(), "output names are not dependency identities");
        field.setExpression("secret * 2");
        var changed = model.getCalculatedDependencyGraph();
        assertNotSame(first, changed);
        assertEquals(Set.of("amount"), first.base("calc"));
        assertEquals(Set.of("secret"), changed.base("calc"));
        assertThrows(UnsupportedOperationException.class, () -> first.base("calc").clear());
    }
    @Test void windowListsAreCapturedWithoutRetainingMutableDtos() {
        var order = new WindowOrderDef("secret", "ASC");
        var window = new CalculatedFieldDef("ranked", "ROW_NUMBER()");
        window.setPartitionBy(new ArrayList<>(List.of("region")));
        window.setWindowOrderBy(new ArrayList<>(List.of(order)));
        var model = model(window);var first = model.getCalculatedDependencyGraph();
        order.setField("salary");window.getPartitionBy().set(0,"team");
        assertEquals(Set.of("region","secret"),first.base("ranked"));
        assertEquals(Set.of("team","salary"),model.getCalculatedDependencyGraph().base("ranked"));
    }
    @Test void sameNamesOnDifferentModelInstancesRemainIsolated() {
        var a=model(new CalculatedFieldDef("calc","publicAmount"));
        var b=model(new CalculatedFieldDef("calc","secretAmount"));
        assertNotSame(a.getCalculatedDependencyGraph(),b.getCalculatedDependencyGraph());
        assertEquals(Set.of("publicAmount"),a.getCalculatedDependencyGraph().base("calc"));
        assertEquals(Set.of("secretAmount"),b.getCalculatedDependencyGraph().base("calc"));
    }
    @Test void requestOverlayRecomputesTransitiveClosureWithoutMutatingModel() {
        var graph=CalculatedDependencyGraph.compile(List.of(new CalculatedFieldDef("calc","amount * 2")));
        var request=new CalculatedFieldDef("amount","secret + 1");
        var overlay=graph.withRequest(List.of(request,new CalculatedFieldDef("calc","1")));
        assertEquals(Set.of("secret"),overlay.base("calc"));
        request.setExpression("another");
        assertEquals(Set.of("secret"),overlay.base("calc"));
        assertEquals(Set.of("amount"),graph.base("calc"));
        assertSame(graph,graph.withRequest(List.of(new CalculatedFieldDef("calc","1"))));
    }
    @Test void invalidModelChangesDoNotReturnStaleGraphs() {
        var field=new CalculatedFieldDef("calc","amount");var model=model(field);
        var previous=model.getCalculatedDependencyGraph();field.setExpression("calc + 1");
        assertThrows(IllegalArgumentException.class,model::getCalculatedDependencyGraph);
        assertThrows(IllegalArgumentException.class,model::getCalculatedDependencyGraph);
        field.setExpression("amount");assertSame(previous,model.getCalculatedDependencyGraph());
    }
    @Test void cyclesFailClosedButInjectionCanCollectTransientInlineAliasEdges() {
        assertThrows(IllegalArgumentException.class,()->CalculatedDependencyGraph.compile(List.of(
                new CalculatedFieldDef("a","b"),new CalculatedFieldDef("b","a"))));
        var graph=CalculatedDependencyGraph.compile(List.of()).withRequest(List.of(new CalculatedFieldDef("amount","SUM(amount)")));
        var required=new LinkedHashSet<>(Set.of("amount"));graph.includeDependencies(required);
        assertEquals(Set.of("amount"),required);
        assertThrows(IllegalArgumentException.class,()->graph.base("amount"));
        assertThrows(RuntimeException.class,()->CalculatedDependencyGraph.compile(List.of(new CalculatedFieldDef("x","unknown_function(secret)"))));
    }
    @Test void topologyAndRecognizedPostAggregateGrammarRemainCompatible() {
        var graph=CalculatedDependencyGraph.compile(List.of(new CalculatedFieldDef("finalCalc","firstCalc + 1"),
                new CalculatedFieldDef("firstCalc","amount * 2"),new CalculatedFieldDef("share","ratio_to_total(finalCalc)")));
        assertEquals(List.of("firstCalc","finalCalc","share"),graph.topologicalOrder());
        assertEquals(Set.of("amount"),graph.base("share"));
        var required=new LinkedHashSet<>(Set.of("share"));graph.includeDependencies(required);
        assertEquals(Set.of("share","finalCalc","firstCalc","amount"),required);
        assertThrows(UnsupportedOperationException.class,()->graph.topologicalOrder().clear());
    }
    @Test void concurrentReadersShareOneImmutableModelGraph() throws Exception {
        var model=model(new CalculatedFieldDef("calc","amount + secret"));
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<CalculatedDependencyGraph>> calls=new ArrayList<>();
            for(int i=0;i<64;i++)calls.add(model::getCalculatedDependencyGraph);
            var results=pool.invokeAll(calls);var first=results.get(0).get();
            for(var result:results)assertSame(first,result.get());
            assertEquals(Set.of("amount","secret"),first.base("calc"));
        } finally {pool.shutdownNow();}
    }
}
