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

    private static boolean hasValidationError(Set<ValidationMessage> errors, String type, String instanceLocation) {
        return errors.stream().anyMatch(error ->
                type.equals(error.getType()) && instanceLocation.equals(error.getInstanceLocation().toString()));
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
    @DisplayName("合规：timeWindow value 缺省")
    void testValidTimeWindowWithoutValue() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "columns": ["salesAmount", "salesAmount__prior", "salesAmount__ratio"],
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "month",
              "comparison": "yoy",
              "targetMetrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "timeWindow.value 缺省应通过 schema 验证: " + errors);
    }

    @Test
    @DisplayName("合规：timeWindow value 恰好两个元素")
    void testValidTimeWindowWithTwoValueElements() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "columns": ["salesAmount", "salesAmount__rolling_7d"],
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "day",
              "comparison": "rolling_7d",
              "value": ["-30d", "now"],
              "targetMetrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "timeWindow.value 两元素数组应通过 schema 验证: " + errors);
    }

    @Test
    @DisplayName("违规：timeWindow value 少于两个元素")
    void testInvalidTimeWindowValueTooShort() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "month",
              "comparison": "yoy",
              "value": ["2025-01-01"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "timeWindow.value 少于两个元素应被 schema 拒绝");
        assertTrue(hasValidationError(errors, "minItems", "$.payload.timeWindow.value"),
                "应报告 minItems 错误: " + errors);
    }

    @Test
    @DisplayName("违规：timeWindow value 多于两个元素")
    void testInvalidTimeWindowValueTooLong() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "month",
              "comparison": "yoy",
              "value": ["2025-01-01", "2025-02-01", "2025-03-01"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "timeWindow.value 多于两个元素应被 schema 拒绝");
        assertTrue(hasValidationError(errors, "maxItems", "$.payload.timeWindow.value"),
                "应报告 maxItems 错误: " + errors);
    }

    @Test
    @DisplayName("合规：timeWindow rollingAggregator 支持 min/max")
    void testValidTimeWindowRollingAggregatorMinMax() throws Exception {
        String template = """
        {
          "model": "FactSales",
          "payload": {
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "day",
              "comparison": "rolling_30d",
              "rollingAggregator": "%s",
              "targetMetrics": ["salesAmount"]
            }
          }
        }
        """;

        Set<ValidationMessage> minErrors = validate(String.format(template, "min"));
        Set<ValidationMessage> maxErrors = validate(String.format(template, "max"));

        assertTrue(minErrors.isEmpty(), "rollingAggregator=min 应通过 schema 验证: " + minErrors);
        assertTrue(maxErrors.isEmpty(), "rollingAggregator=max 应通过 schema 验证: " + maxErrors);
    }

    @Test
    @DisplayName("违规：timeWindow rollingAggregator 拒绝未开放枚举")
    void testInvalidTimeWindowRollingAggregator() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "timeWindow": {
              "field": "salesDate$id",
              "grain": "day",
              "comparison": "rolling_30d",
              "rollingAggregator": "median",
              "targetMetrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "未开放 rollingAggregator 应被 schema 拒绝");
        assertTrue(hasValidationError(errors, "enum", "$.payload.timeWindow.rollingAggregator"),
                "应报告 rollingAggregator enum 错误: " + errors);
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

    @Test
    @DisplayName("违规：对象形式的 axis 缺少 field 必填项")
    void testInvalidPivotAxisObjectWithoutField() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {
                  "limit": 3,
                  "orderBy": ["-salesAmount"]
                }
              ],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "缺少 field 必填项应触发拦截");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("field")), "应报告 field 缺失");
    }

    @Test
    @DisplayName("违规：对象形式的 axis limit 类型错误")
    void testInvalidPivotAxisObjectLimitType() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {
                  "field": "category",
                  "limit": "three"
                }
              ],
              "metrics": ["salesAmount"]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "limit 为 string 应触发拦截");
        assertTrue(errors.stream().anyMatch(e -> e.getMessage().contains("limit")), "应报告 limit 类型错误");
    }

    // ========== S11: parentShare Schema Tests ==========

    @Test
    @DisplayName("合规：parentShare 对象指标")
    void testValidParentShareMetric() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {"field": "category"},
                {"field": "subCategory"}
              ],
              "metrics": [
                "salesAmount",
                {"name": "share", "type": "parentShare", "of": "salesAmount"}
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 parentShare 应通过验证: " + errors);
    }

    @Test
    @DisplayName("合规：parentShare 带 axis=rows")
    void testValidParentShareWithAxisRows() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {"field": "category"},
                {"field": "subCategory"}
              ],
              "metrics": [
                "salesAmount",
                {"name": "share", "type": "parentShare", "of": "salesAmount", "axis": "rows"}
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 parentShare + axis=rows 应通过验证: " + errors);
    }

    @Test
    @DisplayName("违规：expr 指标被 schema 禁止（无 expr 属性）")
    void testInvalidExprMetric() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["category"],
              "metrics": [
                "revenue",
                {"name": "profit", "expr": "revenue - cost"}
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "expr 指标应被 schema 拒绝");
        boolean hasExprError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("expr") || msg.getMessage().contains("additionalProperties"));
        assertTrue(hasExprError, "应报告 expr 属性不允许错误: " + errors);
        log.info("expr metric schema errors: {}", errors);
    }

    @Test
    @DisplayName("违规：axis=columns 被 schema enum 拒绝")
    void testInvalidAxisColumns() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {"field": "category"},
                {"field": "subCategory"}
              ],
              "metrics": [
                "salesAmount",
                {"name": "share", "type": "parentShare", "of": "salesAmount", "axis": "columns"}
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "axis=columns 应触发 schema enum 验证失败");
        boolean foundEnumError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("rows") || msg.getMessage().contains("enum"));
        assertTrue(foundEnumError, "应触发 axis enum 验证失败: " + errors);
    }

    @Test
    @DisplayName("违规：parentShare 缺少 of → schema required 前置拦截")
    void testParentShareMissingOfSchemaNote() throws Exception {
        // S11 fail-closed: of 是 parentShare 的必填契约，必须在 schema 层前置拒绝
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": [
                {"field": "category"},
                {"field": "subCategory"}
              ],
              "metrics": [
                "salesAmount",
                {"name": "share", "type": "parentShare"}
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "缺少 of 属性应被 schema 拒绝");
        boolean hasRequiredError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("of") || msg.getMessage().contains("required"));
        assertTrue(hasRequiredError, "应报告 of 是必填属性错误: " + errors);
        log.info("parentShare missing of schema errors: {}", errors);
    }

    // ========== S12 baselineRatio ==========

    @Test
    @DisplayName("合规：baselineRatio metric")
    void testValidBaselineRatioMetric() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["category"],
              "columns": ["month"],
              "metrics": [
                "salesAmount",
                {
                  "name": "idx",
                  "type": "baselineRatio",
                  "of": "salesAmount",
                  "axis": "columns",
                  "baseline": "first"
                }
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertTrue(errors.isEmpty(), "合规 baselineRatio 应通过验证: " + errors);
    }

    @Test
    @DisplayName("违规：baselineRatio axis=rows 拒绝")
    void testInvalidBaselineRatioAxis() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["category"],
              "columns": ["month"],
              "metrics": [
                "salesAmount",
                {
                  "name": "idx",
                  "type": "baselineRatio",
                  "of": "salesAmount",
                  "axis": "rows",
                  "baseline": "first"
                }
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "baselineRatio axis=rows 应被拒绝");
        boolean hasAxisError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("columns") || msg.getMessage().contains("enum") || msg.getMessage().contains("oneOf"));
        assertTrue(hasAxisError, "应报告 axis 不匹配错误: " + errors);
    }

    @Test
    @DisplayName("违规：baselineRatio 缺少 baseline 拒绝")
    void testInvalidBaselineRatioMissingBaseline() throws Exception {
        String json = """
        {
          "model": "FactSales",
          "payload": {
            "pivot": {
              "rows": ["category"],
              "columns": ["month"],
              "metrics": [
                "salesAmount",
                {
                  "name": "idx",
                  "type": "baselineRatio",
                  "of": "salesAmount",
                  "axis": "columns"
                }
              ]
            }
          }
        }
        """;
        Set<ValidationMessage> errors = validate(json);
        assertFalse(errors.isEmpty(), "baselineRatio 缺少 baseline 应被拒绝");
        boolean hasRequiredError = errors.stream()
                .anyMatch(msg -> msg.getMessage().contains("baseline") || msg.getMessage().contains("required") || msg.getMessage().contains("oneOf"));
        assertTrue(hasRequiredError, "应报告 baseline 必填错误: " + errors);
    }
}
