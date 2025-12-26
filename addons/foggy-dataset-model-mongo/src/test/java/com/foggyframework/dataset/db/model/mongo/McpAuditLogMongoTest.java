package com.foggyframework.dataset.db.model.mongo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 审计日志 MongoDB 模型测试
 *
 * <p>测试 MCP 工具调用审计日志的 QM 模型查询功能，
 * 通过 JdbcService.queryModelData 查询，并与 MongoTemplate 直接查询的结果进行比对</p>
 *
 * <p>对应模型:
 * - TM: foggy-dataset-demo/src/main/resources/foggy/templates/mcp_audit/model/McpAuditLogModel.tm
 * - QM: foggy-dataset-demo/src/main/resources/foggy/templates/mcp_audit/query/McpAuditLogQueryModel.qm
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MCP审计日志MongoDB模型测试 - QM查询对比")
class McpAuditLogMongoTest extends MongoTestSupport {

    @Resource
    private JdbcService jdbcService;

    /**
     * 查询模型名称
     */
    private static final String QUERY_MODEL_NAME = "McpAuditLogQueryModel";

    /**
     * 用于测试的 traceId 列表
     */
    private static final List<String> TEST_TRACE_IDS = new ArrayList<>();

    /**
     * 工具名称字典值
     */
    private static final String[] TOOL_NAMES = {
            "dataset.get_metadata",
            "dataset.describe_model_internal",
            "dataset.query_model_v2",
            "dataset.export_with_chart",
            "chart.generate",
            "dataset_nl.query"
    };

    /**
     * 用户角色字典值
     */
    private static final String[] USER_ROLES = {"ADMIN", "ANALYST", "BUSINESS"};

    /**
     * 错误类型字典值
     */
    private static final String[] ERROR_TYPES = {
            "JSON_PARSE_ERROR",
            "EXECUTION_ERROR",
            "TIMEOUT_ERROR",
            "AUTH_ERROR"
    };

    // ==========================================
    // 测试数据准备
    // ==========================================

    @BeforeAll
    static void beforeAll() {
        log.info("========== MCP审计日志MongoDB模型测试开始 ==========");
    }

    @AfterAll
    static void afterAll() {
        log.info("========== MCP审计日志MongoDB模型测试结束 ==========");
    }

    @Test
    @Order(1)
    @DisplayName("清空并初始化测试数据")
    void setupTestData() {
        // 清空集合
        clearCollection(MCP_AUDIT_COLLECTION);

        // 生成测试数据
        List<Document> testDocuments = generateTestData();

        // 批量插入
        insertDocuments(MCP_AUDIT_COLLECTION, testDocuments);

        // 验证插入
        long count = getCollectionCount(MCP_AUDIT_COLLECTION);
        assertEquals(testDocuments.size(), count, "文档数量应与插入数量一致");

        log.info("测试数据初始化完成，共插入 {} 条记录", count);
    }

    /**
     * 生成测试数据
     *
     * @return 测试文档列表
     */
    private List<Document> generateTestData() {
        List<Document> documents = new ArrayList<>();
        Random random = new Random(42); // 使用固定种子以保证可重复性
        Instant baseTime = Instant.now().minus(7, ChronoUnit.DAYS);

        // 生成3个AI会话（traceId），每个会话有多次工具调用
        for (int session = 0; session < 3; session++) {
            String traceId = UUID.randomUUID().toString();
            TEST_TRACE_IDS.add(traceId);

            // 每个会话生成 3-5 次HTTP请求
            int requestCount = 3 + random.nextInt(3);
            for (int req = 0; req < requestCount; req++) {
                String requestId = UUID.randomUUID().toString();
                String userRole = USER_ROLES[session % USER_ROLES.length];

                // 每次请求调用 1-3 个工具
                int toolCallCount = 1 + random.nextInt(3);
                for (int call = 0; call < toolCallCount; call++) {
                    Document doc = createAuditLogDocument(
                            traceId,
                            requestId,
                            TOOL_NAMES[random.nextInt(TOOL_NAMES.length)],
                            userRole,
                            baseTime.plus(session * 24 + req, ChronoUnit.HOURS)
                                    .plus(call * 10, ChronoUnit.SECONDS),
                            random
                    );
                    documents.add(doc);
                }
            }
        }

        // 添加一些失败的调用记录
        for (int i = 0; i < 5; i++) {
            String traceId = TEST_TRACE_IDS.get(random.nextInt(TEST_TRACE_IDS.size()));
            Document doc = createFailedAuditLogDocument(
                    traceId,
                    UUID.randomUUID().toString(),
                    TOOL_NAMES[random.nextInt(TOOL_NAMES.length)],
                    USER_ROLES[random.nextInt(USER_ROLES.length)],
                    baseTime.plus(random.nextInt(168), ChronoUnit.HOURS),
                    ERROR_TYPES[random.nextInt(ERROR_TYPES.length)],
                    random
            );
            documents.add(doc);
        }

        return documents;
    }

