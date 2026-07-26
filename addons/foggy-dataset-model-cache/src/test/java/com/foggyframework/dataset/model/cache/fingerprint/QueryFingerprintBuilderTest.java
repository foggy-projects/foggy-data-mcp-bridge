package com.foggyframework.dataset.model.cache.fingerprint;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.expression.SqlFragment;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignature;
import com.foggyframework.dataset.model.spi.support.CalculatedDbColumn;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryFingerprintBuilder 单元测试
 */
@DisplayName("QueryFingerprintBuilder 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryFingerprintBuilderTest {

    private QueryFingerprintBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new QueryFingerprintBuilder();
    }

    @Test
    @Order(1)
    @DisplayName("build - 基本查询生成正确指纹")
    void testBuild_BasicQuery() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("id", "name", "status"));

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertEquals("TestModel", fingerprint.getModelName());
        assertEquals(3, fingerprint.getColumns().size());
        assertTrue(fingerprint.isCacheable(), "基本查询应该可缓存");

        context.setCalculatedColumns(Collections.singletonList(
                new CalculatedDbColumn("grossAmount", "Gross amount", SqlFragment.ofLiteral("price * quantity"))));

        QueryFingerprint calculated = builder.build(context);

        assertEquals(4, calculated.getColumns().size());
        assertEquals(1, calculated.getCalculatedFieldCount());
        assertTrue(calculated.getColumns().stream().anyMatch(column -> column.startsWith("grossAmount:")),
                "计算字段名称和表达式哈希必须进入列身份");
    }

    @Test
    @Order(2)
    @DisplayName("build - 包含分组的查询")
    void testBuild_WithGroupBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("category", "count"));

        List<GroupRequestDef> groupBy = new ArrayList<>();
        GroupRequestDef g1 = new GroupRequestDef();
        g1.setField("category");
        groupBy.add(g1);
        GroupRequestDef g2 = new GroupRequestDef();
        g2.setField("count");
        g2.setAgg("SUM");
        groupBy.add(g2);
        queryRequest.setGroupBy(groupBy);

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertNotNull(fingerprint.getGroupBy());
        assertEquals(2, fingerprint.getGroupBy().size());
    }

    @Test
    @Order(3)
    @DisplayName("build - 包含排序的查询")
    void testBuild_WithOrderBy() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        List<OrderRequestDef> orderBy = new ArrayList<>();
        OrderRequestDef o1 = new OrderRequestDef();
        o1.setField("id");
        o1.setDir("ASC");
        orderBy.add(o1);
        OrderRequestDef o2 = new OrderRequestDef();
        o2.setField("name");
        o2.setDir("DESC");
        orderBy.add(o2);
        queryRequest.setOrderBy(orderBy);

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertNotNull(fingerprint.getOrderBy());
        assertEquals(2, fingerprint.getOrderBy().size());
        // orderBy 保持原顺序
        assertEquals("id:ASC", fingerprint.getOrderBy().get(0));
        assertEquals("name:DESC", fingerprint.getOrderBy().get(1));
    }

    @Test
    @Order(4)
    @DisplayName("build - 包含条件的查询")
    void testBuild_WithSlice() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("status");
        slice.setOp("=");
        slice.setValue("active");
        slices.add(slice);
        queryRequest.setSlice(slices);

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertNotNull(fingerprint.getConditionSignatures());
        assertFalse(fingerprint.getConditionSignatures().isEmpty());
        assertTrue(fingerprint.isCacheable());
    }

    @Test
    @Order(5)
    @DisplayName("build - 分页信息正确")
    void testBuild_Pagination() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Collections.singletonList("id"));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setPage(3);
        pagingRequest.setPageSize(20);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);

        QueryFingerprint fingerprint = builder.build(context);

        assertEquals(3, fingerprint.getPageNo());
        assertEquals(20, fingerprint.getPageSize());
    }

    @Test
    @Order(6)
    @DisplayName("build - 列排序后一致")
    void testBuild_ColumnsSorted() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("c", "a", "b"));

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        // 列应该被排序
        assertEquals(Arrays.asList("a", "b", "c"), fingerprint.getColumns());
    }

    @Test
    @Order(7)
    @DisplayName("build - 嵌套条件组")
    void testBuild_NestedSliceConditions() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Collections.singletonList("id"));

        // 创建嵌套条件：(status = 'active' OR status = 'pending')
        List<SliceRequestDef> slices = new ArrayList<>();

        // 创建 OR 条件组
        SliceRequestDef child1 = new SliceRequestDef();
        child1.setField("status");
        child1.setOp("=");
        child1.setValue("active");

        SliceRequestDef child2 = new SliceRequestDef();
        child2.setField("status");
        child2.setOp("=");
        child2.setValue("pending");

        // 使用 $or 语法创建条件组
        SliceRequestDef orCondition = SliceRequestDef.or(Arrays.asList(child1, child2));
        slices.add(orCondition);
        queryRequest.setSlice(slices);

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertNotNull(fingerprint.getConditionSignatures());
        assertFalse(fingerprint.getConditionSignatures().isEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("build - 空查询也能构建")
    void testBuild_EmptyQuery() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        // 不设置 columns

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertEquals("TestModel", fingerprint.getModelName());
        assertTrue(fingerprint.isCacheable());
    }

    @Test
    @Order(9)
    @DisplayName("build - IN 条件（列表值）")
    void testBuild_InCondition() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Collections.singletonList("id"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("id");
        slice.setOp("in");
        slice.setValue(Arrays.asList(1, 2, 3));
        slices.add(slice);
        queryRequest.setSlice(slices);

        ModelResultContext context = createContext(queryRequest);

        QueryFingerprint fingerprint = builder.build(context);

        assertNotNull(fingerprint);
        assertFalse(fingerprint.getConditionSignatures().isEmpty());
        assertTrue(fingerprint.isCacheable());
    }

    @Test
    @Order(10)
    @DisplayName("build - 相同查询生成相同指纹")
    void testBuild_SameQuerySameFingerprint() {
        DbQueryRequestDef queryRequest1 = new DbQueryRequestDef();
        queryRequest1.setQueryModel("TestModel");
        queryRequest1.setColumns(Arrays.asList("id", "name"));

        List<SliceRequestDef> slices1 = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("status");
        slice1.setOp("=");
        slice1.setValue("active");
        slices1.add(slice1);
        queryRequest1.setSlice(slices1);

        DbQueryRequestDef queryRequest2 = new DbQueryRequestDef();
        queryRequest2.setQueryModel("TestModel");
        queryRequest2.setColumns(Arrays.asList("id", "name"));

        List<SliceRequestDef> slices2 = new ArrayList<>();
        SliceRequestDef slice2 = new SliceRequestDef();
        slice2.setField("status");
        slice2.setOp("=");
        slice2.setValue("active");
        slices2.add(slice2);
        queryRequest2.setSlice(slices2);

        ModelResultContext context1 = createContext(queryRequest1);
        ModelResultContext context2 = createContext(queryRequest2);

        QueryFingerprint fp1 = builder.build(context1);
        QueryFingerprint fp2 = builder.build(context2);

        assertEquals(fp1.toCacheKey(), fp2.toCacheKey(), "相同查询应该生成相同的缓存键");
    }

    @Test
    @Order(11)
    @DisplayName("build - 不同条件值生成不同指纹")
    void testBuild_DifferentValueDifferentFingerprint() {
        DbQueryRequestDef queryRequest1 = new DbQueryRequestDef();
        queryRequest1.setQueryModel("TestModel");
        queryRequest1.setColumns(Collections.singletonList("id"));

        List<SliceRequestDef> slices1 = new ArrayList<>();
        SliceRequestDef slice1 = new SliceRequestDef();
        slice1.setField("status");
        slice1.setOp("=");
        slice1.setValue("active");
        slices1.add(slice1);
        queryRequest1.setSlice(slices1);

        DbQueryRequestDef queryRequest2 = new DbQueryRequestDef();
        queryRequest2.setQueryModel("TestModel");
        queryRequest2.setColumns(Collections.singletonList("id"));

        List<SliceRequestDef> slices2 = new ArrayList<>();
        SliceRequestDef slice2 = new SliceRequestDef();
        slice2.setField("status");
        slice2.setOp("=");
        slice2.setValue("inactive");  // 不同值
        slices2.add(slice2);
        queryRequest2.setSlice(slices2);

        ModelResultContext context1 = createContext(queryRequest1);
        ModelResultContext context2 = createContext(queryRequest2);

        QueryFingerprint fp1 = builder.build(context1);
        QueryFingerprint fp2 = builder.build(context2);

        assertNotEquals(fp1.toCacheKey(), fp2.toCacheKey(), "不同条件值应该生成不同的缓存键");
    }

    @Test
    @Order(12)
    @DisplayName("build - 最终授权签名绑定 fieldAccess、deniedColumns、systemSlice")
    void testBuild_EffectiveSecurityPolicyChangesFingerprint() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        ModelResultContext base = createContext(queryRequest);
        QueryFingerprint unrestricted = builder.build(base);

        base.setFieldAccess(new LinkedHashSet<>(Collections.singletonList("id")));
        base.setAuthorizationSignature(protectedSignature("field-limited"));
        QueryFingerprint fieldLimited = builder.build(base);
        assertNotEquals(unrestricted.toCacheKey(), fieldLimited.toCacheKey(),
                "fieldAccess 变化必须改变指纹");

        base.setFieldAccess(null);
        base.setDeniedColumns(Collections.singletonList(
                new DeniedPhysicalColumn("public", "test", "secret")));
        base.setAuthorizationSignature(protectedSignature("physical-column-denied"));
        QueryFingerprint physicalColumnDenied = builder.build(base);
        assertNotEquals(unrestricted.toCacheKey(), physicalColumnDenied.toCacheKey(),
                "deniedColumns 变化必须改变指纹");

        base.setDeniedColumns(null);
        SliceRequestDef systemFilter = new SliceRequestDef();
        systemFilter.setField("tenant_id");
        systemFilter.setOp("=");
        systemFilter.setValue("tenant-a");
        base.setSystemSlice(Collections.singletonList(systemFilter));
        base.setAuthorizationSignature(protectedSignature("tenant-a"));
        QueryFingerprint systemFiltered = builder.build(base);
        assertNotEquals(unrestricted.toCacheKey(), systemFiltered.toCacheKey(),
                "systemSlice 变化必须改变指纹");

        base.setDeniedColumns(Collections.singletonList(null));
        base.setSystemSlice(null);
        base.setAuthorizationSignature(protectedSignature("null-denied-entry"));
        QueryFingerprint nullDeniedEntry = builder.build(base);
        assertTrue(nullDeniedEntry.isCacheable(), "显式 null denied entry 必须有稳定身份");
        assertNotEquals(unrestricted.toCacheKey(), nullDeniedEntry.toCacheKey());

        base.setDeniedColumns(null);
        base.setSystemSlice(Collections.singletonList(null));
        base.setAuthorizationSignature(protectedSignature("null-system-slice"));
        QueryFingerprint nullSystemSlice = builder.build(base);
        assertTrue(nullSystemSlice.isCacheable(), "显式 null system slice 必须有稳定身份");
        assertNotEquals(unrestricted.toCacheKey(), nullSystemSlice.toCacheKey());

        SliceRequestDef policyGroup = SliceRequestDef.or(Arrays.asList(
                condition("tenant_id", "tenant-a"),
                condition("department_id", "department-a")));
        base.setSystemSlice(Collections.singletonList(policyGroup));
        base.setAuthorizationSignature(protectedSignature("grouped-system-slice"));
        QueryFingerprint groupedSystemSlice = builder.build(base);
        assertTrue(groupedSystemSlice.isCacheable());
        assertNotEquals(unrestricted.toCacheKey(), groupedSystemSlice.toCacheKey(),
                "权限条件树结构必须进入安全指纹");

        base.setSystemSlice(Collections.singletonList(
                new SliceRequestDef("tenant_id", "=", new Object())));
        base.setAuthorizationSignature(null);
        QueryFingerprint unsupportedPolicy = builder.build(base);
        assertTrue(unsupportedPolicy.isHasIncompleteSecurityPolicy());
        assertFalse(unsupportedPolicy.isCacheable(), "无法稳定编码的权限值必须 fail closed");
    }

    @Test
    @Order(13)
    @DisplayName("build - 缺少显式安全上下文时不可缓存")
    void testBuild_MissingSecurityContext_FailClosed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        ModelResultContext context = createContext(queryRequest);
        context.setSecurityContext(null);
        context.setAuthorizationSignature(null);

        QueryFingerprint fingerprint = builder.build(context);

        assertFalse(fingerprint.isCacheable());
        assertTrue(fingerprint.isHasIncompleteSecurityPolicy());
        assertNull(fingerprint.toCacheKey());
        assertTrue(SecurityPolicyFingerprint.from(null).isEmpty());
    }

    @Test
    @Order(14)
    @DisplayName("build - 空安全上下文不得隐式当作 PUBLIC 身份")
    void testBuild_EmptySecurityContext_FailClosed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        ModelResultContext context = createContext(queryRequest);
        context.setSecurityContext(new ModelResultContext.SecurityContext());
        context.setAuthorizationSignature(null);

        QueryFingerprint fingerprint = builder.build(context);

        assertFalse(fingerprint.isCacheable());
        assertTrue(fingerprint.isHasIncompleteSecurityPolicy());
        assertNull(fingerprint.toCacheKey());

        List<ModelResultContext.SecurityContext> explicitIdentities = Arrays.asList(
                ModelResultContext.SecurityContext.builder().userId("user-only").build(),
                ModelResultContext.SecurityContext.builder().tenantId("tenant-only").build(),
                ModelResultContext.SecurityContext.builder().deptId("department-only").build(),
                ModelResultContext.SecurityContext.builder().roles(Arrays.asList(" ", "auditor")).build(),
                ModelResultContext.SecurityContext.builder().attributes(Map.of("scope", "finance")).build());
        int identityIndex = 0;
        for (ModelResultContext.SecurityContext identity : explicitIdentities) {
            ModelResultContext explicit = createContext(queryRequest);
            explicit.setSecurityContext(identity);
            explicit.setAuthorizationSignature(null);
            QueryFingerprint unsignedFingerprint = builder.build(explicit);
            assertTrue(unsignedFingerprint.isHasIncompleteSecurityPolicy(),
                    "原始身份字段不能替代引擎计算的最终授权签名");
            assertFalse(unsignedFingerprint.isCacheable());

            explicit.setAuthorizationSignature(protectedSignature("explicit-" + identityIndex++));
            QueryFingerprint signedFingerprint = builder.build(explicit);
            assertFalse(signedFingerprint.isHasIncompleteSecurityPolicy());
            assertTrue(signedFingerprint.isCacheable());
        }

        ModelResultContext blankRoles = createContext(queryRequest);
        blankRoles.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .roles(Arrays.asList(null, " ", ""))
                .build());
        blankRoles.setAuthorizationSignature(null);
        assertTrue(builder.build(blankRoles).isHasIncompleteSecurityPolicy(),
                "全为空白的角色列表不是显式身份");

        ModelResultContext emptyAttributes = createContext(queryRequest);
        emptyAttributes.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .attributes(Collections.emptyMap())
                .build());
        emptyAttributes.setAuthorizationSignature(null);
        assertTrue(builder.build(emptyAttributes).isHasIncompleteSecurityPolicy(),
                "空 attributes 不是显式身份");
    }

    @Test
    @Order(15)
    @DisplayName("build - 布尔条件树保留递归括号结构")
    void testBuild_DifferentBooleanTreesDoNotCollide() {
        DbQueryRequestDef leftGrouped = new DbQueryRequestDef();
        leftGrouped.setQueryModel("TestModel");
        leftGrouped.setColumns(Collections.singletonList("id"));
        leftGrouped.setSlice(Collections.singletonList(SliceRequestDef.and(Arrays.asList(
                SliceRequestDef.or(Arrays.asList(
                        condition("a", 1),
                        condition("b", 2))),
                condition("c", 3)))));

        DbQueryRequestDef rightGrouped = new DbQueryRequestDef();
        rightGrouped.setQueryModel("TestModel");
        rightGrouped.setColumns(Collections.singletonList("id"));
        rightGrouped.setSlice(Collections.singletonList(SliceRequestDef.or(Arrays.asList(
                condition("a", 1),
                SliceRequestDef.and(Arrays.asList(
                        condition("b", 2),
                        condition("c", 3)))))));

        QueryFingerprint first = builder.build(createContext(leftGrouped));
        QueryFingerprint second = builder.build(createContext(rightGrouped));

        assertNotEquals(first.getConditionSignatures(), second.getConditionSignatures());
        assertNotEquals(first.toCacheKey(), second.toCacheKey(),
                "(A OR B) AND C 与 A OR (B AND C) 必须产生不同缓存键");
    }

    @Test
    @Order(16)
    @DisplayName("build - JdbcQuery 显式原生 SQL 即使带参也 fail closed")
    void testBuild_RawJdbcSqlWithValue_FailClosed() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        ModelResultContext context = createContext(queryRequest);
        JdbcQuery jdbcQuery = new JdbcQuery();
        jdbcQuery.andSql("tenant_id = ?", "tenant-a");
        context.setQuery(jdbcQuery);

        QueryFingerprint fingerprint = builder.build(context);

        assertTrue(fingerprint.isHasRawSql());
        assertFalse(fingerprint.isCacheable());
        assertNull(fingerprint.toCacheKey());

        JdbcQuery structuredQuery = new JdbcQuery();
        structuredQuery.andQueryTypeValueCond("status", "dimension", "ACTIVE");
        JdbcQuery.JdbcGroupCond group = structuredQuery.getWhere().newGroupCond("and");
        group.and("amount > ?", 100);
        structuredQuery.getWhere().addCond(group);
        structuredQuery.getHaving().andList("COUNT(*) IN (?, ?)", Arrays.<Object>asList(1, 2));
        context.setQuery(structuredQuery);

        QueryFingerprint structured = builder.build(context);

        assertFalse(structured.isHasRawSql(), "结构化 JDBC 条件不应被误判为 raw SQL");
        assertFalse(structured.isHasUnsupportedValue());
        assertEquals(2, structured.getConditionSignatures().size(),
                "WHERE 与 HAVING 必须分别进入条件签名");
        assertTrue(structured.isCacheable());
    }

    @Test
    @Order(17)
    @DisplayName("build - 非确定性函数检测不受默认 Locale 影响")
    void testBuild_NonDeterministicFunctionIsLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel("TestModel");
            SliceRequestDef expression = new SliceRequestDef();
            expression.setExpr("current_timestamp() > created_at");
            queryRequest.setSlice(Collections.singletonList(expression));

            QueryFingerprint fingerprint = builder.build(createContext(queryRequest));

            assertTrue(fingerprint.isHasNonDeterministic());
            assertFalse(fingerprint.isCacheable());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private SliceRequestDef condition(String field, Object value) {
        return new SliceRequestDef(field, "=", value);
    }

    /**
     * 创建测试用的 ModelResultContext
     */
    private ModelResultContext createContext(DbQueryRequestDef queryRequest) {
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setPage(1);
        pagingRequest.setPageSize(10);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization("Bearer fingerprint-test")
                .userId("test-user")
                .tenantId("test-tenant")
                .roles(Arrays.asList("reader", "analyst"))
                .build());
        context.setAuthorizationSignature(
                new AuthorizationSignature("PUBLIC:test", true, null));
        return context;
    }

    private AuthorizationSignature protectedSignature(String value) {
        return new AuthorizationSignature("AUTH:" + value, false, null);
    }
}
