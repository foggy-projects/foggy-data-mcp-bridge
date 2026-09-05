package com.foggyframework.dataset.model.engine.dictionary;

import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.model.def.dict.DbDictDef;
import com.foggyframework.dataset.model.def.dict.DbDictItemDef;
import com.foggyframework.dataset.model.spi.DbColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class DictionaryBindingTest {

    private static final ObjectTransFormatter<Integer> INTEGER_FORMATTER = new ObjectTransFormatter<>() {
        @Override
        public Integer format(Object object) {
            return object == null ? null : Integer.valueOf(String.valueOf(object));
        }

        @Override
        public Class<Integer> type() {
            return Integer.class;
        }
    };

    @Test
    void normalizesNumericCodesAndPreservesNullOrUnknownCodes() {
        DbColumn source = mock(DbColumn.class);
        doReturn(INTEGER_FORMATTER).when(source).getFormatter(true);
        DbDictDef dictionary = new DbDictDef(
                "express_order_status",
                "运单状态",
                null,
                List.of(new DbDictItemDef("100", "待揽收"),
                        new DbDictItemDef(1200L, "已签收")),
                null,
                null);

        DictionaryBinding binding = DictionaryBinding.bind(dictionary, source);

        assertEquals("待揽收", binding.toLabel(100));
        assertEquals("已签收", binding.toLabel(1200L));
        assertEquals(100, binding.toValue("待揽收"));
        assertEquals(100, binding.toCodeOrLabel("100"));
        assertEquals(100, binding.toCodeOrLabel("待揽收"));
        assertEquals(999, binding.toCodeOrLabel("999"));
        assertNull(binding.toLabel(null));
        assertNull(binding.toValue(null));
        assertEquals("999", binding.toLabel(999));
    }

    @Test
    void rejectsUnknownAndAmbiguousLabels() {
        DbColumn source = mock(DbColumn.class);
        doReturn(INTEGER_FORMATTER).when(source).getFormatter(true);
        DbDictDef dictionary = new DbDictDef(
                "duplicate_labels",
                "重复标签",
                null,
                List.of(new DbDictItemDef(1, "相同"), new DbDictItemDef(2, "相同")),
                null,
                null);
        DictionaryBinding binding = DictionaryBinding.bind(dictionary, source);

        RuntimeException missing = assertThrows(RuntimeException.class,
                () -> binding.toValue("不存在"));
        assertTrue(missing.getMessage().contains("DICT_LABEL_NOT_FOUND"));

        RuntimeException ambiguous = assertThrows(RuntimeException.class,
                () -> binding.toValue("相同"));
        assertTrue(ambiguous.getMessage().contains("DICT_LABEL_AMBIGUOUS"));
        RuntimeException ambiguousRawField = assertThrows(RuntimeException.class,
                () -> binding.toCodeOrLabel("相同"));
        assertTrue(ambiguousRawField.getMessage().contains("DICT_LABEL_AMBIGUOUS"));
    }
}