    /**
     * 创建成功的审计日志文档
     */
    private Document createAuditLogDocument(String traceId, String requestId,
                                            String toolName, String userRole,
                                            Instant timestamp, Random random) {
        Document doc = new Document();
        doc.put("traceId", traceId);
        doc.put("requestId", requestId);
        doc.put("toolName", toolName);
        doc.put("userRole", userRole);
        doc.put("authorization", "Bearer ***" + String.format("%04d", random.nextInt(10000)));
        doc.put("timestamp", Date.from(timestamp));
        doc.put("durationMs", 50 + random.nextInt(950)); // 50-1000ms
        doc.put("success", true);
        doc.put("resultSummary", generateResultSummary(toolName, random));
        doc.put("clientIp", "192.168.1." + (1 + random.nextInt(254)));
        doc.put("requestPath", "/api/v1/mcp/rpc");

        // 添加模拟的调用参数
        doc.put("arguments", createSampleArguments(toolName, random));

        return doc;
    }

    /**
     * 创建失败的审计日志文档
     */
    private Document createFailedAuditLogDocument(String traceId, String requestId,
                                                   String toolName, String userRole,
                                                   Instant timestamp, String errorType,
                                                   Random random) {
        Document doc = new Document();
        doc.put("traceId", traceId);
        doc.put("requestId", requestId);
        doc.put("toolName", toolName);
        doc.put("userRole", userRole);
        doc.put("authorization", "Bearer ***" + String.format("%04d", random.nextInt(10000)));
        doc.put("timestamp", Date.from(timestamp));
        doc.put("durationMs", 100 + random.nextInt(2000)); // 失败的通常耗时更长
        doc.put("success", false);
        doc.put("errorType", errorType);
        doc.put("errorMessage", generateErrorMessage(errorType));
        doc.put("clientIp", "192.168.1." + (1 + random.nextInt(254)));
        doc.put("requestPath", "/api/v1/mcp/rpc");
        doc.put("arguments", createSampleArguments(toolName, random));

        return doc;
    }

    /**
     * 生成结果摘要
     */
    private String generateResultSummary(String toolName, Random random) {
        return switch (toolName) {
            case "dataset.get_metadata" -> "返回 " + (3 + random.nextInt(5)) + " 个可用模型";
            case "dataset.describe_model_internal" -> "模型包含 " + (5 + random.nextInt(15)) + " 个字段";
            case "dataset.query_model_v2" -> "查询返回 " + (10 + random.nextInt(990)) + " 条数据";
            case "dataset.export_with_chart" -> "导出成功，文件大小 " + (100 + random.nextInt(900)) + "KB";
            case "chart.generate" -> "图表生成成功，类型: " + (random.nextBoolean() ? "bar" : "line");
            case "dataset_nl.query" -> "自然语言查询解析成功";
            default -> "操作完成";
        };
    }

    /**
     * 生成错误信息
     */
    private String generateErrorMessage(String errorType) {
        return switch (errorType) {
            case "JSON_PARSE_ERROR" -> "JSON参数格式错误: 缺少必需字段 'modelName'";
            case "EXECUTION_ERROR" -> "执行失败: 模型 'UnknownModel' 不存在";
            case "TIMEOUT_ERROR" -> "查询超时: 执行时间超过30秒限制";
            case "AUTH_ERROR" -> "认证失败: Token已过期";
            default -> "未知错误";
        };
    }

