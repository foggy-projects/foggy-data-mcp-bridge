package com.foggyframework.dataset.model.semantic.domain.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SemanticQueryRequest.OrderItem 列表的自定义反序列化器
 * <p>
 * 支持与 {@link com.foggyframework.dataset.model.def.query.request.deserializer.OrderRequestDefListDeserializer}
 * 一致的简写格式，确保 Semantic API 与 DbQueryRequest API 的 orderBy 输入形态保持一致：
 * <ul>
 *   <li>简写格式1：{@code "fieldName"} → {@code {field: "fieldName", dir: "asc"}}</li>
 *   <li>简写格式2：{@code "-fieldName"} → {@code {field: "fieldName", dir: "desc"}}</li>
 *   <li>简写格式3：{@code "+fieldName"} → {@code {field: "fieldName", dir: "asc"}}</li>
 *   <li>简写格式4：{@code "fieldName desc"} → {@code {field: "fieldName", dir: "desc"}}</li>
 *   <li>完整格式：{@code {"field": "fieldName", "dir": "desc"}}</li>
 * </ul>
 *
 * @since 1.4 B1a
 */
public class OrderItemListDeserializer extends JsonDeserializer<List<SemanticQueryRequest.OrderItem>> {

    @Override
    public List<SemanticQueryRequest.OrderItem> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<SemanticQueryRequest.OrderItem> result = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            return result;
        }

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                result.add(parseShorthand(p.getText()));
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                JsonNode node = p.readValueAsTree();
                SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
                if (node.has("field")) {
                    item.setField(node.get("field").asText());
                } else if (node.has("column")) {
                    item.setField(node.get("column").asText());
                }
                if (node.has("dir")) {
                    item.setDir(node.get("dir").asText());
                } else if (node.has("direction")) {
                    item.setDir(node.get("direction").asText());
                } else {
                    item.setDir("asc");
                }
                result.add(item);
            }
        }

        return result;
    }

    /**
     * 解析简写格式
     * <ul>
     *   <li>{@code "fieldName"} → asc</li>
     *   <li>{@code "-fieldName"} → desc</li>
     *   <li>{@code "+fieldName"} → asc</li>
     *   <li>{@code "fieldName desc"} → desc</li>
     * </ul>
     */
    private SemanticQueryRequest.OrderItem parseShorthand(String text) {
        SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
        text = text.trim();

        // 负号前缀 → 降序
        if (text.startsWith("-")) {
            item.setField(text.substring(1).trim());
            item.setDir("desc");
            return item;
        }

        // 正号前缀 → 升序
        if (text.startsWith("+")) {
            item.setField(text.substring(1).trim());
            item.setDir("asc");
            return item;
        }

        // 空格分隔格式：「field asc」或「field desc」
        int spaceIndex = text.lastIndexOf(' ');
        if (spaceIndex > 0) {
            String fieldPart = text.substring(0, spaceIndex).trim();
            String dirPart = text.substring(spaceIndex + 1).trim().toLowerCase();

            if ("asc".equals(dirPart) || "desc".equals(dirPart)) {
                item.setField(fieldPart);
                item.setDir(dirPart);
                return item;
            }
        }

        // 默认：仅字段名，升序
        item.setField(text);
        item.setDir("asc");
        return item;
    }
}
