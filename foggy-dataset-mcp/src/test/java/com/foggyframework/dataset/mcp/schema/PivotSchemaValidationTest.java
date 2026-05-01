package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("query_model_v3_schema Pivot 扩展与防漏验证")
class PivotSchemaValidationTest {

    private static final Logger log = LoggerFactory.getLogger(PivotSchemaValidationTest.class);

    private static JsonSchema schema;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream is = PivotSchemaValidationTest.class.getResourceAsStream("/schemas/query_model_v3_schema.json");
        assertNotNull(is, "无法加载 query_model_v3_schema.json");
        schema = factory.getSchema(is);
    }

    private Set<ValidationMessage> validate(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return schema.validate(node);
    }

    @Test
    @DisplayName("合规：Flat Pivot (仅 rows + metrics)")
    void testValidFlatPivot() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["region", "city"],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 Flat Pivot 应该通过验证: " + errors);
    }

    @Test
    @DisplayName("合规：Grid Pivot 带有 options 和 layout")
    void testValidGridPivot() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["region"],
              "columns": ["month"],
              "metrics": ["salesAmount", "orderCount"],
              "outputFormat": "grid",
              "options": {
                "crossjoin": true,
                "rowSubtotals": true
              },
              "layout": {
                "metricPlacement": "rows"
              }
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 Grid Pivot 应该通过验证: " + errors);
    }

    @Test
    @DisplayName("合规：对象形态 rows 且包含 having")
    void testValidPivotHaving() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {
                  "field": "category",
                  "limit": 10,
                  "orderBy": ["-salesAmount"],
                  "having": [
                    { "metric": "salesAmount", "op": ">=", "value": 5000 }
                  ]
                }
              ],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 Having Pivot 应该通过验证: " + errors);
    }

    @Test
    @DisplayName("违规：pivot 与 columns 互斥")
    void testInvalidMutuallyExclusiveColumns() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "columns": ["region", "salesAmount"],
            "pivot": {
              "rows": ["region"],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "pivot 与 columns 同时存在应触发拦截");
        
        boolean foundMutuallyExclusiveError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("allOf"));
        assertTrue(foundMutuallyExclusiveError, "应触发 allOf 互斥验证失败");
    }

    @Test
    @DisplayName("违规：pivot 与 timeWindow 互斥")
    void testInvalidMutuallyExclusiveTimeWindow() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "timeWindow": {
              "field": "date",
              "grain": "month",
              "comparison": "yoy"
            },
            "pivot": {
              "rows": ["region"],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "pivot 与 timeWindow 同时存在应触发拦截");
    }

    @Test
    @DisplayName("违规：非法的 metricPlacement 枚举")
    void testInvalidMetricPlacement() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["region"],
              "metrics": ["salesAmount"],
              "layout": {
                "metricPlacement": "unknown"
              }
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "应产生验证错误");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("columns")), 
                   "应触发 metricPlacement 的枚举类型验证错误, 实际错误: " + errors);
    }
}
