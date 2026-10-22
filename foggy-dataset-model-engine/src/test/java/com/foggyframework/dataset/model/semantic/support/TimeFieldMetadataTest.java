package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.dataset.model.def.TimeMetadataDefinitionValidator;
import com.foggyframework.dataset.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.model.impl.property.DbPropertyImpl;
import com.foggyframework.dataset.model.spi.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimeFieldMetadataTest {
    private DbProperty property(String name, DbColumnType type, String role, String use) {
        DbProperty p = mock(DbProperty.class);
        DbColumn column = mock(DbColumn.class);
        when(p.getName()).thenReturn(name);
        when(p.getPropertyDbColumn()).thenReturn(column);
        when(column.getType()).thenReturn(type);
        when(p.getTimeRole()).thenReturn(role);
        when(p.getRecommendedUse()).thenReturn(use);
        return p;
    }

    @Test void definitionConversionAndApplyRetainExplicitAnnotations() {
        var def = FsscriptConversionService.getSharedInstance().convert(Map.of("name","loadedAt", "column","loaded_at",
                "type","DATETIME", "timeRole","system_time", "recommendedUse","ingestion only"), DbPropertyDef.class);
        var runtime = new DbPropertyImpl();
        def.apply(runtime);
        assertEquals("system_time",runtime.getTimeRole());
        assertEquals("ingestion only",runtime.getRecommendedUse());
    }

    @Test void explicitRolesAndPurposeAreIndependentAndOpen() {
        var time = TimeFieldMetadata.property(property("createdAt",DbColumnType.DATETIME,"due_date","age | overdue\nby invoice"));
        assertEquals("due_date",time.timeRole());
        assertEquals("age | overdue\nby invoice",time.recommendedUse());
        assertEquals("system_time",TimeFieldMetadata.property(property("loadedAt",DbColumnType.DATETIME,"system_time",null)).timeRole());
        var ordinary = TimeFieldMetadata.property(property("status",DbColumnType.STRING,null,"filter only"));
        assertNull(ordinary.timeRole());
        assertEquals("filter only",ordinary.recommendedUse());
        assertFalse(ordinary.timeField());
    }

    @Test void blanksUseLegacyInferenceAndInferenceIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("system_time",TimeFieldMetadata.property(property("WRITE_TIME",DbColumnType.DATETIME," ",null)).timeRole());
            assertEquals("business_date",TimeFieldMetadata.property(property("loadedAt",DbColumnType.DAY,null," ")).timeRole());
        } finally { Locale.setDefault(previous); }
    }

    @Test void onlyBusinessDimensionsHaveLegacyBusinessPurpose() {
        DbDimension dim = mock(DbDimension.class);
        when(dim.getName()).thenReturn("auditTime");
        when(dim.getTimeRole()).thenReturn("system_time");
        assertNull(TimeFieldMetadata.dimension(dim).recommendedUse());
        when(dim.getTimeRole()).thenReturn(null);
        assertEquals("business_date",TimeFieldMetadata.dimension(dim).timeRole());
        assertEquals(TimeFieldMetadata.BUSINESS_AXIS_USE,TimeFieldMetadata.dimension(dim).recommendedUse());
        assertNull(TimeFieldMetadata.property(property("year",DbColumnType.INTEGER,null,null)).timeRole());
    }

    @Test void rawValidationRejectsCoercibleTypesAndReportsNestedFieldPath() {
        for (Object value : List.of(123, true, List.of("business_date"), Map.of("role","business_date"))) {
            for (String key : List.of("timeRole","recommendedUse")) {
                Map<String,Object> field = Map.of("name","createdAt",key,value);
                var model = Map.of("name","Probe","dimensions",List.of(Map.of("name","audit","properties",List.of(field))));
                var error = assertThrows(IllegalArgumentException.class,()->TimeMetadataDefinitionValidator.validate(model));
                assertTrue(error.getMessage().contains("Probe.dimensions[0](audit).properties[0](createdAt)."+key),error.getMessage());
                assertTrue(error.getMessage().contains("must be a string or null"));
            }
        }
        assertDoesNotThrow(()->TimeMetadataDefinitionValidator.validate(Map.of("name","Probe","properties",List.of(
                Map.of("name","date","timeRole","custom_delivery_date","recommendedUse","any explanatory text")))));
    }
}
