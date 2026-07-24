package com.foggyframework.dataset.model.semantic.domain.pivot;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson 自定义反序列化器：处理 pivot.metrics 混合数组
 *
 * <p>支持：
 * <pre>
 * "metrics": [
 *   "salesAmount",
 *   {"name": "grossProfit", "expr": "revenueAmount - costAmount"},
 *   {"name": "share", "type": "parentShare", "of": "salesAmount"}
 * ]
 * </pre>
 */
public class PivotMetricsDeserializer extends JsonDeserializer<List<PivotMetricItem>> {

    @Override
    public List<PivotMetricItem> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<PivotMetricItem> items = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            throw ctxt.wrongTokenException(p, List.class, JsonToken.START_ARRAY,
                    "pivot.metrics 必须是数组");
        }

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                // 字符串简写 → native metric
                items.add(PivotMetricItem.ofNative(p.getText()));
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                // 对象 → 解析为 PivotMetricItem
                JsonNode node = p.readValueAsTree();
                PivotMetricItem item = new PivotMetricItem();

                if (node.has("name")) {
                    item.setName(node.get("name").asText());
                }
                if (node.has("expr")) {
                    item.setExpr(node.get("expr").asText());
                }
                if (node.has("type")) {
                    item.setType(node.get("type").asText());
                }
                if (node.has("of")) {
                    item.setOf(node.get("of").asText());
                }
                if (node.has("axis")) {
                    item.setAxis(node.get("axis").asText());
                }
                if (node.has("level")) {
                    item.setLevel(node.get("level").asText());
                }
                if (node.has("parentLevel")) {
                    item.setParentLevel(node.get("parentLevel").asText());
                }

                items.add(item);
            } else {
                throw ctxt.wrongTokenException(p, PivotMetricItem.class, JsonToken.VALUE_STRING,
                        "pivot.metrics 中的元素必须是字符串或对象");
            }
        }

        return items;
    }
}
