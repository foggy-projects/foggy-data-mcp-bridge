package com.foggyframework.dataset.model.def.query.request.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * orderBy 列表的自定义反序列化器
 * <p>
 * 支持三种格式：
 * <ul>
 *   <li>简写格式1：{@code "fieldName"} → {@code {"field": "fieldName", "dir": "asc"}}</li>
 *   <li>简写格式2：{@code "fieldName desc"} → {@code {"field": "fieldName", "dir": "desc"}}</li>
 *   <li>简写格式3：{@code "-fieldName"} → {@code {"field": "fieldName", "dir": "desc"}}</li>
 *   <li>完整格式：{@code {"field": "fieldName", "dir": "desc", "nullLast": true}}</li>
 * </ul>
 * <p>
 * 可混合使用：{@code ["field1 desc", "-field2", {"field": "field3", "dir": "asc", "nullLast": true}]}
 */
public class OrderRequestDefListDeserializer extends JsonDeserializer<List<OrderRequestDef>> {

    @Override
    public List<OrderRequestDef> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<OrderRequestDef> result = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            return result;
        }

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                // 简写格式：字符串
                result.add(parseShorthand(p.getText()));
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                // 完整格式：对象
                JsonNode node = p.readValueAsTree();
                OrderRequestDef def = new OrderRequestDef();
                if (node.has("field")) {
                    def.setField(node.get("field").asText());
                }
                if (node.has("dir")) {
                    def.setDir(node.get("dir").asText());
                } else {
                    def.setDir("asc"); // 默认升序
                }
                if (node.has("nullLast")) {
                    def.setNullLast(node.get("nullLast").asBoolean());
                }
                if (node.has("nullFirst")) {
                    def.setNullFirst(node.get("nullFirst").asBoolean());
                }
                result.add(def);
            }
        }

        return result;
    }

    /**
     * 解析简写格式
     * <ul>
     *   <li>{@code "fieldName"} → asc</li>
     *   <li>{@code "fieldName asc"} → asc</li>
     *   <li>{@code "fieldName desc"} → desc</li>
     *   <li>{@code "-fieldName"} → desc</li>
     * </ul>
     */
    private OrderRequestDef parseShorthand(String text) {
        OrderRequestDef def = new OrderRequestDef();
        text = text.trim();

        // 检查负号前缀（降序）
        if (text.startsWith("-")) {
            def.setField(text.substring(1).trim());
            def.setDir("desc");
            return def;
        }

        // 检查空格分隔的格式："field asc" 或 "field desc"
        int spaceIndex = text.lastIndexOf(' ');
        if (spaceIndex > 0) {
            String fieldPart = text.substring(0, spaceIndex).trim();
            String dirPart = text.substring(spaceIndex + 1).trim().toLowerCase();

            if ("asc".equals(dirPart) || "desc".equals(dirPart)) {
                def.setField(fieldPart);
                def.setDir(dirPart);
                return def;
            }
        }

        // 默认：仅字段名，升序
        def.setField(text);
        def.setDir("asc");
        return def;
    }
}
