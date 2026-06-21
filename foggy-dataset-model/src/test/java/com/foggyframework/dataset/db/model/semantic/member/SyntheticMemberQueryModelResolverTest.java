package com.foggyframework.dataset.db.model.semantic.member;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Synthetic member-QM 解析测试")
class SyntheticMemberQueryModelResolverTest extends EcommerceTestSupport {

    private final SyntheticMemberQueryModelResolver resolver = new SyntheticMemberQueryModelResolver();

    @Test
    @DisplayName("普通维度可解析为唯一 synthetic member-QM")
    void resolveRootDimensionSchema() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");

        SyntheticMemberQueryModelDescriptor descriptor = resolver.resolve(queryModelLoader,
                "FactSalesQueryModel", "product$brand", " ");

        assertEquals("", descriptor.namespace());
        assertEquals("FactSalesQueryModel", descriptor.sourceModelName());
        assertEquals("FactSalesQueryModel#product", descriptor.syntheticModelName());
        assertEquals("product", descriptor.dimensionFieldBase());
        assertEquals("product", descriptor.matchedNodePath());
        assertEquals("|FactSalesQueryModel|product", descriptor.cacheKey());

        SyntheticMemberQueryModelSchema schema = descriptor.schema();
        assertFalse(schema.hierarchySupported());
        assertFalse(schema.fields().isEmpty());
        assertField(schema, "id", "product$id", SyntheticMemberFieldKind.ID, false, false);
        assertField(schema, "caption", "product$caption", SyntheticMemberFieldKind.CAPTION, false, false);
        assertField(schema, "productId", "product$productId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "categoryId", "product$categoryId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "categoryName", "product$categoryName", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "subCategoryId", "product$subCategoryId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "subCategoryName", "product$subCategoryName", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "brand", "product$brand", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "unitPrice", "product$unitPrice", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "unitCost", "product$unitCost", SyntheticMemberFieldKind.PROPERTY, false, false);
    }

    @Test
    @DisplayName("内部权限字段未声明为属性时仍进入 reserved schema")
    void resolveHiddenPermissionFieldSchema() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesHiddenMemberPermQueryModel");

        SyntheticMemberQueryModelDescriptor descriptor = resolver.resolve(queryModel,
                "product$brand", null);

