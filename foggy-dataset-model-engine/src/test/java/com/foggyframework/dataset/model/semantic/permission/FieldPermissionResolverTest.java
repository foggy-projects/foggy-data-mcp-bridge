package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.permission.FieldPermissionRuleDef;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.spi.DbProperty;
import com.foggyframework.dataset.model.spi.PhysicalColumnMapping;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FieldPermissionResolver 动态 TM/QM 列权限解析")
class FieldPermissionResolverTest {

    private final FieldPermissionResolver resolver = new FieldPermissionResolver();

    @Test
    @DisplayName("QM 不允许放宽 TM 可见字段上界")
    void qmCannotWidenTmPermissions() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "salesAmount", "secretMargin"),
                permissions(false, List.of(rule(Map.of("hasAnyGroup", List.of("finance")),
                        "orderId", "salesAmount")), null),
                permissions(false, List.of(rule(null,
                        "orderId", "salesAmount", "secretMargin")), null));

        ModelResultContext.SecurityContext securityContext = new ModelResultContext.SecurityContext();
        securityContext.setAttribute("groups", List.of("finance"));

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", securityContext,
                null, null);

        assertEquals(Set.of("orderId", "salesAmount"), result.getEffectiveFieldAccess());
        assertFalse(result.getEffectiveFieldAccess().contains("secretMargin"));
    }

    @Test
    @DisplayName("hiddenFields 优先级高于 visibleFields")
    void hiddenFieldsWinOverVisibleFields() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "secretMargin"),
                null,
                permissions(false,
                        List.of(rule(Map.of("hasAnyRole", List.of("manager")),
                                "orderId", "secretMargin")),
                        List.of(rule(Map.of("hasAnyRole", List.of("manager")),
                                "secretMargin"))));

        ModelResultContext.SecurityContext securityContext = ModelResultContext.SecurityContext.builder()
                .roles(List.of("manager"))
                .build();

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", securityContext,
                null, null);

        assertEquals(Set.of("orderId"), result.getEffectiveFieldAccess());
    }

    @Test
    @DisplayName("运行时 fieldAccess 只能继续收窄模型权限")
    void runtimeFieldAccessNarrowsModelPermissions() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "salesAmount", "customer$caption"),
                null,
                permissions(true, null, null));

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                Set.of("orderId", "customer$caption"), null);

        assertEquals(Set.of("orderId", "customer"), result.getEffectiveFieldAccess());
        assertFalse(result.getEffectiveFieldAccess().contains("salesAmount"));
    }

    @Test
    @DisplayName("deniedColumns 映射到 QM 字段后按基础字段拒绝")
    void deniedColumnsMappedToBaseField() {
        PhysicalColumnMapping mapping = mockMapping(Set.of("orderId", "customer$id", "customer$caption"));
        when(mapping.toDeniedQmFields(any())).thenReturn(Set.of("customer$caption"));
        QueryModel queryModel = mockQueryModel(mapping, null, null);

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                null, List.of(new DeniedPhysicalColumn(null, "dim_customer", "customer_name")));

        assertEquals(Set.of("customer"), result.getDeniedQmFields());
        assertEquals(Set.of("orderId"), result.getEffectiveFieldAccess());
    }

    @Test
    @DisplayName("动态谓词只从 securityContext 属性读取上下文")
    void dynamicPredicateUsesSecurityContextAttributes() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "secretMargin"),
                null,
                permissions(false,
                        List.of(rule(Map.of("hasAnyPermission", List.of("sales.margin.read")),
                                "secretMargin")),
                        null));

        ModelResultContext.SecurityContext securityContext = new ModelResultContext.SecurityContext();
        securityContext.setAttribute("permissions", List.of("sales.margin.read"));

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", securityContext,
                null, null);

        assertEquals(Set.of("secretMargin"), result.getEffectiveFieldAccess());
    }

    @Test
    @DisplayName("未知谓词 fail-closed")
    void unknownPredicateFailsClosed() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId"),
                null,
                permissions(false,
                        List.of(rule(Map.of("script", "return true"), "orderId")),
                        null));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resolver.resolve(queryModel, "demo", null, null, null));

        assertTrue(ex.getMessage().contains("Unsupported fieldPermissions.when predicate"));
    }

    @Test
    @DisplayName("未命中规则引用未知字段也 fail-closed")
    void unknownFieldInUnmatchedRuleFailsClosed() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId"),
                null,
                permissions(false,
                        List.of(rule(Map.of("hasAnyRole", List.of("admin")), "unknownField")),
                        null));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resolver.resolve(queryModel, "demo", null, null, null));

        assertTrue(ex.getMessage().contains("unknownField"));
    }

    @Test
    @DisplayName("未配置模型权限和运行时权限时保持不受限")
    void unconstrainedWhenNoPermissionLayerConfigured() {
        QueryModel queryModel = mockQueryModel(Set.of("orderId", "salesAmount"), null, null);

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                null, null);

        assertNull(result.getEffectiveFieldAccess());
        assertTrue(result.getDeniedQmFields().isEmpty());
    }

    @Test
    @DisplayName("请求计算字段别名参与动态权限全集校验")
    void requestCalculatedFieldAliasIncludedInPermissionUniverse() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "salesAmount"),
                null,
                permissions(false, List.of(rule(null, "doubleAmount")), null));

        CalculatedFieldDef calculatedField = new CalculatedFieldDef();
        calculatedField.setName("doubleAmount");
        calculatedField.setExpression("salesAmount * 2");
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setCalculatedFields(List.of(calculatedField));
        ModelResultContext context = new ModelResultContext();
        context.setQueryModel(queryModel);
        context.setRequest(PagingRequest.buildPagingRequest(request, 100));

        FieldPermissionResolution result = resolver.resolve(context);

        assertEquals(Set.of("doubleAmount"), result.getEffectiveFieldAccess());
    }

    @Test
    @DisplayName("多 TM 权限按字段归属模型收窄")
    void multiTableTmPermissionsApplyPerOwningModel() {
        TableModel salesModel = mockTableModel("FactSalesModel",
                permissions(false, List.of(rule(null, "orderId", "salesAmount")), null),
                Set.of("orderId", "salesAmount"));
        TableModel paymentModel = mockTableModel("FactPaymentModel",
                permissions(false, List.of(rule(null, "paymentAmount")), null),
                Set.of("paymentAmount", "paymentMethod"));
        QueryModel queryModel = mockQueryModelWithTables(
                Set.of("orderId", "salesAmount", "paymentAmount", "paymentMethod"),
                null,
                salesModel,
                paymentModel);

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                null, null);

        assertEquals(Set.of("orderId", "salesAmount", "paymentAmount"),
                result.getEffectiveFieldAccess());
        assertFalse(result.getEffectiveFieldAccess().contains("paymentMethod"));
    }

    @Test
    @DisplayName("计算字段别名不要求 TM 重复声明，依赖字段仍受 TM 上界约束")
    void calculatedAliasNotBlockedByTmLayerWhenDependenciesAllowedByTmAndQmAllowsAlias() {
        QueryModel queryModel = mockQueryModel(
                Set.of("orderId", "salesAmount"),
                permissions(false, List.of(rule(null, "salesAmount")), null),
                permissions(false, List.of(rule(null, "salesAmount", "doubleAmount")), null));

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                null, null, Set.of("doubleAmount"));

        assertEquals(Set.of("salesAmount", "doubleAmount"), result.getEffectiveFieldAccess());
        assertFalse(result.getEffectiveFieldAccess().contains("orderId"));
    }

    @Test
    @DisplayName("无法推断 TM 字段归属时退回全局收窄")
    void tableModelWithUnknownFieldOwnershipFallsBackToGlobalConstraint() {
        TableModel tableModel = mockTableModel("UnknownFieldModel",
                permissions(false, List.of(rule(null, "orderId")), null),
                Set.of());
        QueryModel queryModel = mockQueryModelWithTables(
                Set.of("orderId", "salesAmount"),
                null,
                tableModel);

        FieldPermissionResolution result = resolver.resolve(queryModel, "demo", null,
                null, null);

        assertEquals(Set.of("orderId"), result.getEffectiveFieldAccess());
    }

    private QueryModel mockQueryModel(Set<String> fieldNames,
                                      FieldPermissionsDef tmPermissions,
                                      FieldPermissionsDef qmPermissions) {
        return mockQueryModel(mockMapping(fieldNames), tmPermissions, qmPermissions);
    }

    private QueryModel mockQueryModel(PhysicalColumnMapping mapping,
                                      FieldPermissionsDef tmPermissions,
                                      FieldPermissionsDef qmPermissions) {
        TableModel tableModel = mockTableModel("FactSalesModel", tmPermissions, mapping.getAllQmFieldNames());

        QueryModel queryModel = mock(QueryModel.class);
        when(queryModel.getName()).thenReturn("FactSalesQueryModel");
        when(queryModel.getPhysicalColumnMapping()).thenReturn(mapping);
        when(queryModel.getJdbcModel()).thenReturn(tableModel);
        when(queryModel.getJdbcModelList()).thenReturn(List.of(tableModel));
        when(queryModel.getFieldPermissions()).thenReturn(qmPermissions);

        return queryModel;
    }

    private QueryModel mockQueryModelWithTables(Set<String> fieldNames,
                                                FieldPermissionsDef qmPermissions,
                                                TableModel... tableModels) {
        PhysicalColumnMapping mapping = mockMapping(fieldNames);
        QueryModel queryModel = mock(QueryModel.class);
        when(queryModel.getName()).thenReturn("FactSalesQueryModel");
        when(queryModel.getPhysicalColumnMapping()).thenReturn(mapping);
        when(queryModel.getJdbcModel()).thenReturn(tableModels[0]);
        when(queryModel.getJdbcModelList()).thenReturn(List.of(tableModels));
        when(queryModel.getFieldPermissions()).thenReturn(qmPermissions);
        return queryModel;
    }

    private TableModel mockTableModel(String name, FieldPermissionsDef permissions, Set<String> fieldNames) {
        List<DbProperty> properties = fieldNames == null
                ? List.of()
                : fieldNames.stream()
                .map(this::mockProperty)
                .toList();
        TableModel tableModel = mock(TableModel.class);
        when(tableModel.getName()).thenReturn(name);
        when(tableModel.getFieldPermissions()).thenReturn(permissions);
        when(tableModel.getDimensions()).thenReturn(List.of());
        when(tableModel.getProperties()).thenReturn(properties);
        when(tableModel.getMeasures()).thenReturn(List.of());
        return tableModel;
    }

    private DbProperty mockProperty(String name) {
        DbProperty property = mock(DbProperty.class);
        when(property.getName()).thenReturn(name);
        return property;
    }

    private PhysicalColumnMapping mockMapping(Set<String> fieldNames) {
        PhysicalColumnMapping mapping = mock(PhysicalColumnMapping.class);
        when(mapping.getAllQmFieldNames()).thenReturn(new LinkedHashSet<>(fieldNames));
        when(mapping.toDeniedQmFields(any())).thenReturn(Set.of());
        return mapping;
    }

    private FieldPermissionsDef permissions(Boolean defaultVisible,
                                            List<FieldPermissionRuleDef> visibleFields,
                                            List<FieldPermissionRuleDef> hiddenFields) {
        FieldPermissionsDef def = new FieldPermissionsDef();
        def.setDefaultVisible(defaultVisible);
        def.setVisibleFields(visibleFields);
        def.setHiddenFields(hiddenFields);
        return def;
    }

    private FieldPermissionRuleDef rule(Map<String, Object> when, String... fields) {
        FieldPermissionRuleDef rule = new FieldPermissionRuleDef();
        rule.setWhen(when);
        rule.setFields(List.of(fields));
        return rule;
    }
}
