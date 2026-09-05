package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.FrontendMeta;
import com.foggyframework.dataviewer.domain.FrontendMeta.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * V3 语义元数据 → frontend-meta v1 转换器
 * <p>
 * 将 SemanticServiceV3 输出的 Map 结构转换为面向前端的标准 FrontendMeta 结构。
 * 核心转换：fields 从 Object 映射转为有序数组、自动推导 memberLookup、新增 category/sortable/uiHints。
 */
public class FrontendMetaConverter {

    private static final String META_VERSION = "v1";
    private static final int DEFAULT_MEMBER_LIMIT = 20;

    /**
     * 将 V3 JSON 的 Map 结构转换为 FrontendMeta
     *
     * @param v3Data SemanticServiceV3 返回的 response.getData()
     * @return FrontendMeta 前端元数据契约
     */
    @SuppressWarnings("unchecked")
    public FrontendMeta convert(Map<String, Object> v3Data) {
        if (v3Data == null) {
            return null;
        }

        // 提取模型信息
        Map<String, Object> modelsMap = getMap(v3Data, "models");
        String modelName = null;
        String caption = null;
        String description = null;
        boolean hasAggregatable = false;

        if (modelsMap != null && !modelsMap.isEmpty()) {
            modelName = modelsMap.keySet().iterator().next();
            Map<String, Object> modelInfo = getMap(modelsMap, modelName);
            if (modelInfo != null) {
                caption = getString(modelInfo, "name");
                description = getFirstString(modelInfo, "purpose", "description", "desc", "describe");
            }
        }

        // 转换字段：Object 映射 → 有序数组
        Map<String, Object> fieldsMap = getMap(v3Data, "fields");
        List<FieldMeta> fields = new ArrayList<>();

        if (fieldsMap != null) {
            // 先收集所有字段名，用于 memberLookup 推导
            Set<String> allFieldNames = fieldsMap.keySet();

            for (Map.Entry<String, Object> entry : fieldsMap.entrySet()) {
                String fieldKey = entry.getKey();
                Map<String, Object> fieldData = (Map<String, Object>) entry.getValue();
                if (fieldData == null) continue;

                FieldMeta field = convertField(fieldKey, fieldData, allFieldNames, modelName);
                fields.add(field);

                if (Boolean.TRUE.equals(field.getAggregatable())) {
                    hasAggregatable = true;
                }
            }
        }

        return FrontendMeta.builder()
                .metaVersion(META_VERSION)
                .model(modelName)
                .caption(caption)
                .description(description)
                .fields(fields)
                .defaults(buildDefaults(fields))
                .capabilities(CapabilitiesMeta.builder()
                        .pageable(true)
                        .sortable(true)
                        .filterable(true)
                        .aggregatable(hasAggregatable)
                        .build())
                .build();
    }

