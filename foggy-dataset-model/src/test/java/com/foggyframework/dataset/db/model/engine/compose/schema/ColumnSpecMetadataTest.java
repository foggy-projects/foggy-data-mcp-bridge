package com.foggyframework.dataset.db.model.engine.compose.schema;

import com.foggyframework.dataset.db.model.engine.compose.relation.ReferencePolicy;
import com.foggyframework.dataset.db.model.engine.compose.relation.SemanticKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7a POC · ColumnSpec semantic metadata fields.
 *
 * <p>Validates:
 * <ul>
 *   <li>New metadata fields default to null.</li>
 *   <li>Builder accessors work correctly.</li>
 *   <li>{@code equals()} / {@code hashCode()} are unchanged when metadata differs.</li>
 *   <li>OutputSchema still rejects/handles duplicates per G10 policy.</li>
 * </ul></p>
 */
@DisplayName("ColumnSpecMetadataTest · S7a POC")
class ColumnSpecMetadataTest {

    @Test
    @DisplayName("new metadata fields default to null")
    void metadataDefaultsToNull() {
        ColumnSpec cs = ColumnSpec.of("salesAmount", "salesAmount");
        assertNull(cs.semanticKind());
        assertNull(cs.valueMeaning());
        assertNull(cs.lineage());
        assertNull(cs.referencePolicy());
    }

    @Test
    @DisplayName("builder sets all metadata fields")
    void builderSetsMetadata() {
        ColumnSpec cs = ColumnSpec.builder()
                .name("salesAmount__ratio")
                .expression("salesAmount__ratio")
                .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                .valueMeaning("current relative to prior salesAmount")
                .lineage(Set.of("salesAmount"))
                .referencePolicy(Set.of(ReferencePolicy.READABLE, ReferencePolicy.ORDERABLE))
                .build();

        assertEquals(SemanticKind.TIME_WINDOW_DERIVED, cs.semanticKind());
        assertEquals("current relative to prior salesAmount", cs.valueMeaning());
        assertEquals(Set.of("salesAmount"), cs.lineage());
        assertTrue(cs.referencePolicy().contains(ReferencePolicy.READABLE));
        assertTrue(cs.referencePolicy().contains(ReferencePolicy.ORDERABLE));
        assertFalse(cs.referencePolicy().contains(ReferencePolicy.AGGREGATABLE));
    }

    @Test
    @DisplayName("lineage and referencePolicy are unmodifiable")
    void metadataSetsAreUnmodifiable() {
        ColumnSpec cs = ColumnSpec.builder()
                .name("x").expression("x")
                .lineage(Set.of("a"))
                .referencePolicy(Set.of("readable"))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> cs.lineage().add("b"));
        assertThrows(UnsupportedOperationException.class, () -> cs.referencePolicy().add("z"));
    }

    @Test
    @DisplayName("equals/hashCode unchanged by metadata — G10 zero-behavior-change")
    void equalsIgnoresMetadata() {
        ColumnSpec base = ColumnSpec.of("col", "col");
        ColumnSpec withMeta = ColumnSpec.builder()
                .name("col").expression("col")
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .valueMeaning("total sales")
                .lineage(Set.of("salesAmount"))
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();

        assertEquals(base, withMeta, "equals must ignore metadata fields");
        assertEquals(base.hashCode(), withMeta.hashCode(),
                "hashCode must ignore metadata fields");
    }

    @Test
    @DisplayName("two columns with different metadata are equal if core fields match")
    void differentMetadataStillEqual() {
        ColumnSpec a = ColumnSpec.builder()
                .name("x").expression("x")
                .semanticKind(SemanticKind.BASE_FIELD)
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        ColumnSpec b = ColumnSpec.builder()
                .name("x").expression("x")
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();

        assertEquals(a, b);
    }

    @Test
    @DisplayName("toString includes metadata fields")
    void toStringIncludesMetadata() {
        ColumnSpec cs = ColumnSpec.builder()
                .name("ratio").expression("ratio")
                .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                .build();
        String s = cs.toString();
        assertTrue(s.contains("semanticKind=time_window_derived"));
    }

    @Test
    @DisplayName("OutputSchema accepts columns with metadata per existing G10 policy")
    void outputSchemaWithMetadata() {
        var cs1 = ColumnSpec.builder()
                .name("dim1").expression("dim1")
                .semanticKind(SemanticKind.BASE_FIELD)
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        var cs2 = ColumnSpec.builder()
                .name("measure1").expression("measure1")
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();

        OutputSchema schema = OutputSchema.of(java.util.List.of(cs1, cs2));
        assertEquals(2, schema.size());
        assertNotNull(schema.get("dim1"));
        assertEquals(SemanticKind.BASE_FIELD, schema.get("dim1").semanticKind());
    }
}
