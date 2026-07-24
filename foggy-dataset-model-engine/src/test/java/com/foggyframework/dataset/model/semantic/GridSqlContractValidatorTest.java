package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.semantic.memorygrid.GridSqlContractValidator;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridInputBinding;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridSqlContractValidatorTest {

    @Test
    @DisplayName("Grid SQL accepts read-only queries over declared aliases")
    void acceptsReadOnlyQueryOverAliases() {
        Map<String, Object> evidence = GridSqlContractValidator.validate(request("""
                select sales.customer_id, sales.amount - ar.unpaid_amount as gap
                from sales join ar on sales.customer_id = ar.customer_id
                where sales.amount > ar.unpaid_amount
                order by gap desc
                """));

        assertEquals(true, evidence.get("grid_sql_supported"));
        assertEquals("foggy-grid-sql-v1", evidence.get("grid_sql_dialect"));
        assertEquals(List.of("sales", "ar"), evidence.get("grid_sql_aliases"));
        assertEquals("ALIAS_ONLY", evidence.get("grid_sql_resource_validation"));
    }

    @Test
    @DisplayName("Grid SQL accepts CTEs when underlying sources are declared aliases")
    void acceptsCteOverAliases() {
        Map<String, Object> evidence = GridSqlContractValidator.validate(request("""
                with ranked as (
                  select sales.customer_id, sales.amount, ar.unpaid_amount
                  from sales join ar on sales.customer_id = ar.customer_id
                )
                select customer_id, amount - unpaid_amount as gap from ranked
                """));

        assertEquals(List.of("sales", "ar"), evidence.get("grid_sql_aliases"));
        assertEquals(List.of("ranked"), evidence.get("grid_sql_with_items"));
    }

    @Test
    @DisplayName("Grid SQL rejects unbound table references")
    void rejectsUnboundTable() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(request("select * from sales join customer on sales.customer_id = customer.id")));

        assertTrue(ex.getMessage().contains(GridSqlContractValidator.RESOURCE_DENIED));
    }

    @Test
    @DisplayName("Grid SQL rejects physical schema references")
    void rejectsPhysicalSchemaReference() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(request("select * from main.sales")));

        assertTrue(ex.getMessage().contains(GridSqlContractValidator.RESOURCE_DENIED));
    }

    @Test
    @DisplayName("Grid SQL rejects DML and multi statements")
    void rejectsDmlAndMultiStatements() {
        RuntimeException dml = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(request("delete from sales")));
        RuntimeException multi = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(request("select * from sales; select * from ar")));

        assertTrue(dml.getMessage().contains(GridSqlContractValidator.STATEMENT_DENIED));
        assertTrue(multi.getMessage().contains(GridSqlContractValidator.MULTI_STATEMENT_DENIED));
    }

    @Test
    @DisplayName("Grid SQL rejects external table functions")
    void rejectsExternalTableFunction() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(request("select * from read_csv('/tmp/orders.csv')")));

        assertTrue(ex.getMessage().contains(GridSqlContractValidator.EXTERNAL_RESOURCE_DENIED));
    }

    @Test
    @DisplayName("Grid SQL rejects invalid bindings")
    void rejectsInvalidBindings() {
        RuntimeException duplicate = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(new MemoryGridRequest(
                        Map.of(),
                        "select * from sales",
                        List.of(binding("sales", "DSL"), binding("Sales", "DSL")),
                        Map.of(),
                        null)));
        RuntimeException ungoverned = assertThrows(RuntimeException.class, () ->
                GridSqlContractValidator.validate(new MemoryGridRequest(
                        Map.of(),
                        "select * from sales",
                        List.of(binding("sales", "PHYSICAL_SQL")),
                        Map.of(),
                        null)));

        assertTrue(duplicate.getMessage().contains(GridSqlContractValidator.BINDING_INVALID));
        assertTrue(ungoverned.getMessage().contains(GridSqlContractValidator.BINDING_INVALID));
    }

    private MemoryGridRequest request(String sql) {
        return new MemoryGridRequest(
                Map.of(),
                sql,
                List.of(binding("sales", "DSL_CTE"), binding("ar", "DSL")),
                Map.of("outputLimit", 200),
                null);
    }

    private MemoryGridInputBinding binding(String alias, String sourceRoute) {
        return new MemoryGridInputBinding(
                alias,
                "mgr_" + alias.toLowerCase(),
                sourceRoute,
                Map.of("row_limit", 500));
    }
}
