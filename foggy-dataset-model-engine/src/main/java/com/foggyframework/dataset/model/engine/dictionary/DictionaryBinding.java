package com.foggyframework.dataset.model.engine.dictionary;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.model.def.dict.DbDictDef;
import com.foggyframework.dataset.model.def.dict.DbDictItemDef;
import com.foggyframework.dataset.model.spi.DbColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, model-bound view of a registered static dictionary.
 *
 * <p>The binding normalizes dictionary values with the source column formatter so
 * JDBC values and fsscript literals use the same Java key type. It intentionally
 * performs both directions in Java; SQL continues to use the source column and
 * its raw value.</p>
 */
public final class DictionaryBinding {

    private final String dictId;
    private final Map<Object, String> valueToLabel;
    private final Map<String, List<Object>> labelToValues;
    private final ObjectTransFormatter<?> rawValueFormatter;
    private final ObjectTransFormatter<String> valueToLabelFormatter = new ObjectTransFormatter<>() {
        @Override
        public String format(Object object) {
            return toLabel(object);
        }

        @Override
        public Class<String> type() {
            return String.class;
        }
    };
    private final ObjectTransFormatter<Object> labelToValueFormatter = new ObjectTransFormatter<>() {
        @Override
        public Object format(Object object) {
            return toValue(object);
        }

        @Override
        public Class<?> type() {
            return rawValueFormatter.type();
        }
    };
    private final ObjectTransFormatter<Object> codeOrLabelFormatter = new ObjectTransFormatter<>() {
        @Override
        public Object format(Object object) {
            return toCodeOrLabel(object);
        }

        @Override
        public Class<?> type() {
            return rawValueFormatter.type();
        }
    };

    private DictionaryBinding(String dictId,
                              Map<Object, String> valueToLabel,
                              Map<String, List<Object>> labelToValues,
                              ObjectTransFormatter<?> rawValueFormatter) {
        this.dictId = dictId;
        this.valueToLabel = valueToLabel;
        this.labelToValues = labelToValues;
        this.rawValueFormatter = rawValueFormatter;
    }

    public static DictionaryBinding bind(DbDictDef dictionary, DbColumn sourceColumn) {
        if (dictionary == null) {
            throw RX.throwAUserTip("DICT_NOT_FOUND: 字典定义不存在");
        }
        if (sourceColumn == null) {
            throw RX.throwAUserTip("DICT_SOURCE_COLUMN_MISSING: 字典字段缺少源列");
        }

        ObjectTransFormatter<?> rawFormatter = sourceColumn.getFormatter(true);
        Map<Object, String> byValue = new LinkedHashMap<>();
        Map<String, List<Object>> byLabel = new LinkedHashMap<>();
        if (dictionary.getItems() != null) {
            for (DbDictItemDef item : dictionary.getItems()) {
                if (item == null || item.getValue() == null || item.getLabel() == null) {
                    continue;
                }
                Object normalizedValue;
                try {
                    normalizedValue = rawFormatter.format(item.getValue());
                } catch (RuntimeException ex) {
                    throw RX.throwAUserTip(
                            "DICT_VALUE_TYPE_INVALID: 字典 " + dictionary.getId()
                                    + " 的值 " + item.getValue() + " 无法转换为字段类型",
                            "字典值类型与字段类型不一致",
                            null,
                            ex);
                }
                if (normalizedValue == null) {
                    continue;
                }
                byValue.put(normalizedValue, item.getLabel());
                byLabel.computeIfAbsent(item.getLabel(), ignored -> new ArrayList<>())
                        .add(normalizedValue);
            }
        }

        Map<String, List<Object>> immutableByLabel = new LinkedHashMap<>();
        byLabel.forEach((label, values) -> immutableByLabel.put(label, List.copyOf(values)));
        return new DictionaryBinding(
                dictionary.getId(),
                Collections.unmodifiableMap(byValue),
                Collections.unmodifiableMap(immutableByLabel),
                rawFormatter);
    }

    public String getDictId() {
        return dictId;
    }

    public Map<Object, String> getValueToLabel() {
        return valueToLabel;
    }

    public String toLabel(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Object normalizedValue = rawValueFormatter.format(rawValue);
        String label = valueToLabel.get(normalizedValue);
        // A stale/unknown code must remain observable instead of disappearing.
        return label != null ? label : String.valueOf(rawValue);
    }

    public Object toValue(Object labelValue) {
        if (labelValue == null) {
            return null;
        }
        String label = String.valueOf(labelValue);
        List<Object> values = labelToValues.get(label);
        if (values == null || values.isEmpty()) {
            throw RX.throwAUserTip("DICT_LABEL_NOT_FOUND: 字典 " + dictId
                    + " 中不存在标签: " + label);
        }
        if (values.size() > 1) {
            throw RX.throwAUserTip("DICT_LABEL_AMBIGUOUS: 字典 " + dictId
                    + " 中标签对应多个值: " + label);
        }
        return values.get(0);
    }

    /**
     * Preserve legacy code filtering while also accepting an exact registered
     * label on the raw dictionary field. A configured code wins if a string is
     * simultaneously a code and a label, keeping existing queries stable.
     */
    public Object toCodeOrLabel(Object input) {
        if (input == null) {
            return null;
        }
        Object normalizedCode;
        try {
            normalizedCode = rawValueFormatter.format(input);
        } catch (RuntimeException codeFormatError) {
            return toValue(input);
        }
        if (valueToLabel.containsKey(normalizedCode)) {
            return normalizedCode;
        }
        List<Object> labelValues = labelToValues.get(String.valueOf(input));
        if (labelValues == null || labelValues.isEmpty()) {
            // Unknown codes remain valid filter inputs for backward compatibility.
            return normalizedCode;
        }
        if (labelValues.size() > 1) {
            throw RX.throwAUserTip("DICT_LABEL_AMBIGUOUS: 字典 " + dictId
                    + " 中标签对应多个值: " + input);
        }
        return labelValues.get(0);
    }

    public ObjectTransFormatter<String> getValueToLabelFormatter() {
        return valueToLabelFormatter;
    }

    public ObjectTransFormatter<Object> getLabelToValueFormatter() {
        return labelToValueFormatter;
    }

    public ObjectTransFormatter<Object> getCodeOrLabelFormatter() {
        return codeOrLabelFormatter;
    }
}
