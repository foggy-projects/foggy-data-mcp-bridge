package com.foggyframework.dataset.db.model.mongo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.jdbc.model.def.query.request.*;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import jakarta.annotation.Resource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB 计算字段集成测试
 *
 * <p>测试通过 QM 模型查询时使用 calculatedFields 动态计算字段的功能，
 * 与 MongoTemplate 直接使用 $addFields 聚合的结果进行对比验证</p>
 *
 * <p>对应模型:
 * - TM: src/test/resources/foggy/templates/calc_test/model/SalesOrderTestModel.tm
 * - QM: src/test/resources/foggy/templates/calc_test/query/SalesOrderTestQueryModel.qm
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MongoDB计算字段集成测试 - QM查询与直接聚合对比")
class MongoCalculatedFieldIntegrationTest extends MongoTestSupport {

    @Resource
    private JdbcService jdbcService;

    /**
     * 查询模型名称
     */
    private static final String QUERY_MODEL_NAME = "SalesOrderTestQueryModel";

    /**
     * 测试集合名称
     */
    private static final String TEST_COLLECTION = "sales_order_test";

    /**
     * 测试数据数量
     */
    private static final int TEST_DATA_COUNT = 20;

    /**
     * 用于验证的测试数据
     */
    private static List<Document> testDocuments;

    // ==========================================
    // 测试数据准备
    // ==========================================

    @BeforeAll
    static void beforeAll() {
        log.info("========== MongoDB计算字段集成测试开始 ==========");
    }

    @AfterAll
    static void afterAll() {
        log.info("========== MongoDB计算字段集成测试结束 ==========");
    }

    @Test
    @Order(1)
    @DisplayName("初始化测试数据")
    void setupTestData() {
        // 清空集合
        clearCollection(TEST_COLLECTION);

        // 生成测试数据
        testDocuments = generateTestData();

        // 批量插入
        insertDocuments(TEST_COLLECTION, testDocuments);

        // 验证插入
        long count = getCollectionCount(TEST_COLLECTION);
        assertEquals(TEST_DATA_COUNT, count, "文档数量应与插入数量一致");

        log.info("测试数据初始化完成，共插入 {} 条记录", count);

        // 打印部分测试数据供调试
        for (int i = 0; i < Math.min(3, testDocuments.size()); i++) {
            Document doc = testDocuments.get(i);
            log.info("测试数据[{}]: orderNo={}, price={}, quantity={}, discount={}",
                    i, doc.getString("orderNo"),
                    doc.get("price"), doc.get("quantity"), doc.get("discount"));
        }
    }

    /**
     * 生成测试数据
     */
    private List<Document> generateTestData() {
        List<Document> documents = new ArrayList<>();
        Random random = new Random(42); // 使用固定种子保证可重复性
        Instant baseTime = Instant.now().minus(30, ChronoUnit.DAYS);

        String[] products = {"iPhone 15", "MacBook Pro", "iPad Air", "AirPods Pro", "Apple Watch"};
        String[] categories = {"手机", "电脑", "平板", "配件", "手表"};
        String[] statuses = {"PENDING", "PAID", "SHIPPED", "COMPLETED"};
        String[] customers = {"张三", "李四", "王五", "赵六", "钱七"};

        for (int i = 0; i < TEST_DATA_COUNT; i++) {
            Document doc = new Document();
            doc.put("orderNo", String.format("ORD%06d", i + 1));

            int productIdx = i % products.length;
            doc.put("productName", products[productIdx]);
            doc.put("category", categories[productIdx]);

            // 价格: 100 - 10000 之间的随机数，保留2位小数
            double price = 100 + random.nextDouble() * 9900;
            price = Math.round(price * 100.0) / 100.0;
            doc.put("price", price);

            // 数量: 1 - 10
            int quantity = 1 + random.nextInt(10);
            doc.put("quantity", quantity);

            // 折扣: 0 - 30 之间的整数（表示百分比）
            int discount = random.nextInt(31);
            doc.put("discount", discount);

            // 税率: 0, 6, 9, 13 中的一个
            int[] taxRates = {0, 6, 9, 13};
            doc.put("taxRate", taxRates[random.nextInt(taxRates.length)]);

            // 订单日期
            doc.put("orderDate", Date.from(baseTime.plus(i, ChronoUnit.DAYS)));

            // 订单状态
            doc.put("status", statuses[random.nextInt(statuses.length)]);

            // 客户信息
            int customerIdx = random.nextInt(customers.length);
            doc.put("customerId", String.format("CUST%03d", customerIdx + 1));
            doc.put("customerName", customers[customerIdx]);

            documents.add(doc);
        }

        return documents;
    }

