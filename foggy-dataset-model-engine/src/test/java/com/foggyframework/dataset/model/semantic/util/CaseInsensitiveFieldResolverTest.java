package com.foggyframework.dataset.model.semantic.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CaseInsensitiveFieldResolver}.
 */
@DisplayName("CaseInsensitiveFieldResolver")
class CaseInsensitiveFieldResolverTest {

    @Test
    @DisplayName("Exact match returns the same field name")
    void testExactMatch() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("arOverdueAmount", "salesAmount"));
        assertEquals("arOverdueAmount", resolver.resolve("arOverdueAmount"));
        assertEquals("salesAmount", resolver.resolve("salesAmount"));
    }

    @Test
    @DisplayName("Case variant resolves to canonical name")
    void testCaseVariantResolves() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("arOverdueAmount", "salesAmount"));
        assertEquals("arOverdueAmount", resolver.resolve("aroverdueamount"));
        assertEquals("arOverdueAmount", resolver.resolve("AROVERDUEAMOUNT"));
        assertEquals("arOverdueAmount", resolver.resolve("ArOverdueAmount"));
        assertEquals("salesAmount", resolver.resolve("SALESAMOUNT"));
        assertEquals("salesAmount", resolver.resolve("salesamount"));
    }

    @Test
    @DisplayName("No match returns input unchanged")
    void testNoMatchReturnsUnchanged() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("arOverdueAmount"));
        assertEquals("nonExistentField", resolver.resolve("nonExistentField"));
    }

    @Test
    @DisplayName("resolveOrNull returns null on no match")
    void testResolveOrNullReturnsNull() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("arOverdueAmount"));
        assertNull(resolver.resolveOrNull("nonExistentField"));
    }

    @Test
    @DisplayName("snake_case is NOT resolved to camelCase (different characters)")
    void testSnakeCaseNotMatched() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("arOverdueAmount"));
        // snake_case has underscores → not a case-only variant
        assertEquals("ar_overdue_amount", resolver.resolve("ar_overdue_amount"));
    }

    @Test
    @DisplayName("Dimension suffix is preserved in resolution")
    void testDimensionSuffixPreserved() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("orderStatus$caption", "orderStatus$id"));
        assertEquals("orderStatus$caption", resolver.resolve("orderstatus$caption"));
        assertEquals("orderStatus$id", resolver.resolve("ORDERSTATUS$ID"));
    }

    @Test
    @DisplayName("Ambiguous fields (differ only by case) throw exception")
    void testAmbiguousFieldsFailClosed() {
        CaseInsensitiveFieldResolver resolver =
                new CaseInsensitiveFieldResolver(Set.of("amount", "Amount"));

        // Exact matches still work
        assertEquals("amount", resolver.resolve("amount"));
        assertEquals("Amount", resolver.resolve("Amount"));

        // Case variant matching 2+ candidates fails
        var ex = assertThrows(
                CaseInsensitiveFieldResolver.CaseInsensitiveFieldAmbiguousException.class,
                () -> resolver.resolve("AMOUNT")
        );
        assertEquals("CASE_INSENSITIVE_FIELD_AMBIGUOUS", ex.getErrorCode());
        assertEquals("AMOUNT", ex.getField());
        assertTrue(ex.getCandidates().contains("amount"));
        assertTrue(ex.getCandidates().contains("Amount"));
    }

    @Test
    @DisplayName("Feature flag is enabled by default")
    void testFeatureFlagEnabledByDefault() {
        // Clear any test-set system property
        String previous = System.getProperty("foggy.dataset.case-insensitive-field-resolve");
        System.clearProperty("foggy.dataset.case-insensitive-field-resolve");
        try {
            assertTrue(CaseInsensitiveFieldResolver.isEnabled());
        } finally {
            if (previous != null) {
                System.setProperty("foggy.dataset.case-insensitive-field-resolve", previous);
            }
        }
    }

    @Test
    @DisplayName("Feature flag can be disabled via system property")
    void testFeatureFlagDisabledViaSysProp() {
        String previous = System.getProperty("foggy.dataset.case-insensitive-field-resolve");
        System.setProperty("foggy.dataset.case-insensitive-field-resolve", "false");
        try {
            assertFalse(CaseInsensitiveFieldResolver.isEnabled());
        } finally {
            if (previous == null) {
                System.clearProperty("foggy.dataset.case-insensitive-field-resolve");
            } else {
                System.setProperty("foggy.dataset.case-insensitive-field-resolve", previous);
            }
        }
    }
}