        SyntheticMemberQueryModelSchema schema = descriptor.schema();
        assertField(schema, "status", "product$status", SyntheticMemberFieldKind.PROPERTY, true, false);
    }

    @Test
    @DisplayName("嵌套维度可按根维度子树展开")
    void resolveNestedDimensionSchema() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesNestedDimQueryModel");

        SyntheticMemberQueryModelDescriptor descriptor = resolver.resolve(queryModel,
                "categoryGroup$groupType", null);

        assertEquals("FactSalesNestedDimQueryModel#product", descriptor.syntheticModelName());
        assertEquals("product", descriptor.dimensionFieldBase());
        assertEquals("productCategory$categoryGroup", descriptor.matchedNodePath());

        SyntheticMemberQueryModelSchema schema = descriptor.schema();
        assertFalse(schema.hierarchySupported());
        assertFalse(schema.fields().isEmpty());
        assertNotNull(schema.nodeIndex().get(""));
        assertNotNull(schema.nodeIndex().get("product"));
        assertNotNull(schema.nodeIndex().get("productCategory"));
        assertNotNull(schema.nodeIndex().get("productCategory$categoryGroup"));

        assertField(schema, "id", "product$id", SyntheticMemberFieldKind.ID, false, false);
        assertField(schema, "caption", "product$caption", SyntheticMemberFieldKind.CAPTION, false, false);
        assertField(schema, "productId", "product$productId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "brand", "product$brand", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "unitPrice", "product$unitPrice", SyntheticMemberFieldKind.PROPERTY, false, false);

        assertField(schema, "productCategory$id", "productCategory$id", SyntheticMemberFieldKind.ID, false, false);
        assertField(schema, "productCategory$caption", "productCategory$caption", SyntheticMemberFieldKind.CAPTION, false, false);
        assertField(schema, "productCategory$categoryId", "productCategory$categoryId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "productCategory$categoryLevel", "productCategory$categoryLevel", SyntheticMemberFieldKind.PROPERTY, false, false);

        assertField(schema, "productCategory$categoryGroup$id", "productCategory$categoryGroup$id", SyntheticMemberFieldKind.ID, false, false);
        assertField(schema, "productCategory$categoryGroup$caption", "productCategory$categoryGroup$caption", SyntheticMemberFieldKind.CAPTION, false, false);
        assertField(schema, "productCategory$categoryGroup$groupId", "productCategory$categoryGroup$groupId", SyntheticMemberFieldKind.PROPERTY, false, false);
        assertField(schema, "productCategory$categoryGroup$groupType", "productCategory$categoryGroup$groupType", SyntheticMemberFieldKind.PROPERTY, false, false);
    }

    @Test
    @DisplayName("父子维保留字段可与 TM 属性共存")
    void resolveParentChildDimensionSchema() {
        JdbcQueryModel queryModel = getQueryModel("FactTeamSalesQueryModel");

        SyntheticMemberQueryModelDescriptor descriptor = resolver.resolve(queryModel,
                "team$teamLevel", "  qa ");

        assertEquals("qa", descriptor.namespace());
        assertEquals("FactTeamSalesQueryModel#team", descriptor.syntheticModelName());
        assertEquals("team", descriptor.dimensionFieldBase());
        assertTrue(descriptor.hierarchyPathNode());

        SyntheticMemberQueryModelSchema schema = descriptor.schema();
        assertTrue(schema.hierarchySupported());
        assertFalse(schema.fields().isEmpty());
        assertField(schema, "id", "team$id", SyntheticMemberFieldKind.ID, false, true);
        assertField(schema, "caption", "team$caption", SyntheticMemberFieldKind.CAPTION, false, true);
        assertField(schema, "teamId", "team$teamId", SyntheticMemberFieldKind.PROPERTY, false, true);
        assertField(schema, "teamName", "team$teamName", SyntheticMemberFieldKind.PROPERTY, false, true);
        assertField(schema, "parentId", "team$parentId", SyntheticMemberFieldKind.PARENT_ID, true, true);
        assertField(schema, "teamLevel", "team$teamLevel", SyntheticMemberFieldKind.PROPERTY, false, true);
        assertField(schema, "managerName", "team$managerName", SyntheticMemberFieldKind.PROPERTY, false, true);
        assertField(schema, "status", "team$status", SyntheticMemberFieldKind.PROPERTY, false, true);
        assertField(schema, "depth", "team$depth", SyntheticMemberFieldKind.DEPTH, true, true);
        assertField(schema, "hasChildren", "team$hasChildren", SyntheticMemberFieldKind.HAS_CHILDREN, true, true);
    }

    @Test
    @DisplayName("namespace 与 cacheKey 规范化稳定")
    void normalizeNamespaceAndCacheKey() {
        assertEquals("", SyntheticMemberQueryModelResolver.normalizeNamespace(null));
        assertEquals("dev", SyntheticMemberQueryModelResolver.normalizeNamespace(" dev "));
        assertEquals("dev|FactSalesQueryModel|product",
                SyntheticMemberQueryModelResolver.buildCacheKey(" dev ", "FactSalesQueryModel", "product"));
    }

    private void assertField(SyntheticMemberQueryModelSchema schema,
                             String name,
                             String sourceRef,
                             SyntheticMemberFieldKind kind,
                             boolean reserved,
                             boolean hierarchyScoped) {
        SyntheticMemberFieldSchema field = schema.fieldIndex().get(name);
        assertNotNull(field, "field should exist: " + name);
        assertEquals(sourceRef, field.sourceRef(), "sourceRef mismatch: " + name);
        assertEquals(kind, field.kind(), "kind mismatch: " + name);
        assertEquals(reserved, field.reserved(), "reserved mismatch: " + name);
        assertEquals(hierarchyScoped, field.hierarchyScoped(), "hierarchyScoped mismatch: " + name);
    }
}
