package com.foggyframework.dataset.db.model.authorization;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.AuthorizationStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStepExecutor;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限控制单元测试
 *
 * <p>测试数据权限控制相关功能：
 * <ul>
 *   <li>SecurityContext 创建和属性管理</li>
 *   <li>AuthorizationStep 过滤条件注入</li>
 *   <li>DataSetResultStepExecutor 执行流程</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("权限控制单元测试")
class AuthorizationStepTest {

    // 常量引用，避免匿名类中直接引用接口常量
    private static final int CONTINUE = DataSetResultStep.CONTINUE;
    private static final int ABORT = DataSetResultStep.ABORT;

    // ==========================================
    // SecurityContext 测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("SecurityContext - fromAuthorization 创建")
    void testSecurityContextFromAuthorization() {
        String token = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.fromAuthorization(token);

        assertNotNull(ctx);
        assertEquals(token, ctx.getAuthorization());
        assertNull(ctx.getUserId());
        assertNull(ctx.getTenantId());
    }

    @Test
    @Order(2)
    @DisplayName("SecurityContext - Builder 模式创建")
    void testSecurityContextBuilder() {
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.builder()
                .authorization("Bearer xxx")
                .userId("user-123")
                .tenantId("tenant-abc")
                .deptId("dept-001")
                .roles(Arrays.asList("ADMIN", "USER"))
                .build();

        assertEquals("Bearer xxx", ctx.getAuthorization());
        assertEquals("user-123", ctx.getUserId());
        assertEquals("tenant-abc", ctx.getTenantId());
        assertEquals("dept-001", ctx.getDeptId());
        assertEquals(2, ctx.getRoles().size());
        assertTrue(ctx.getRoles().contains("ADMIN"));
    }

    @Test
    @Order(3)
    @DisplayName("SecurityContext - 额外属性管理")
    void testSecurityContextAttributes() {
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.builder().build();

        // 设置属性
        ctx.setAttribute("customKey", "customValue");
        ctx.setAttribute("numericKey", 123);

        // 获取属性
        String strValue = ctx.getAttribute("customKey");
        Integer numValue = ctx.getAttribute("numericKey");

        assertEquals("customValue", strValue);
        assertEquals(123, numValue);
        assertNull(ctx.getAttribute("nonExistent"));
    }

    // ==========================================
    // AuthorizationStep 测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("AuthorizationStep - 无 SecurityContext 时不添加过滤")
    void testAuthorizationStepWithoutSecurityContext() {
        AuthorizationStep step = new AuthorizationStep();
        ModelResultContext ctx = createMockContext(null);

        int result = step.beforeQuery(ctx);

        assertEquals(CONTINUE, result);
        // 没有添加任何过滤条件
        assertTrue(ctx.getRequest().getParam().getSlice() == null ||
                ctx.getRequest().getParam().getSlice().isEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("AuthorizationStep - 租户过滤条件注入")
    void testAuthorizationStepTenantFilter() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .tenantId("tenant-123")
                .build();

        ModelResultContext ctx = createMockContext(security);

        int result = step.beforeQuery(ctx);

        assertEquals(CONTINUE, result);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertNotNull(slice);
        assertEquals(1, slice.size());

        SliceRequestDef tenantFilter = slice.get(0);
        assertEquals("tenant_id", tenantFilter.getField());
        assertEquals("eq", tenantFilter.getOp());
        assertEquals("tenant-123", tenantFilter.getValue());
    }

    @Test
    @Order(12)
    @DisplayName("AuthorizationStep - 部门过滤条件注入")
    void testAuthorizationStepDeptFilter() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .deptId("dept-456")
                .build();

        ModelResultContext ctx = createMockContext(security);

        int result = step.beforeQuery(ctx);

        assertEquals(CONTINUE, result);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertNotNull(slice);
        assertEquals(1, slice.size());

        SliceRequestDef deptFilter = slice.get(0);
        assertEquals("dept_id", deptFilter.getField());
        assertEquals("eq", deptFilter.getOp());
        assertEquals("dept-456", deptFilter.getValue());
    }