    /**
     * 转换单个字段
     */
    @SuppressWarnings("unchecked")
    private FieldMeta convertField(String fieldKey, Map<String, Object> fieldData,
                                   Set<String> allFieldNames, String modelName) {
        String name = getString(fieldData, "fieldName", fieldKey);
        String title = getString(fieldData, "name");
        String description = getFirstString(fieldData, "description", "desc", "descrip", "describe", "comment", "remark", "purpose");
        String type = getString(fieldData, "type");
        Map<String, Object> groupData = getMap(fieldData, "group");
        String groupKey = groupData != null
                ? getFirstString(groupData, "key", "id", "code", "name")
                : getFirstString(fieldData, "groupKey", "groupCode", "groupId", "group");
        String groupTitle = groupData != null
                ? getFirstString(groupData, "title", "label", "caption", "name")
                : getFirstString(fieldData, "groupTitle", "groupLabel", "groupCaption", "groupName", "group");
        Integer groupOrder = groupData != null
                ? getInteger(groupData, "order", "sort", "index")
                : getInteger(fieldData, "groupOrder", "groupSort", "groupIndex");
        if (groupKey == null) {
            groupKey = groupTitle;
        }
        if (groupTitle == null) {
            groupTitle = groupKey;
        }
        String filterType = getString(fieldData, "filterType");
        Boolean filterable = getBoolean(fieldData, "filterable");
        Boolean measure = getBoolean(fieldData, "measure");
        Boolean aggregatable = getBoolean(fieldData, "aggregatable");
        String aggregation = getString(fieldData, "aggregation");
        String sourceColumn = getString(fieldData, "sourceColumn");
        BigDecimal semanticScaleFactor = getBigDecimal(fieldData, "semanticScaleFactor");
        String semanticUnit = getString(fieldData, "semanticUnit");
        String semanticUnitLabel = getString(fieldData, "semanticUnitLabel");
        String dictId = getString(fieldData, "dictId");
        Boolean calculated = getBoolean(fieldData, "calculated");
        Boolean hierarchical = getBoolean(fieldData, "hierarchical");
        List<String> hierarchyOps = getStringList(fieldData, "hierarchyOps");
        String semanticRole = getString(fieldData, "semanticRole");

        // 推导 category
        String category = deriveCategory(name, measure, calculated, semanticRole);

        // 优先服从语义元数据的显式能力；旧元数据仍按原规则推导。
        Boolean declaredSortable = getBoolean(fieldData, "sortable");
        boolean sortable = declaredSortable != null
                ? declaredSortable
                : !Boolean.TRUE.equals(measure);

        // 推导 dictMode
        String dictMode = dictId != null ? "static" : null;

        // 读取内联字典选项
        List<FrontendMeta.DictItemMeta> dictItems = convertDictItems(fieldData);

        // 推导 memberLookup（仅 $caption 字段自动开启）
        MemberLookupMeta memberLookup = deriveMemberLookup(name, filterType, allFieldNames, hierarchical);

        // 推导 uiHints
        UiHintsMeta uiHints = deriveUiHints(category, type);
        Map<String, Object> extData = convertViewerExtData(fieldData);

        return FieldMeta.builder()
                .name(name)
                .title(title)
                .description(description)
                .type(type)
                .groupKey(groupKey)
                .groupTitle(groupTitle)
                .groupOrder(groupOrder)
                .category(category)
                .filterType(filterType)
                .filterable(filterable)
                .sortable(sortable)
                .measure(measure)
                .aggregatable(aggregatable)
                .aggregation(aggregation)
                .sourceColumn(sourceColumn)
                .semanticScaleFactor(semanticScaleFactor)
                .semanticUnit(semanticUnit)
                .semanticUnitLabel(semanticUnitLabel)
                .dictId(dictId)
                .dictMode(dictMode)
                .dictItems(dictItems)
                .calculated(calculated)
                .hierarchical(hierarchical)
                .hierarchyOps(hierarchyOps)
                .memberLookup(memberLookup)
                .uiHints(uiHints)
                .extData(extData)
                .build();
    }

    /**
     * 根据字段命名和类型推导 category
     */
    private String deriveCategory(String name, Boolean measure, Boolean calculated,
                                  String semanticRole) {
        if ("dictionary-caption".equals(semanticRole)) {
            return "dictionary-caption";
        }
        if (Boolean.TRUE.equals(calculated)) {
            return "calculated";
        }
        if (Boolean.TRUE.equals(measure)) {
            return "measure";
        }
        if (name.contains("$")) {
            if (name.endsWith("$id")) {
                return "dimension-id";
            }
            if (name.endsWith("$caption")) {
                return "dimension-caption";
            }
            return "dimension-property";
        }
        return "attribute";
    }

    /**
     * $caption 字段自动推导 memberLookup
     * <p>
     * 规则：
     * 1. 字段名以 $caption 结尾，且同基名存在 $id → 自动开启
     * 2. 字段名以 $id 结尾 → 不自动开启（过滤器挂在 $caption 上）
     * 3. 无法推导 → 不开启
     */
    private MemberLookupMeta deriveMemberLookup(String name, String filterType,
                                                 Set<String> allFieldNames,
                                                 Boolean hierarchical) {
        if (!"dimension".equals(filterType)) {
            return null;
        }
        if (!name.endsWith("$caption")) {
            return null;
        }

        String baseName = name.substring(0, name.lastIndexOf("$caption"));
        String idFieldName = baseName + "$id";

        if (!allFieldNames.contains(idFieldName)) {
            return null;
        }

        return MemberLookupMeta.builder()
                .enabled(true)
                .selectionFieldName(idFieldName)
                .displayFieldName(name)
                .searchable(true)
                .pageable(true)
                .defaultLimit(DEFAULT_MEMBER_LIMIT)
                .build();
    }

