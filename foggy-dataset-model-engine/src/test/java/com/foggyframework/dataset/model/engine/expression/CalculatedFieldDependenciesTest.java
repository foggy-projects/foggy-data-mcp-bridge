package com.foggyframework.dataset.model.engine.expression;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.WindowOrderDef;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CalculatedFieldDependenciesTest {
    @Test void closureIncludesTransitiveWindowSpecifications() {
        var partition = new CalculatedFieldDef("partition", "region + 1");
        var order = new CalculatedFieldDef("ordered", "secret * 2");
        var window = new CalculatedFieldDef("ranked", "ROW_NUMBER()");
        window.setPartitionBy(List.of("partition"));
        var sort = new WindowOrderDef(); sort.setField("ordered"); sort.setDir("ASC");
        window.setWindowOrderBy(List.of(sort));
        var definitions = CalculatedFieldDependencies.index(List.of(window, partition, order));
        assertEquals(Set.of("region", "secret"), CalculatedFieldDependencies.base(window, definitions));
        var required = new LinkedHashSet<>(Set.of("ranked"));
        CalculatedFieldDependencies.includeDependencies(required, definitions);
        assertEquals(Set.of("ranked", "partition", "ordered", "region", "secret"), required);
    }
    @Test void cyclesAndUnparseableExpressionsFailClosed() {
        var a = new CalculatedFieldDef("a", "b + 1");
        var b = new CalculatedFieldDef("b", "a + 1");
        assertThrows(IllegalArgumentException.class, () -> CalculatedFieldDependencies.base(a, CalculatedFieldDependencies.index(List.of(a, b))));
        assertThrows(RuntimeException.class, () -> CalculatedFieldDependencies.base("amount + (", Map.of()));
    }
    @Test void repeatedDependenciesAreNotCyclesAndAliasesAreNotInputs() {
        var a = new CalculatedFieldDef("a", "amount * 2"); a.setAlias("out");
        var b = new CalculatedFieldDef("b", "a + amount");
        var definitions = CalculatedFieldDependencies.index(List.of(a, b));
        assertEquals(Set.of("amount"), CalculatedFieldDependencies.base("a + b", definitions));
        assertEquals(Set.of("out"), CalculatedFieldDependencies.base("out", definitions));
    }
    @Test void recognizedPostAggregateSyntaxRetainsItsDependency() {
        for (String expression : List.of("ratio_to_total(total)", "rank_by(total)",
                "total / NULLIF(SUM(total) OVER (), 0)")) {
            assertEquals(Set.of("total"), CalculatedFieldDependencies.direct(new CalculatedFieldDef("share", expression)));
        }
        assertThrows(RuntimeException.class, () -> CalculatedFieldDependencies.direct(new CalculatedFieldDef("unsafe", "unknown_function(secret)")));
    }

}