    /**
     * 创建示例参数
     */
    private Document createSampleArguments(String toolName, Random random) {
        Document args = new Document();
        switch (toolName) {
            case "dataset.get_metadata" -> args.put("levels", Arrays.asList(1, 2));
            case "dataset.describe_model_internal" -> args.put("modelName", "FactSalesQueryModel");
            case "dataset.query_model_v2" -> {
                args.put("modelName", "FactSalesQueryModel");
                args.put("columns", Arrays.asList("product$caption", "salesAmount"));
                args.put("limit", 100);
            }
            case "dataset.export_with_chart" -> {
                args.put("modelName", "FactSalesQueryModel");
                args.put("chartType", random.nextBoolean() ? "bar" : "pie");
            }
            case "chart.generate" -> {
                args.put("chartType", "bar");
                args.put("title", "销售分析图表");
            }
            case "dataset_nl.query" -> args.put("question", "查询本月销售额前10的商品");
        }
        return args;
    }

    // ==========================================
    // QM 模型查询 vs MongoTemplate 直查 对比测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("明细查询对比 - 查询所有日志（带分页）")
    void testDetailQuery_AllLogs() {
        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query().limit(10);
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);
        long mongoTotal = getCollectionCount(MCP_AUDIT_COLLECTION);

        log.info("MongoTemplate 查询结果: {} 条, 总数: {}", mongoResults.size(), mongoTotal);

        // 2. 通过 JdbcService 使用 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("traceId", "requestId", "toolName", "userRole", "timestamp", "success"));
        queryRequest.setReturnTotal(true);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型查询结果: {} 条, 总数: {}", result.getItems().size(), result.getTotal());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "返回条数应一致");
        assertEquals((int) mongoTotal, result.getTotal(), "总数应一致");

