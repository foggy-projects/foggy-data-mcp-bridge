package com.foggyframework.dataset.model.impl.query;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryFieldBindingsTest {
    @Test void largeResultsResolveEachSelectedColumnOnlyOnce() {
        var model=mock(QueryModel.class);var column=mock(DbQueryColumn.class);
        when(model.findJdbcColumnForSelectByName("amount",false)).thenReturn(column);when(column.getAlias()).thenReturn("out");
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<10000;i++)rows.add(Map.of("out",i));
        var result=QueryFieldBindings.logicalRows(rows,model,List.of("amount"),List.of());
        assertEquals(Map.of("amount",9999),result.get(9999));
        assertEquals(Map.of("out",0),rows.get(0));
        verify(model,times(1)).findJdbcColumnForSelectByName("amount",false);
    }
    @Test void projectionCapturesNamesAndKeepsModelProvenance() {
        var model=mock(QueryModel.class);var column=mock(DbQueryColumn.class);
        when(model.findJdbcColumnForSelectByName("amount",false)).thenReturn(column);when(column.getAlias()).thenReturn("out");
        var fields=new ArrayList<>(List.of("amount"));var projection=QueryFieldBindings.compile(model,fields,List.of());
        fields.clear();when(column.getAlias()).thenReturn("changed");
        assertSame(column,projection.bindings().get(0).source());
        assertEquals(List.of(Map.of("amount",3)),projection.logicalRows(List.of(Map.of("out",3))));
        assertThrows(UnsupportedOperationException.class,()->projection.bindings().clear());
    }
    @Test void explicitAsWinsOverFormulaOutputAndDoesNotBecomeInputAlias() {
        var formula=new CalculatedFieldDef("calc","amount * 2");formula.setAlias("formulaOut");
        assertEquals("formulaOut",QueryFieldBindings.bind(null,"calc",List.of(formula)).outputName());
        var as=QueryFieldBindings.bind(null,"calc AS requestOut",List.of(formula));
        assertEquals("requestOut",as.outputName());assertEquals("calc",as.expression());
        assertEquals("formulaOut",QueryFieldBindings.bind(null,"formulaOut",List.of(formula)).expression());
    }
    @Test void crossedOutputNamesAreMappedWithoutInPlaceOverwrite() {
        var a=new CalculatedFieldDef("a","x");a.setAlias("b");var b=new CalculatedFieldDef("b","y");b.setAlias("a");
        var original=Map.<String,Object>of("a",10,"b",20);
        var rows=QueryFieldBindings.logicalRows(List.of(original),null,List.of("a","b"),List.of(a,b));
        assertEquals(Map.of("a",20,"b",10),rows.get(0));assertEquals(Map.of("a",10,"b",20),original);
    }
    @Test void sqlAndRowsUseTheSameCompiledProjection() {
        var dialect=mock(FDialect.class);when(dialect.quoteIdentifier(anyString())).thenAnswer(i->"["+i.getArgument(0)+"]");
        var calc=new CalculatedFieldDef("calc","amount");calc.setAlias("out");
        var plan=QueryFieldBindings.compile(null,List.of("calc"),List.of(calc));
        assertEquals("SELECT fb.[out] AS [calc] FROM (SELECT 7 AS [out]) fb",plan.logicalSql("SELECT 7 AS [out]",dialect));
        assertEquals(List.of(Map.of("calc",7)),plan.logicalRows(List.of(Map.of("out",7))));
        assertEquals("SELECT 1",QueryFieldBindings.compile(null,List.of("x"),List.of()).logicalSql("SELECT 1",dialect));
    }
}
