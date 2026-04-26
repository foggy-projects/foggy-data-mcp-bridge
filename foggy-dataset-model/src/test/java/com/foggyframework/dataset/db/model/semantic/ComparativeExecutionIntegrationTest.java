package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Comparative execution (YoY/MoM/WoW) bypassing QueryFacade.
 */
@Slf4j
@DisplayName("Comparative Execution Integration Test")
public class ComparativeExecutionIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Test
    @DisplayName("S8.6 YoY execution on FactSalesModel returns diff and ratio")
    void testYoYExecution() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        
        // Group by year and month
        SemanticQueryRequest.GroupByItem yearGroup = new SemanticQueryRequest.GroupByItem();
        yearGroup.setField("salesDate$year");
        
        SemanticQueryRequest.GroupByItem monthGroup = new SemanticQueryRequest.GroupByItem();
        monthGroup.setField("salesDate$month");
        
        request.setGroupBy(Arrays.asList(yearGroup, monthGroup));
        
        // Query the timeWindow dynamically generated columns
        request.setColumns(Arrays.asList(
                "salesDate$year", 
                "salesDate$month", 
                "salesAmount", 
                "salesAmount__prior", 
                "salesAmount__diff", 
                "salesAmount__ratio"
        ));

        // Define YoY timeWindow
        Map<String, Object> timeWindow = Map.of(
                "field", "salesDate$id",
                "grain", "month",
                "comparison", "yoy"
        );
        request.setTimeWindow(timeWindow);
        
        // Ensure some limit
        request.setLimit(50);

        SemanticQueryResponse response = semanticQueryServiceV3.queryModel(
                TEST_MODEL, request, "execute", SemanticRequestContext.empty()
        );

        assertNotNull(response);
        assertNotNull(response.getItems());
        
        log.info("Returned rows: {}", response.getItems().size());
        if (!response.getItems().isEmpty()) {
            Map<String, Object> firstRow = response.getItems().get(0);
            log.info("First row: {}", firstRow);
            
            assertTrue(firstRow.containsKey("salesAmount"));
            assertTrue(firstRow.containsKey("salesAmount__prior"));
            assertTrue(firstRow.containsKey("salesAmount__diff"));
            assertTrue(firstRow.containsKey("salesAmount__ratio"));
        }
    }
}