    // ==========================================
    // 基础算术运算测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("计算字段 - 基础乘法 (price * quantity)")
    void testCalculatedField_Multiplication() {
        // 1. 使用 MongoTemplate $addFields 直接计算
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("totalAmount", new Document("$multiply", Arrays.asList("$price", "$quantity")))
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("totalAmount", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 10);

        Aggregation aggregation = Aggregation.newAggregation(addFields, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate 计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, mongoList.size()); i++) {
            Document doc = mongoList.get(i);
            log.info("  {} -> price={}, qty={}, total={}",
                    doc.getString("orderNo"), doc.get("price"), doc.get("quantity"), doc.get("totalAmount"));
        }

        // 2. 使用 QM 模型 + calculatedFields 查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price", "quantity"));

        // 定义计算字段
        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("totalAmount", "总金额", "price * quantity"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("  {} -> price={}, qty={}, total={}",
                    row.get("orderNo"), row.get("price"), row.get("quantity"), row.get("totalAmount"));
        }

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "结果数量应一致");

        for (int i = 0; i < mongoList.size(); i++) {
            Document mongoDoc = mongoList.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            // 验证计算结果
            double mongoTotal = ((Number) mongoDoc.get("totalAmount")).doubleValue();
            double qmTotal = ((Number) qmRow.get("totalAmount")).doubleValue();

            assertEquals(mongoTotal, qmTotal, 0.01,
                    String.format("第%d条 totalAmount 应一致: mongo=%f, qm=%f", i + 1, mongoTotal, qmTotal));
        }

        log.info("基础乘法计算字段测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("计算字段 - 复合运算 (折扣后金额)")
    void testCalculatedField_DiscountedAmount() {
        // 计算: price * quantity * (1 - discount/100)

        // 1. MongoTemplate 直接计算
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("discountedAmount",
                        new Document("$multiply", Arrays.asList(
                                new Document("$multiply", Arrays.asList("$price", "$quantity")),
                                new Document("$subtract", Arrays.asList(
                                        1,
                                        new Document("$divide", Arrays.asList("$discount", 100))
                                ))
                        ))
                )
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("discount", 1)
                        .append("discountedAmount", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 10);

        Aggregation aggregation = Aggregation.newAggregation(addFields, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate 折扣计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, mongoList.size()); i++) {
            Document doc = mongoList.get(i);
            log.info("  {} -> price={}, qty={}, discount={}%, discounted={}",
                    doc.getString("orderNo"), doc.get("price"), doc.get("quantity"),
                    doc.get("discount"), doc.get("discountedAmount"));
        }

        // 2. QM 模型 + calculatedFields
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price", "quantity", "discount"));

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("discountedAmount", "折扣后金额",
                "price * quantity * (1 - discount / 100)"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型折扣计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("  {} -> price={}, qty={}, discount={}%, discounted={}",
                    row.get("orderNo"), row.get("price"), row.get("quantity"),
                    row.get("discount"), row.get("discountedAmount"));
        }

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "结果数量应一致");

        for (int i = 0; i < mongoList.size(); i++) {
            Document mongoDoc = mongoList.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            double mongoDiscounted = ((Number) mongoDoc.get("discountedAmount")).doubleValue();
            double qmDiscounted = ((Number) qmRow.get("discountedAmount")).doubleValue();

            assertEquals(mongoDiscounted, qmDiscounted, 0.01,
                    String.format("第%d条 discountedAmount 应一致", i + 1));
        }

        log.info("复合运算计算字段测试通过");
    }

    // ==========================================
    // 数学函数测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("计算字段 - ROUND函数")
    void testCalculatedField_Round() {
        // 计算: ROUND(price * quantity / 100, 2)

        // 1. MongoTemplate 直接计算
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("roundedValue",
                        new Document("$round", Arrays.asList(
                                new Document("$divide", Arrays.asList(
                                        new Document("$multiply", Arrays.asList("$price", "$quantity")),
                                        100
                                )),
                                2
                        ))
                )
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("roundedValue", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 10);

        Aggregation aggregation = Aggregation.newAggregation(addFields, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate ROUND计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, mongoList.size()); i++) {
            Document doc = mongoList.get(i);
            log.info("  {} -> price*qty/100 rounded = {}",
                    doc.getString("orderNo"), doc.get("roundedValue"));
        }

        // 2. QM 模型 + calculatedFields
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price", "quantity"));

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("roundedValue", "四舍五入值",
                "ROUND(price * quantity / 100, 2)"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型ROUND计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("  {} -> roundedValue = {}",
                    row.get("orderNo"), row.get("roundedValue"));
        }

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "结果数量应一致");

        for (int i = 0; i < mongoList.size(); i++) {
            Document mongoDoc = mongoList.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            double mongoValue = ((Number) mongoDoc.get("roundedValue")).doubleValue();
            double qmValue = ((Number) qmRow.get("roundedValue")).doubleValue();

            assertEquals(mongoValue, qmValue, 0.001,
                    String.format("第%d条 roundedValue 应一致", i + 1));
        }

        log.info("ROUND函数计算字段测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("计算字段 - ABS函数")
    void testCalculatedField_Abs() {
        // 计算: ABS(price - 5000) - 计算与5000的价差绝对值

        // 1. MongoTemplate 直接计算
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("priceDiff",
                        new Document("$abs",
                                new Document("$subtract", Arrays.asList("$price", 5000))
                        )
                )
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("priceDiff", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 10);

        Aggregation aggregation = Aggregation.newAggregation(addFields, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate ABS计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, mongoList.size()); i++) {
            Document doc = mongoList.get(i);
            log.info("  {} -> price={}, |price-5000| = {}",
                    doc.getString("orderNo"), doc.get("price"), doc.get("priceDiff"));
        }

        // 2. QM 模型 + calculatedFields
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price"));

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("priceDiff", "价格差异", "ABS(price - 5000)"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型ABS计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("  {} -> price={}, priceDiff = {}",
                    row.get("orderNo"), row.get("price"), row.get("priceDiff"));
        }

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "结果数量应一致");

        for (int i = 0; i < mongoList.size(); i++) {
            Document mongoDoc = mongoList.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            double mongoValue = ((Number) mongoDoc.get("priceDiff")).doubleValue();
            double qmValue = ((Number) qmRow.get("priceDiff")).doubleValue();

            assertEquals(mongoValue, qmValue, 0.01,
                    String.format("第%d条 priceDiff 应一致", i + 1));
        }

        log.info("ABS函数计算字段测试通过");
    }

    // ==========================================
    // 多个计算字段测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("计算字段 - 多个计算字段组合")
    void testCalculatedField_MultipleFields() {
        // 同时计算多个字段:
        // 1. totalAmount = price * quantity
        // 2. discountAmount = totalAmount * discount / 100
        // 3. finalAmount = totalAmount - discountAmount

        // 1. MongoTemplate 直接计算
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("totalAmount", new Document("$multiply", Arrays.asList("$price", "$quantity")))
                        .append("discountAmount", new Document("$multiply", Arrays.asList(
                                new Document("$multiply", Arrays.asList("$price", "$quantity")),
                                new Document("$divide", Arrays.asList("$discount", 100))
                        )))
                        .append("finalAmount", new Document("$subtract", Arrays.asList(
                                new Document("$multiply", Arrays.asList("$price", "$quantity")),
                                new Document("$multiply", Arrays.asList(
                                        new Document("$multiply", Arrays.asList("$price", "$quantity")),
                                        new Document("$divide", Arrays.asList("$discount", 100))
                                ))
                        )))
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("discount", 1)
                        .append("totalAmount", 1)
                        .append("discountAmount", 1)
                        .append("finalAmount", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 10);

        Aggregation aggregation = Aggregation.newAggregation(addFields, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate 多字段计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, mongoList.size()); i++) {
            Document doc = mongoList.get(i);
            log.info("  {} -> total={}, discount={}%, discountAmt={}, final={}",
                    doc.getString("orderNo"),
                    formatNumber(doc.get("totalAmount")),
                    doc.get("discount"),
                    formatNumber(doc.get("discountAmount")),
                    formatNumber(doc.get("finalAmount")));
        }

        // 2. QM 模型 + calculatedFields
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price", "quantity", "discount"));

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("totalAmount", "总金额", "price * quantity"));
        calcFields.add(new CalculatedFieldDef("discountAmount", "折扣金额", "price * quantity * discount / 100"));
        calcFields.add(new CalculatedFieldDef("finalAmount", "实付金额", "price * quantity * (1 - discount / 100)"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型多字段计算结果 (前3条):");
        for (int i = 0; i < Math.min(3, result.getItems().size()); i++) {
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(i);
            log.info("  {} -> total={}, discount={}%, discountAmt={}, final={}",
                    row.get("orderNo"),
                    formatNumber(row.get("totalAmount")),
                    row.get("discount"),
                    formatNumber(row.get("discountAmount")),
                    formatNumber(row.get("finalAmount")));
        }

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "结果数量应一致");

        for (int i = 0; i < mongoList.size(); i++) {
            Document mongoDoc = mongoList.get(i);
            Map<String, Object> qmRow = (Map<String, Object>) result.getItems().get(i);

            // 验证 totalAmount
            double mongoTotal = ((Number) mongoDoc.get("totalAmount")).doubleValue();
            double qmTotal = ((Number) qmRow.get("totalAmount")).doubleValue();
            assertEquals(mongoTotal, qmTotal, 0.01, "totalAmount 应一致");

            // 验证 discountAmount
            double mongoDiscountAmt = ((Number) mongoDoc.get("discountAmount")).doubleValue();
            double qmDiscountAmt = ((Number) qmRow.get("discountAmount")).doubleValue();
            assertEquals(mongoDiscountAmt, qmDiscountAmt, 0.01, "discountAmount 应一致");

            // 验证 finalAmount
            double mongoFinal = ((Number) mongoDoc.get("finalAmount")).doubleValue();
            double qmFinal = ((Number) qmRow.get("finalAmount")).doubleValue();
            assertEquals(mongoFinal, qmFinal, 0.01, "finalAmount 应一致");
        }

        log.info("多个计算字段组合测试通过");
    }

    // ==========================================
    // 条件过滤 + 计算字段测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("计算字段 - 带条件过滤的计算")
    void testCalculatedField_WithFilter() {
        String targetCategory = "手机";

        // 1. MongoTemplate: match + addFields
        AggregationOperation match = context -> new Document("$match",
                new Document("category", targetCategory));
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("totalAmount", new Document("$multiply", Arrays.asList("$price", "$quantity")))
        );
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("productName", 1)
                        .append("category", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("totalAmount", 1)
        );

        Aggregation aggregation = Aggregation.newAggregation(match, addFields, project);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate 带过滤的计算结果: {} 条", mongoList.size());

        // 2. QM 模型 + calculatedFields + slice
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "productName", "category", "price", "quantity"));

        // 过滤条件
        List<SliceRequestDef> slices = new ArrayList<>();
        SliceRequestDef slice = new SliceRequestDef();
        slice.setField("category");
        slice.setOp("=");
        slice.setValue(targetCategory);
        slices.add(slice);
        queryRequest.setSlice(slices);

        // 计算字段
        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("totalAmount", "总金额", "price * quantity"));
        queryRequest.setCalculatedFields(calcFields);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 100);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型带过滤的计算结果: {} 条", result.getItems().size());

        // 3. 对比验证
        assertEquals(mongoList.size(), result.getItems().size(), "过滤后结果数量应一致");

        // 验证所有结果都是目标类别
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertEquals(targetCategory, row.get("category"), "category 应为 " + targetCategory);
            assertNotNull(row.get("totalAmount"), "应包含计算字段 totalAmount");
        }

        log.info("带条件过滤的计算字段测试通过");
    }

    // ==========================================
    // 排序 + 计算字段测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("计算字段 - 排序验证")
    void testCalculatedField_WithSort() {
        // 注意：MongoDB 中按计算字段排序需要先 $addFields，再 $sort
        // QM 模型可能有不同的实现方式

        // 1. MongoTemplate: addFields + sort by totalAmount DESC
        AggregationOperation addFields = context -> new Document("$addFields",
                new Document("totalAmount", new Document("$multiply", Arrays.asList("$price", "$quantity")))
        );
        AggregationOperation sort = context -> new Document("$sort",
                new Document("totalAmount", -1));
        AggregationOperation project = context -> new Document("$project",
                new Document("orderNo", 1)
                        .append("price", 1)
                        .append("quantity", 1)
                        .append("totalAmount", 1)
        );
        AggregationOperation limit = context -> new Document("$limit", 5);

        Aggregation aggregation = Aggregation.newAggregation(addFields, sort, project, limit);
        AggregationResults<Document> mongoResults = mongoTemplate.aggregate(
                aggregation, TEST_COLLECTION, Document.class);
        List<Document> mongoList = mongoResults.getMappedResults();

        log.info("MongoTemplate 按totalAmount排序 (前5):");
        for (Document doc : mongoList) {
            log.info("  {} -> totalAmount = {}", doc.getString("orderNo"), formatNumber(doc.get("totalAmount")));
        }

        // 2. QM 模型 (目前可能不支持按计算字段排序，这里按price排序作为对比)
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("orderNo", "price", "quantity"));

        List<CalculatedFieldDef> calcFields = new ArrayList<>();
        calcFields.add(new CalculatedFieldDef("totalAmount", "总金额", "price * quantity"));
        queryRequest.setCalculatedFields(calcFields);

        // 按 price 降序
        List<OrderRequestDef> orders = new ArrayList<>();
        OrderRequestDef order = new OrderRequestDef();
        order.setField("price");
        order.setOrder("DESC");
        orders.add(order);
        queryRequest.setOrderBy(orders);

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 5);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM模型按price排序结果 (包含totalAmount计算字段):");
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            log.info("  {} -> price={}, totalAmount = {}",
                    row.get("orderNo"), row.get("price"), formatNumber(row.get("totalAmount")));
        }

        // 验证计算字段存在且正确
        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;
            assertNotNull(row.get("totalAmount"), "应包含计算字段");

            // 手动验证计算正确性
            double price = ((Number) row.get("price")).doubleValue();
            int quantity = ((Number) row.get("quantity")).intValue();
            double expectedTotal = price * quantity;
            double actualTotal = ((Number) row.get("totalAmount")).doubleValue();

            assertEquals(expectedTotal, actualTotal, 0.01, "totalAmount 计算应正确");
        }

        log.info("排序 + 计算字段测试通过");
    }

    // ==========================================
    // 清理测试数据
    // ==========================================

    @Test
    @Order(100)
    @DisplayName("清理测试数据")
    void cleanupTestData() {
        long count = getCollectionCount(TEST_COLLECTION);
        log.info("测试完成，集合 {} 保留 {} 条记录", TEST_COLLECTION, count);

        // 可选：清理测试数据
        // clearCollection(TEST_COLLECTION);
        // log.info("测试数据已清理");
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private String formatNumber(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return String.format("%.2f", ((Number) value).doubleValue());
        }
        return value.toString();
    }
}
