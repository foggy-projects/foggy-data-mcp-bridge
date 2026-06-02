package com.foggyframework.dataset.mcp.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("query_model_v3 描述文档关键边界一致性")
class QueryModelDescriptionConsistencyTest {

    private static final List<String> DESCRIPTION_FILES = List.of(
            "query_model_v3.md",
            "query_model_v3_basic.md",
            "query_model_v3_no_vector.md"
    );

    private static final List<String> REQUIRED_SNIPPETS = List.of(
            "AI 能力",
            "timeWindow (可选)",
            "`value` 可选；传入时必须是两个元素的数组",
            "`rollingAggregator` 支持 `sum` / `avg` / `count` / `min` / `max`",
            "Pivot 透视表查询",
            "`pivot` 与 `columns` 互斥",
            "`pivot` 与 `timeWindow` 互斥",
            "顶层 `orderBy` / `limit` 不作为透视轴排序或 TopN 控制",
            "parentShare",
            "teamShare",
            "amountTotal",
            "sum(amountTotal)",
            "分组后的聚合阈值",
            "Pivot 轴成员阈值",
            "pivot.rows[*].having",
            "顶层 `slice` 只用于聚合前的数据域过滤",
            "totalSales > 10000",
            "amountTotal > 10000",
            "baselineRatio",
            "ROLLUP_TO",
            "CELL_AT",
            "AXIS_MEMBER",
            "AXIS_REF",
            "Foggy 表达式 DSL",
            "DATEDIFF(...)",
            "dateMaturity",
            "2026-04-06",
            "overdueDays",
            "DSL_CTE 受控 recipe",
            "`hours_between(createdAt, firstResponseAt|resolvedAt)`",
            "`priority_threshold(priority, P1=..., P2=..., P3=...)`",
            "`business_hours_between(...)` / `working_hours_between(...)`",
            "`contract_calendar_hours_between(...)` / `service_calendar_hours_between(...)` / `calendar_hours_between(...)`",
            "`net_hours_between(...)` / `pause_excluded_hours_between(...)` / `hold_excluded_hours_between(...)` / `customer_wait_excluded_hours_between(...)`"
    );

    @Test
    @DisplayName("full/basic/no_vector 三份描述都包含关键能力边界")
    void shouldKeepCriticalQueryModelDescriptionSnippetsAligned() throws Exception {
        for (String fileName : DESCRIPTION_FILES) {
            String content = readDescription(fileName);
            for (String snippet : REQUIRED_SNIPPETS) {
                assertTrue(content.contains(snippet),
                        () -> fileName + " 缺少关键边界片段: " + snippet);
            }
        }
    }

    private String readDescription(String fileName) throws IOException {
        String resourcePath = "/schemas/descriptions/" + fileName;
        try (InputStream input = QueryModelDescriptionConsistencyTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(input, "无法加载描述文件: " + resourcePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