        // 验证返回数据结构
        if (!result.getItems().isEmpty()) {
            Map<String, Object> firstRow = (Map<String, Object>) result.getItems().get(0);
            assertTrue(firstRow.containsKey("traceId"), "应包含 traceId 字段");
            assertTrue(firstRow.containsKey("toolName"), "应包含 toolName 字段");
            assertTrue(firstRow.containsKey("success"), "应包含 success 字段");
        }
    }

    @Test
    @Order(11)
    @DisplayName("条件查询对比 - 按 traceId 过滤")
    void testFilterQuery_ByTraceId() {
        String traceId = TEST_TRACE_IDS.get(0);

        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(Criteria.where("traceId").is(traceId));
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 按traceId查询: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("traceId", "toolName", "timestamp", "success", "durationMs"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("traceId");
        slice.setOp("=");
        slice.setValue(traceId);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型按traceId查询: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "按traceId过滤的结果数量应一致");

        // 验证所有返回的数据都属于同一个 traceId
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertEquals(traceId, row.get("traceId"), "traceId 应匹配");
        }
    }

    @Test
    @Order(12)
    @DisplayName("条件查询对比 - 按工具名称过滤")
    void testFilterQuery_ByToolName() {
        String toolName = "dataset.query_model_v2";

        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(Criteria.where("toolName").is(toolName));
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 按toolName查询: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("toolName", "userRole", "durationMs", "success"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("toolName");
        slice.setOp("=");
        slice.setValue(toolName);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型按toolName查询: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "按toolName过滤的结果数量应一致");

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertEquals(toolName, row.get("toolName"), "toolName 应匹配");
        }
    }

    @Test
    @Order(13)
    @DisplayName("条件查询对比 - 查询失败记录")
    void testFilterQuery_FailedCalls() {
        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(Criteria.where("success").is(false));
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 查询失败记录: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("toolName", "errorType", "errorMessage", "durationMs"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("success");
        slice.setOp("=");
        slice.setValue(false);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型查询失败记录: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "失败记录数量应一致");
        assertEquals(5, result.getItems().size(), "应有5条失败记录");

        // 验证失败记录包含错误信息
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertNotNull(row.get("errorType"), "失败记录应有errorType");
        }
    }

    @Test
    @Order(14)
    @DisplayName("条件查询对比 - IN 条件过滤")
    void testFilterQuery_InCondition() {
        List<String> roles = Arrays.asList("ADMIN", "ANALYST");

        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(Criteria.where("userRole").in(roles));
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate IN条件查询: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("userRole", "toolName", "success"));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("userRole");
        slice.setOp("in");
        slice.setValue(roles);
        slices.add(slice);
        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型IN条件查询: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "IN条件查询结果数量应一致");

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertTrue(roles.contains(row.get("userRole")), "userRole应在指定列表中");
        }
    }

    @Test
    @Order(15)
    @DisplayName("条件查询对比 - 组合条件过滤")
    void testFilterQuery_CombinedConditions() {
        String userRole = "ADMIN";

        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(
                Criteria.where("userRole").is(userRole)
                        .and("success").is(true)
        );
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 组合条件查询: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("userRole", "toolName", "success", "durationMs"));

        List<SliceRequestDef> slices = new ArrayList<>();

        SliceRequestDef roleSlice = new SliceRequestDef();
        roleSlice.setField("userRole");
        roleSlice.setOp("=");
        roleSlice.setValue(userRole);
        slices.add(roleSlice);

        SliceRequestDef successSlice = new SliceRequestDef();
        successSlice.setField("success");
        successSlice.setOp("=");
        successSlice.setValue(true);
        slices.add(successSlice);

        queryRequest.setSlice(slices);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型组合条件查询: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "组合条件查询结果数量应一致");

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertEquals(userRole, row.get("userRole"), "userRole应匹配");
            assertEquals(true, row.get("success"), "success应为true");
        }
    }

    // ==========================================
    // 排序查询对比测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("排序查询对比 - 按时间倒序")
    void testSortQuery_ByTimestampDesc() {
        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query()
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .limit(10);
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 时间倒序查询: {} 条", mongoResults.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("timestamp", "toolName", "success"));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("timestamp");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型时间倒序查询: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "排序查询结果数量应一致");

        // 验证排序正确性
        Date prevTimestamp = null;
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            Date timestamp = (Date) row.get("timestamp");
            if (prevTimestamp != null) {
                assertTrue(timestamp.compareTo(prevTimestamp) <= 0, "时间应为降序排列");
            }
            prevTimestamp = timestamp;
        }
    }

    @Test
    @Order(21)
    @DisplayName("排序查询对比 - 按耗时降序")
    void testSortQuery_ByDurationDesc() {
        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query()
                .with(Sort.by(Sort.Direction.DESC, "durationMs"))
                .limit(5);
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        log.info("MongoTemplate 耗时倒序查询:");
        for (Document doc : mongoResults) {
            log.info("  工具: {}, 耗时: {}ms", doc.getString("toolName"), doc.getInteger("durationMs"));
        }

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("toolName", "durationMs", "success"));

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("durationMs");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 5);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型耗时倒序查询:");
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            log.info("  工具: {}, 耗时: {}ms", row.get("toolName"), row.get("durationMs"));
        }

        // 3. 对比验证
        assertEquals(mongoResults.size(), result.getItems().size(), "排序查询结果数量应一致");

        // 验证耗时排序正确
        Integer prevDuration = null;
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            Integer duration = (Integer) row.get("durationMs");
            if (prevDuration != null) {
                assertTrue(duration <= prevDuration, "耗时应为降序排列");
            }
            prevDuration = duration;
        }

        // 对比具体的耗时值
        for (int i = 0; i < mongoResults.size(); i++) {
            Document mongoDoc = mongoResults.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);
            assertEquals(mongoDoc.getInteger("durationMs"), qmRow.get("durationMs"),
                    "第" + (i + 1) + "条记录的耗时应一致");
        }
    }

    // ==========================================
    // 分页查询对比测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("分页查询对比 - 验证分页正确性")
    void testPaginationQuery() {
        int pageSize = 5;

        // 1. 查询第一页
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("toolName", "timestamp", "success"));
        queryRequest.setReturnTotal(true);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("timestamp");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form1 = new PagingRequest<>(1, pageSize, 0, pageSize, queryRequest);
        PagingResultImpl result1 = jdbcService.queryModelData(form1);

        // 2. 查询第二页
        PagingRequest<DbQueryRequestDef> form2 = new PagingRequest<>(2, pageSize, pageSize, pageSize, queryRequest);
        PagingResultImpl result2 = jdbcService.queryModelData(form2);

        log.info("分页查询: 第一页 {} 条, 第二页 {} 条, 总数: {}",
                result1.getItems().size(), result2.getItems().size(), result1.getTotal());

        // 3. 对比验证
        assertEquals(pageSize, result1.getItems().size(), "第一页应返回" + pageSize + "条");
        assertEquals(pageSize, result2.getItems().size(), "第二页应返回" + pageSize + "条");
        assertEquals(result1.getTotal(), result2.getTotal(), "两页的总数应一致");

        // 验证第一页和第二页数据不重复
        Map<String, Object> lastOfPage1 = (Map<String, Object>) result1.getItems().get(pageSize - 1);
        Map<String, Object> firstOfPage2 = (Map<String, Object>) result2.getItems().get(0);

        Date page1LastTimestamp = (Date) lastOfPage1.get("timestamp");
        Date page2FirstTimestamp = (Date) firstOfPage2.get("timestamp");
        assertTrue(page1LastTimestamp.compareTo(page2FirstTimestamp) <= 0,
                "第一页最后一条的时间应<=第二页第一条的时间");
    }

    // ==========================================
    // 字段值对比测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("字段值对比 - 逐条验证数据一致性")
    void testFieldValueComparison() {
        String traceId = TEST_TRACE_IDS.get(0);

        // 1. MongoTemplate 直接查询
        Query mongoQuery = new Query(Criteria.where("traceId").is(traceId))
                .with(Sort.by(Sort.Direction.ASC, "timestamp"));
        List<Document> mongoResults = find(MCP_AUDIT_COLLECTION, mongoQuery);

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList(
                "traceId", "requestId", "toolName", "userRole",
                "timestamp", "durationMs", "success", "clientIp", "requestPath"
        ));

        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("traceId");
        slice.setOp("=");
        slice.setValue(traceId);
        slices.add(slice);
        queryRequest.setSlice(slices);

        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("timestamp");
        order.setOrder("ASC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("字段值对比: MongoTemplate {} 条, QM模型 {} 条",
                mongoResults.size(), result.getItems().size());

        // 3. 逐条对比
        assertEquals(mongoResults.size(), result.getItems().size(), "记录数应一致");

        for (int i = 0; i < mongoResults.size(); i++) {
            Document mongoDoc = mongoResults.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            assertEquals(mongoDoc.getString("traceId"), qmRow.get("traceId"),
                    "第" + (i + 1) + "条 traceId 应一致");
            assertEquals(mongoDoc.getString("requestId"), qmRow.get("requestId"),
                    "第" + (i + 1) + "条 requestId 应一致");
            assertEquals(mongoDoc.getString("toolName"), qmRow.get("toolName"),
                    "第" + (i + 1) + "条 toolName 应一致");
            assertEquals(mongoDoc.getString("userRole"), qmRow.get("userRole"),
                    "第" + (i + 1) + "条 userRole 应一致");
            assertEquals(mongoDoc.getInteger("durationMs"), qmRow.get("durationMs"),
                    "第" + (i + 1) + "条 durationMs 应一致");
            assertEquals(mongoDoc.getBoolean("success"), qmRow.get("success"),
                    "第" + (i + 1) + "条 success 应一致");
            assertEquals(mongoDoc.getString("clientIp"), qmRow.get("clientIp"),
                    "第" + (i + 1) + "条 clientIp 应一致");
            assertEquals(mongoDoc.getString("requestPath"), qmRow.get("requestPath"),
                    "第" + (i + 1) + "条 requestPath 应一致");

            log.debug("第{}条记录验证通过: tool={}, duration={}ms",
                    i + 1, qmRow.get("toolName"), qmRow.get("durationMs"));
        }

        log.info("所有 {} 条记录的字段值对比验证通过", mongoResults.size());
    }

    @Test
    @Order(50)
    @DisplayName("清理测试数据")
    void cleanupTestData() {
        // 保留数据用于后续分析，不清理
        // clearCollection(MCP_AUDIT_COLLECTION);

        long count = getCollectionCount(MCP_AUDIT_COLLECTION);
        log.info("测试完成，集合 {} 保留 {} 条记录供后续分析", MCP_AUDIT_COLLECTION, count);
    }
}