    /**
     * 推导 UI 提示
     */
    private UiHintsMeta deriveUiHints(String category, String type) {
        boolean visible = !"dimension-id".equals(category)
                && !"dictionary-caption".equals(category);

        UiHintsMeta.UiHintsMetaBuilder builder = UiHintsMeta.builder()
                .visible(visible)
                .nullable(true);

        // 日期时间类型添加 format 提示
        if ("DATETIME".equals(type)) {
            builder.format("yyyy-MM-dd HH:mm:ss");
        } else if ("DAY".equals(type)) {
            builder.format("yyyy-MM-dd");
        }

        return builder.build();
    }

    /**
     * 构建默认配置
     */
    private DefaultsMeta buildDefaults(List<FieldMeta> fields) {
        List<String> visibleColumns = new ArrayList<>();
        List<String> searchFields = new ArrayList<>();

        for (FieldMeta field : fields) {
            if (field.getUiHints() != null && Boolean.TRUE.equals(field.getUiHints().getVisible())) {
                visibleColumns.add(field.getName());
            }
            // 文本属性字段纳入默认搜索
            if ("attribute".equals(field.getCategory())
                    && "text".equals(field.getFilterType())
                    && Boolean.TRUE.equals(field.getFilterable())) {
                searchFields.add(field.getName());
            }
        }

        return DefaultsMeta.builder()
                .visibleColumns(visibleColumns)
                .searchFields(searchFields.isEmpty() ? null : searchFields)
                .pageSize(50)
                .build();
    }

    /**
     * 转换内联字典选项（从 V3 元数据的 dictItems 数组）
     */
    @SuppressWarnings("unchecked")
    private List<FrontendMeta.DictItemMeta> convertDictItems(Map<String, Object> fieldData) {
        Object raw = fieldData.get("dictItems");
        if (!(raw instanceof List)) {
            return null;
        }

        List<?> rawList = (List<?>) raw;
        if (rawList.isEmpty()) {
            return null;
        }

        List<FrontendMeta.DictItemMeta> items = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) item;
                Object value = m.get("value");
                Object label = m.get("label");
                if (value != null && label != null) {
                    items.add(new FrontendMeta.DictItemMeta(value, String.valueOf(label)));
                }
            }
        }
        return items.isEmpty() ? null : items;
    }

    /**
     * frontend-meta intentionally exposes only extData.viewer and only its
     * documented scalar properties. Runtime/private extData keys stay hidden.
     */
    private Map<String, Object> convertViewerExtData(Map<String, Object> fieldData) {
        Map<String, Object> rawExtData = getMap(fieldData, "extData");
        Map<String, Object> rawViewer = rawExtData == null ? null : getMap(rawExtData, "viewer");
        if (rawViewer == null) {
            return null;
        }

        Map<String, Object> viewer = new LinkedHashMap<>();
        for (String key : List.of("format", "rawUnit", "displayUnit", "scaleFactor", "precision")) {
            Object value = rawViewer.get(key);
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                viewer.put(key, value);
            }
        }
        return viewer.isEmpty() ? null : Map.of("viewer", viewer);
    }

    // ── 工具方法 ──

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        String val = getString(map, key);
        return val != null ? val : defaultVal;
    }

    private String getFirstString(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            String val = getString(map, key);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val instanceof Number || val instanceof String) {
            String text = String.valueOf(val);
            if (!text.isBlank()) {
                return new BigDecimal(text);
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object val = map.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            if (val instanceof String) {
                String text = ((String) val).trim();
                if (!text.isEmpty()) {
                    try {
                        return Integer.parseInt(text);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            return (List<String>) val;
        }
        return null;
    }
}
