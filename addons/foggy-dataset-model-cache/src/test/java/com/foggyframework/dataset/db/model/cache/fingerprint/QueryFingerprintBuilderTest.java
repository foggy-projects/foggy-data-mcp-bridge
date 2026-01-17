package com.foggyframework.dataset.db.model.cache.fingerprint;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        pagingRequest.setPageNo(3);
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
        SliceRequestDef parentSlice = new SliceRequestDef();
        parentSlice.setLink("OR");

        List<SliceRequestDef> children = new ArrayList<>();
        SliceRequestDef child1 = new SliceRequestDef();
        child1.setField("status");
        child1.setOp("=");
        child1.setValue("active");
        children.add(child1);

        SliceRequestDef child2 = new SliceRequestDef();
        child2.setField("status");
        child2.setOp("=");
        child2.setValue("pending");
        children.add(child2);

        parentSlice.setChildren(children);
        slices.add(parentSlice);
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

    /**
     * 创建测试用的 ModelResultContext
     */
    private ModelResultContext createContext(DbQueryRequestDef queryRequest) {
        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setPageNo(1);
        pagingRequest.setPageSize(10);

        return new ModelResultContext(pagingRequest, null);
    }
}