    @Test
    @Order(13)
    @DisplayName("AuthorizationStep - 多条件组合注入")
    void testAuthorizationStepMultipleFilters() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .tenantId("tenant-123")
                .deptId("dept-456")
                .build();

        ModelResultContext ctx = createMockContext(security);

        int result = step.beforeQuery(ctx);

        assertEquals(CONTINUE, result);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertNotNull(slice);
        assertEquals(2, slice.size());

        // 验证包含租户和部门过滤
        Set<String> fields = new HashSet<>();
        for (SliceRequestDef s : slice) {
            fields.add(s.getField());
        }
        assertTrue(fields.contains("tenant_id"));
        assertTrue(fields.contains("dept_id"));
    }

    @Test
    @Order(14)
    @DisplayName("AuthorizationStep - 自定义字段名")
    void testAuthorizationStepCustomColumnNames() {
        AuthorizationStep step = new AuthorizationStep();
        step.setTenantIdColumn("org_id");
        step.setDeptIdColumn("department_id");
        step.setUserIdColumn("creator_id");

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .tenantId("org-789")
                .build();

        ModelResultContext ctx = createMockContext(security);

        step.beforeQuery(ctx);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertEquals(1, slice.size());
        assertEquals("org_id", slice.get(0).getField());
        assertEquals("org-789", slice.get(0).getValue());
    }

    @Test
    @Order(15)
    @DisplayName("AuthorizationStep - 与现有过滤条件合并")
    void testAuthorizationStepMergeWithExisting() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .tenantId("tenant-123")
                .build();

        // 创建带有现有过滤条件的上下文
        ModelResultContext ctx = createMockContext(security);
        SliceRequestDef existingFilter = new SliceRequestDef();
        existingFilter.setField("status");
        existingFilter.setOp("eq");
        existingFilter.setValue("active");
        ctx.getRequest().getParam().setSlice(new ArrayList<>(List.of(existingFilter)));

        step.beforeQuery(ctx);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertEquals(2, slice.size());

        // 验证原有条件保留
        boolean hasStatusFilter = slice.stream()
                .anyMatch(s -> "status".equals(s.getField()));
        boolean hasTenantFilter = slice.stream()
                .anyMatch(s -> "tenant_id".equals(s.getField()));

        assertTrue(hasStatusFilter, "原有 status 过滤条件应保留");
        assertTrue(hasTenantFilter, "应添加 tenant_id 过滤条件");
    }

    @Test
    @Order(16)
    @DisplayName("AuthorizationStep - 用户级别过滤（自定义实现）")
    void testAuthorizationStepUserFilter() {
        // 自定义实现：非管理员需要按用户过滤
        AuthorizationStep step = new AuthorizationStep() {
            @Override
            protected boolean shouldFilterByUser(ModelResultContext.SecurityContext security) {
                List<String> roles = security.getRoles();
                return roles == null || !roles.contains("ADMIN");
            }
        };

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .userId("user-001")
                .roles(Arrays.asList("USER")) // 非管理员
                .build();

        ModelResultContext ctx = createMockContext(security);

        step.beforeQuery(ctx);

        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertEquals(1, slice.size());
        assertEquals("user_id", slice.get(0).getField());
        assertEquals("user-001", slice.get(0).getValue());
    }

    @Test
    @Order(17)
    @DisplayName("AuthorizationStep - 管理员跳过用户过滤")
    void testAuthorizationStepAdminSkipsUserFilter() {
        AuthorizationStep step = new AuthorizationStep() {
            @Override
            protected boolean shouldFilterByUser(ModelResultContext.SecurityContext security) {
                List<String> roles = security.getRoles();
                return roles == null || !roles.contains("ADMIN");
            }
        };

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .userId("admin-001")
                .roles(Arrays.asList("ADMIN", "USER")) // 管理员
                .build();

        ModelResultContext ctx = createMockContext(security);

        step.beforeQuery(ctx);

        // 管理员不应添加用户过滤
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertTrue(slice == null || slice.isEmpty());
    }

    // ==========================================
    // DataSetResultStepExecutor 测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("StepExecutor - 执行顺序测试")
    void testStepExecutorOrder() {
        List<String> executionOrder = new ArrayList<>();

        DataSetResultStep step1 = createStep("step1", executionOrder, CONTINUE, 100);
        DataSetResultStep step2 = createStep("step2", executionOrder, CONTINUE, 1000);
        DataSetResultStep step3 = createStep("step3", executionOrder, CONTINUE, 50);

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(Arrays.asList(step1, step2, step3));
        ModelResultContext ctx = createMockContext(null);

        executor.executeBeforeQuery(ctx);

        // 与 Spring @Order 一致，按 order 升序执行：step3(50) -> step1(100) -> step2(1000)
        assertEquals(3, executionOrder.size());
        assertEquals("step3", executionOrder.get(0));
        assertEquals("step1", executionOrder.get(1));
        assertEquals("step2", executionOrder.get(2));
    }

    @Test
    @Order(21)
    @DisplayName("StepExecutor - ABORT 中止执行链")
    void testStepExecutorAbort() {
        List<String> executionOrder = new ArrayList<>();

        DataSetResultStep step1 = createStep("step1", executionOrder, ABORT, 100);
        DataSetResultStep step2 = createStep("step2", executionOrder, CONTINUE, 1000);

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(Arrays.asList(step1, step2));
        ModelResultContext ctx = createMockContext(null);

        int result = executor.executeBeforeQuery(ctx);

        assertEquals(ABORT, result);
        // step2 不应执行
        assertEquals(1, executionOrder.size());
        assertEquals("step1", executionOrder.get(0));
    }

    @Test
    @Order(22)
    @DisplayName("StepExecutor - process 方法执行")
    void testStepExecutorProcess() {
        List<String> processOrder = new ArrayList<>();

        DataSetResultStep step1 = createProcessStep("process1", processOrder, CONTINUE, 100);
        DataSetResultStep step2 = createProcessStep("process2", processOrder, CONTINUE, 200);

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(Arrays.asList(step1, step2));
        ModelResultContext ctx = createMockContext(null);

        executor.executeProcess(ctx);

        assertEquals(2, processOrder.size());
        assertEquals("process1", processOrder.get(0)); // 小值优先
        assertEquals("process2", processOrder.get(1));
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private ModelResultContext createMockContext(ModelResultContext.SecurityContext securityContext) {
        DbQueryRequestDef param = new DbQueryRequestDef();
        PagingRequest<DbQueryRequestDef> request = new PagingRequest<>();
        request.setParam(param);

        ModelResultContext ctx = new ModelResultContext(request, null, securityContext);
        return ctx;
    }

    /**
     * 创建用于测试 beforeQuery 的 Step
     */
    private DataSetResultStep createStep(String name, List<String> executionOrder, int returnValue, int order) {
        return new DataSetResultStep() {
            @Override
            public int beforeQuery(ModelResultContext ctx) {
                executionOrder.add(name);
                return returnValue;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public int process(ModelResultContext ctx) {
                return CONTINUE;
            }
        };
    }

    /**
     * 创建用于测试 process 的 Step
     */
    private DataSetResultStep createProcessStep(String name, List<String> processOrder, int returnValue, int order) {
        return new DataSetResultStep() {
            @Override
            public int beforeQuery(ModelResultContext ctx) {
                return CONTINUE;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public int process(ModelResultContext ctx) {
                processOrder.add(name);
                return returnValue;
            }
        };
    }

    // ==========================================
    // 增强：SecurityContext 边界值测试
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("SecurityContext - 空字符串属性值")
    void testSecurityContextEmptyStrings() {
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.builder()
                .tenantId("")
                .userId("")
                .deptId("")
                .build();

        assertEquals("", ctx.getTenantId());
        assertEquals("", ctx.getUserId());
        assertEquals("", ctx.getDeptId());
    }

    @Test
    @Order(5)
    @DisplayName("SecurityContext - null roles 列表")
    void testSecurityContextNullRoles() {
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.builder()
                .userId("user-001")
                .roles(null)
                .build();

        assertNull(ctx.getRoles());
    }

    @Test
    @Order(6)
    @DisplayName("SecurityContext - 空 roles 列表")
    void testSecurityContextEmptyRoles() {
        ModelResultContext.SecurityContext ctx = ModelResultContext.SecurityContext.builder()
                .userId("user-001")
                .roles(Collections.emptyList())
                .build();

        assertNotNull(ctx.getRoles());
        assertTrue(ctx.getRoles().isEmpty());
    }

    // ==========================================
    // 增强：AuthorizationStep 边界行为
    // ==========================================

    @Test
    @Order(18)
    @DisplayName("AuthorizationStep - 空字符串 tenantId 不添加过滤")
    void testAuthorizationStepEmptyTenantId() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .tenantId("")
                .build();

        ModelResultContext ctx = createMockContext(security);

        step.beforeQuery(ctx);

        // 空字符串的 tenantId 不应添加过滤条件
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertTrue(slice == null || slice.isEmpty(),
                "Empty tenantId should not generate filter");
    }

    @Test
    @Order(19)
    @DisplayName("AuthorizationStep - null SecurityContext 中所有字段为 null")
    void testAuthorizationStepAllNullFields() {
        AuthorizationStep step = new AuthorizationStep();

        ModelResultContext.SecurityContext security = ModelResultContext.SecurityContext.builder()
                .build();

        ModelResultContext ctx = createMockContext(security);

        int result = step.beforeQuery(ctx);

        assertEquals(CONTINUE, result, "Should continue even with all-null security context");
        List<SliceRequestDef> slice = ctx.getRequest().getParam().getSlice();
        assertTrue(slice == null || slice.isEmpty(),
                "All-null security context should not add any filters");
    }

    // ==========================================
    // 增强：StepExecutor ABORT 后续 process 不执行
    // ==========================================

    @Test
    @Order(23)
    @DisplayName("StepExecutor - ABORT 后续步骤不执行 (3步链)")
    void testStepExecutorAbortChain() {
        List<String> executionOrder = new ArrayList<>();

        DataSetResultStep step1 = createStep("step1", executionOrder, CONTINUE, 100);
        DataSetResultStep step2 = createStep("step2", executionOrder, ABORT, 500);
        DataSetResultStep step3 = createStep("step3", executionOrder, CONTINUE, 1000);

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(Arrays.asList(step1, step2, step3));
        ModelResultContext ctx = createMockContext(null);

        int result = executor.executeBeforeQuery(ctx);

        assertEquals(ABORT, result);
        // step1(100) 先执行 → CONTINUE, step2(500) → ABORT, step3 不执行
        assertEquals(2, executionOrder.size());
        assertEquals("step1", executionOrder.get(0));
        assertEquals("step2", executionOrder.get(1));
    }

    @Test
    @Order(24)
    @DisplayName("StepExecutor - 空步骤列表不报错")
    void testStepExecutorEmptySteps() {
        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(Collections.emptyList());
        ModelResultContext ctx = createMockContext(null);

        int result = executor.executeBeforeQuery(ctx);
        assertEquals(CONTINUE, result, "Empty steps should return CONTINUE");
    }

    @Test
    @Order(25)
    @DisplayName("StepExecutor - process 执行顺序独立于 beforeQuery")
    void testStepExecutorProcessIndependent() {
        List<String> beforeOrder = new ArrayList<>();
        List<String> processOrder = new ArrayList<>();

        // 创建同时实现 beforeQuery 和 process 的 step
        DataSetResultStep step = new DataSetResultStep() {
            @Override
            public int beforeQuery(ModelResultContext ctx) {
                beforeOrder.add("before");
                return CONTINUE;
            }
            @Override
            public int order() {
                return 100;
            }
            @Override
            public int process(ModelResultContext ctx) {
                processOrder.add("process");
                return CONTINUE;
            }
        };

        DataSetResultStepExecutor executor = new DataSetResultStepExecutor(List.of(step));
        ModelResultContext ctx = createMockContext(null);

        executor.executeBeforeQuery(ctx);
        executor.executeProcess(ctx);

        assertEquals(1, beforeOrder.size());
        assertEquals(1, processOrder.size());
    }
}
