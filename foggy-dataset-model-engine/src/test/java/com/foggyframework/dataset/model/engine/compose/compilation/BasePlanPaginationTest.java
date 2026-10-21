package com.foggyframework.dataset.model.engine.compose.compilation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class BasePlanPaginationTest {
    @ParameterizedTest @ValueSource(strings={"sqlite","postgres","postgresql","mysql","mysql57","mysql8"})
    void finiteWindowAndNoImplicitCap(String dialect) {
        String sql="SELECT id FROM t WHERE amount > ? ORDER BY id";
        assertEquals(sql,BasePlanPagination.render(sql,dialect,null,null));
        assertEquals(sql+" LIMIT 2 OFFSET 3",BasePlanPagination.render(sql,dialect,2,3));
        assertEquals(sql+" LIMIT 0",BasePlanPagination.render(sql,dialect,0,null));
    }
    @Test void offsetOnlyUsesDialectUnlimitedForm() {
        assertEquals("SELECT id FROM t LIMIT -1 OFFSET 2",BasePlanPagination.render("SELECT id FROM t","sqlite",null,2));
        assertEquals("SELECT id FROM t LIMIT 18446744073709551615 OFFSET 2",BasePlanPagination.render("SELECT id FROM t","mysql",null,2));
        assertEquals("SELECT id FROM t OFFSET 2",BasePlanPagination.render("SELECT id FROM t","postgres",null,2));
    }
    @Test void sqlServerZeroAndUnorderedLimitUseTop() {
        assertTrue(BasePlanPagination.render("SELECT DISTINCT id FROM t ORDER BY id","mssql",0,3).contains("TOP 0"));
        assertTrue(BasePlanPagination.render("SELECT id FROM t","sqlserver",2,null).contains("TOP 2"));
    }
    @Test void sqlServerOffsetKeepsTopLevelOrderAndRequiresIt() {
        String sql="SELECT [id], ROW_NUMBER() OVER (ORDER BY [id]) AS n FROM t ORDER BY [id]";
        assertEquals(sql+" OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY",BasePlanPagination.render(sql,"mssql",3,2));
        assertEquals(sql+" OFFSET 0 ROWS",BasePlanPagination.render(sql,"mssql",null,null));
        assertThrows(ComposeCompileException.class,()->BasePlanPagination.render(
            "SELECT ROW_NUMBER() OVER (ORDER BY id) AS n FROM t","mssql",3,2));
    }
    @Test void neverSilentlyDoublePagesAPortResult() {
        assertThrows(ComposeCompileException.class,()->BasePlanPagination.render("SELECT id FROM t LIMIT 1","sqlite",2,0));
    }
}
