package com.foggyframework.dataset.db.model.def.query.request.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * groupBy 列表的自定义反序列化器
 * <p>
 * 支持两种格式：
 * <ul>
 *   <li>简写格式（字符串）：{@code "fieldName"} → {@code {"field": "fieldName"}}</li>
 *   <li>完整格式（对象）：{@code {"field": "fieldName", "agg": "SUM"}}</li>
 * </ul>
 * <p>
 * 可混合使用：{@code ["field1", {"field": "field2", "agg": "AVG"}]}
 */
public class GroupRequestDefListDeserializer extends JsonDeserializer<List<GroupRequestDef>> {

    @Override
    public List<GroupRequestDef> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<GroupRequestDef> result = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            return result;
        }

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                // 简写格式：字符串 → GroupRequestDef
                GroupRequestDef def = new GroupRequestDef();
                def.setField(p.getText());
                result.add(def);
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                // 完整格式：对象
                JsonNode node = p.readValueAsTree();
                GroupRequestDef def = new GroupRequestDef();
                if (node.has("field")) {
                    def.setField(node.get("field").asText());
                }
                if (node.has("agg")) {
                    def.setAgg(node.get("agg").asText());
                }
                result.add(def);
            }
        }

        return result;
    }
}
