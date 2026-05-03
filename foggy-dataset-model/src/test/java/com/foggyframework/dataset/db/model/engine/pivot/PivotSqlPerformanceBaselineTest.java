package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import jakarta.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Pivot SQL Pushdown Performance Baseline")
public class PivotSqlPerformanceBaselineTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private DataSource dataSource;

    private static final int TARGET_ROWS = 10000;

    @BeforeAll
    public void setupData() {
        log.info("Generating synthetic data for performance baseline...");
        Integer currentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fact_sales", Integer.class);
        if (currentCount != null && currentCount >= TARGET_ROWS) {
            log.info("Data already generated. Count: " + currentCount);
            return;
        }

        // Generate synthetic data
        int batchSize = 2000;
        List<Object[]> batchArgs = new ArrayList<>();
        
        // Let's assume we have 50 products and 1000 customers
        for (int i = 0; i < TARGET_ROWS; i++) {
            int productKey = (i % 50) + 1; // 1 to 50
            int customerKey = (i % 1000) + 1; // 1 to 1000
            batchArgs.add(new Object[]{
                    "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                    1,
                    20240101 + (i % 30), // date_key
                    productKey,
                    customerKey,
                    1, // store_key
                    1, // channel_key
                    1, // promotion_key
                    1, // quantity
                    100.0, // unit_price
                    50.0, // unit_cost
                    0.0, // discount_amount
                    100.0, // sales_amount
                    50.0, // cost_amount
                    50.0, // profit_amount
                    0.0, // tax_amount
                    "COMPLETED", // order_status
                    "CREDIT_CARD" // payment_method
            });

            if (batchArgs.size() == batchSize) {
                insertBatch(batchArgs);
                batchArgs.clear();
            }
        }
        if (!batchArgs.isEmpty()) {
            insertBatch(batchArgs);
        }
        
        log.info("Synthetic data generation completed. Target rows: " + TARGET_ROWS);
    }

    @org.junit.jupiter.api.AfterAll
    public void cleanupBenchmarkData() {
        // Benchmark 插入的数据 order_id 格式为 'ORD-xxxxxxxx' (UUID)，
        // 原始测试数据格式为 'ORD20240101000001'（日期格式），不会被误删。
        int deleted = jdbcTemplate.update("DELETE FROM fact_sales WHERE order_id LIKE 'ORD-%'");
        log.info("Cleaned up {} benchmark rows from fact_sales", deleted);
    }

    private void insertBatch(List<Object[]> batchArgs) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO fact_sales (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key, quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount, tax_amount, order_status, payment_method) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchArgs);
    }

    private AxisField axis(String field) {
        AxisField af = new AxisField();
        af.setField(field);
        return af;
    }

    @Test
    public void runBenchmarks() {
        log.info("=== Starting Performance Baselines ===");

        // S1: Single row axis + limit/orderBy using additive metric
        log.info("--- S1: Pushdown Single Axis ---");
        PivotRequest s1Push = new PivotRequest();
        AxisField s1Cat = axis("product$categoryName");
        s1Cat.setOrderBy(List.of("-salesAmount"));
        s1Cat.setLimit(10);
        s1Push.setRows(List.of(s1Cat));
        s1Push.setMetrics(List.of("salesAmount"));
        s1Push.setOutputFormat("flat");
        measure("S1_Pushdown", s1Push, false);

        log.info("--- S1: Memory Single Axis ---");
        // To force memory, we can use an unsupported operation or just not use orderBy in the axis, but apply limit later?
        // Wait, if we set hierarchyMode=tree on a dummy axis, it forces memory, but we can't do that.
        // If we set crossjoin=true? No, PivotPipeline checks `hasAxisDomainOperations`. 
        // We can force memory by setting a metric that is non-additive in the ORDER BY. But salesAmount is additive.
        // Actually, we can just temporarily catch UnsupportedOperationException and mock dialect.
        // Let's use `parentShare` metric to force memory fallback! 
        // Oh wait, parentShare is evaluated after TopN. PivotPipeline canUseSqlPushdown returns false if parentShare is present!
        PivotRequest s1Mem = new PivotRequest();
        AxisField s1CatMem = axis("product$categoryName");
        s1CatMem.setOrderBy(List.of("-salesAmount"));
        s1CatMem.setLimit(10);
        s1Mem.setRows(List.of(s1CatMem));
        s1Mem.setMetrics(List.of("salesAmount"));
        s1Mem.setOutputFormat("flat");
        
        PivotPipeline.SQL_PUSHDOWN_ENABLED = false;
        try {
            measure("S1_MemoryFallback", s1Mem, true);
        } finally {
            PivotPipeline.SQL_PUSHDOWN_ENABLED = true;
        }

        // S2: Rows + Columns + limit/orderBy
        log.info("--- S2: Pushdown Rows+Cols ---");
        PivotRequest s2Push = new PivotRequest();
        AxisField s2Col = axis("salesDate$month");
        s2Col.setOrderBy(List.of("-salesAmount"));
        s2Col.setLimit(5);
        s2Push.setRows(List.of(s1Cat)); // Category Top 10
        s2Push.setColumns(List.of(s2Col)); // Month Top 5
        s2Push.setMetrics(List.of("salesAmount"));
        s2Push.setOutputFormat("flat");
        measure("S2_Pushdown", s2Push, false);

        log.info("--- S2: Memory Rows+Cols ---");
        PivotRequest s2Mem = new PivotRequest();
        s2Mem.setRows(List.of(s1Cat));
        s2Mem.setColumns(List.of(s2Col));
        s2Mem.setMetrics(List.of("salesAmount"));
        s2Mem.setOutputFormat("flat");
        
        PivotPipeline.SQL_PUSHDOWN_ENABLED = false;
        try {
            measure("S2_MemoryFallback", s2Mem, true);
        } finally {
            PivotPipeline.SQL_PUSHDOWN_ENABLED = true;
        }

        // S3: Axis having + TopN co-pushdown
        log.info("--- S3: Pushdown Having+TopN ---");
        PivotRequest s3Push = new PivotRequest();
        AxisField s3Cat = axis("product$categoryName");
        s3Cat.setOrderBy(List.of("-salesAmount"));
        s3Cat.setLimit(10);
        MetricFilter having = new MetricFilter();
        having.setMetric("salesAmount");
        having.setOp(">");
        having.setValue(100);
        s3Cat.setHaving(List.of(having));
        s3Push.setRows(List.of(s3Cat));
        s3Push.setMetrics(List.of("salesAmount"));
        s3Push.setOutputFormat("flat");
        measure("S3_Pushdown", s3Push, false);

        // S4: Keep the required benchmark dialect-portable; cascade TopN is covered by validation tests.
        log.info("--- S4: Pushdown Subtotals ---");
        PivotRequest s4Push = new PivotRequest();
        AxisField s4CatTop10 = axis("product$categoryName");
        s4CatTop10.setOrderBy(List.of("-salesAmount"));
        s4CatTop10.setLimit(10);
        s4Push.setRows(List.of(s4CatTop10));
        s4Push.setMetrics(List.of("salesAmount"));
        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        s4Push.setOptions(options);
        s4Push.setOutputFormat("flat");
        measure("S4_Pushdown_Subtotals", s4Push, false);
        
        log.info("--- S4: Memory Subtotals+COUNT_DISTINCT (non-cascade) ---");
        PivotRequest s4Mem = new PivotRequest();
        s4Mem.setRows(List.of(axis("product$categoryName"), axis("product$subCategoryName")));
        s4Mem.setMetrics(List.of("salesAmount", "uniqueCustomers"));
        s4Mem.setOptions(options);
        s4Mem.setOutputFormat("flat");
        
        PivotPipeline.SQL_PUSHDOWN_ENABLED = false;
        try {
            measure("S4_Memory_Subtotals", s4Mem, true);
        } finally {
            PivotPipeline.SQL_PUSHDOWN_ENABLED = true;
        }
        
        // S5: Large Domain Threshold (> 500)
        log.info("--- S5: Pushdown Large Domain (>500) ---");
        PivotRequest s5Push = new PivotRequest();
        AxisField s5Cust = axis("customer$caption");
        s5Cust.setOrderBy(List.of("-salesAmount"));
        s5Cust.setLimit(600); // Exceeds the 500 limit
        s5Push.setRows(List.of(s5Cust));
        s5Push.setMetrics(List.of("salesAmount", "uniqueCustomers")); // non-additive
        s5Push.setOptions(options); // Subtotals are required to trigger large domain error
        s5Push.setOutputFormat("flat");
        try {
            measure("S5_Pushdown_LargeDomain", s5Push, false);
        } catch (Exception e) {
            log.info("[Benchmark Result] S5_Pushdown_LargeDomain -> FAILED: {}", e.getMessage());
        }

        log.info("--- S5: Memory Large Domain (>500) ---");
        PivotRequest s5Mem = new PivotRequest();
        s5Mem.setRows(List.of(s5Cust));
        s5Mem.setMetrics(List.of("salesAmount", "uniqueCustomers"));
        s5Mem.setOptions(options); // Subtotals are required to trigger large domain error
        s5Mem.setOutputFormat("flat");
        
        PivotPipeline.SQL_PUSHDOWN_ENABLED = false;
        try {
            measure("S5_Memory_LargeDomain", s5Mem, true);
        } catch (Exception e) {
            log.info("[Benchmark Result] S5_Memory_LargeDomain -> FAILED: {}", e.getMessage());
        } finally {
            PivotPipeline.SQL_PUSHDOWN_ENABLED = true;
        }

        log.info("=== Performance Baselines Completed ===");
    }

    private void measure(String label, PivotRequest pivot, boolean isMemory) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        long start = System.nanoTime();
        SemanticQueryResponse response = execute(request);
        long end = System.nanoTime();

        long ms = (end - start) / 1_000_000;
        int rowCount = response.getItems() != null ? response.getItems().size() : 0;
        
        log.info("[Benchmark Result] {} -> {} ms, {} rows", label, ms, rowCount);
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        return semanticQueryServiceV3.queryModel(TEST_MODEL, request, "execute", SemanticRequestContext.empty());
    }
}
