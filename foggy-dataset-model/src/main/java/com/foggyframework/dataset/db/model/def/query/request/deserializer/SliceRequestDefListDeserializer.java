package com.foggyframework.dataset.db.model.def.query.request.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * slice 列表的自定义反序列化器
 * <p>
 * 支持三种格式：
 * <ul>
 *   <li>简写格式（map.size==1 且 key 不是保留字）：{@code {"orderStatus": "COMPLETED"}} → {@code {"field": "orderStatus", "op": "=", "value": "COMPLETED"}}</li>
 *   <li>完整格式：{@code {"field": "amount", "op": ">=", "value": 100}}</li>
 *   <li>逻辑组格式：{@code {"$or": [...]}} 或 {@code {"$and": [...]}}</li>
 * </ul>
 * <p>
 * 可混合使用：{@code [{"orderStatus": "COMPLETED"}, {"field": "amount", "op": ">=", "value": 100}]}
 */
public class SliceRequestDefListDeserializer extends JsonDeserializer<List<SliceRequestDef>> {

    /**
     * 保留字段名（不作为简写格式的 key）
     */
    private static final Set<String> RESERVED_KEYS = Set.of(
            "$or", "$and", "field", "op", "value", "maxDepth"
    );

    @Override
    public List<SliceRequestDef> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        List<SliceRequestDef> result = new ArrayList<>();

        if (p.currentToken() != JsonToken.START_ARRAY) {
            return result;
        }

        ObjectMapper mapper = (ObjectMapper) p.getCodec();

        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() == JsonToken.START_OBJECT) {
                JsonNode node = p.readValueAsTree();
                SliceRequestDef def = parseSliceItem(node, mapper);
                if (def != null) {
                    result.add(def);
                }
            }
        }

        return result;
    }

    /**
     * 解析单个 slice 项
     */
    private SliceRequestDef parseSliceItem(JsonNode node, ObjectMapper mapper) {
        // 获取所有字段名
        List<String> fieldNames = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            fieldNames.add(it.next());
        }

        // 判断是否为简写格式：map.size == 1 且 key 不是保留字
        if (fieldNames.size() == 1) {
            String key = fieldNames.get(0);

            // $or 或 $and 逻辑组
            if ("$or".equals(key)) {
                return parseLogicalGroup(node.get("$or"), true, mapper);
            }
            if ("$and".equals(key)) {
                return parseLogicalGroup(node.get("$and"), false, mapper);
            }

            // 简写格式：{ "fieldName": value } → { field, op: "=", value }
            if (!RESERVED_KEYS.contains(key)) {
                SliceRequestDef def = new SliceRequestDef();
                def.setField(key);
                def.setOp("=");
                def.setValue(nodeToValue(node.get(key)));
                return def;
            }
        }

        // 完整格式
        return parseFullFormat(node, mapper);
    }

    /**
     * 解析完整格式
     */
    private SliceRequestDef parseFullFormat(JsonNode node, ObjectMapper mapper) {
        SliceRequestDef def = new SliceRequestDef();

        if (node.has("field")) {
            def.setField(node.get("field").asText());
        }
        if (node.has("op")) {
            def.setOp(node.get("op").asText());
        }
        if (node.has("value")) {
            def.setValue(nodeToValue(node.get("value")));
        }
        if (node.has("maxDepth")) {
            def.setMaxDepth(node.get("maxDepth").asInt());
        }

        // 处理 $or 和 $and
        if (node.has("$or")) {
            def.setOr(parseConditionList(node.get("$or"), mapper));
        }
        if (node.has("$and")) {
            def.setAnd(parseConditionList(node.get("$and"), mapper));
        }

        return def;
    }

    /**
     * 解析逻辑组（$or 或 $and）
     */
    private SliceRequestDef parseLogicalGroup(JsonNode arrayNode, boolean isOr, ObjectMapper mapper) {
        SliceRequestDef def = new SliceRequestDef();
        List<CondRequestDef> conditions = parseConditionList(arrayNode, mapper);

        if (isOr) {
            def.setOr(conditions);
        } else {
            def.setAnd(conditions);
        }

        return def;
    }

    /**
     * 解析条件列表
     */
    private List<CondRequestDef> parseConditionList(JsonNode arrayNode, ObjectMapper mapper) {
        List<CondRequestDef> result = new ArrayList<>();

        if (arrayNode == null || !arrayNode.isArray()) {
            return result;
        }

        for (JsonNode itemNode : arrayNode) {
            CondRequestDef cond = parseCondItem(itemNode, mapper);
            if (cond != null) {
                result.add(cond);
            }
        }

        return result;
    }

    /**
     * 解析单个条件项（用于嵌套条件）
     */
    private CondRequestDef parseCondItem(JsonNode node, ObjectMapper mapper) {
        List<String> fieldNames = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            fieldNames.add(it.next());
        }

        // 判断是否为简写格式
        if (fieldNames.size() == 1) {
            String key = fieldNames.get(0);

            if ("$or".equals(key)) {
                return CondRequestDef.or(parseConditionList(node.get("$or"), mapper));
            }
            if ("$and".equals(key)) {
                return CondRequestDef.and(parseConditionList(node.get("$and"), mapper));
            }

            if (!RESERVED_KEYS.contains(key)) {
                CondRequestDef def = new CondRequestDef();
                def.setField(key);
                def.setOp("=");
                def.setValue(nodeToValue(node.get(key)));
                return def;
            }
        }

        // 完整格式
        CondRequestDef def = new CondRequestDef();

        if (node.has("field")) {
            def.setField(node.get("field").asText());
        }
        if (node.has("op")) {
            def.setOp(node.get("op").asText());
        }
        if (node.has("value")) {
            def.setValue(nodeToValue(node.get("value")));
        }
        if (node.has("maxDepth")) {
            def.setMaxDepth(node.get("maxDepth").asInt());
        }

        if (node.has("$or")) {
            def.setOr(parseConditionList(node.get("$or"), mapper));
        }
        if (node.has("$and")) {
            def.setAnd(parseConditionList(node.get("$and"), mapper));
        }

        return def;
    }

    /**
     * 将 JsonNode 转换为 Java 对象
     */
    private Object nodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(nodeToValue(item));
            }
            return list;
        }
        if (node.isObject()) {
            // 返回为 Map
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(node, Map.class);
        }
        return node.asText();
    }
}
