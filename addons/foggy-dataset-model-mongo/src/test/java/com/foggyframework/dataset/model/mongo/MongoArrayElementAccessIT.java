package com.foggyframework.dataset.model.mongo;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import jakarta.annotation.Resource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB 数组元素访问测试
 *
 * <p>验证 TM 中定义 column 为 "location.coordinates.0" / "location.coordinates.1"
 * 时，通过 QM 查询能正确返回数组中对应索引的值。</p>
 *
 * <p>BUG 背景：Spring Data MongoDB 的 ProjectionOperation 会将
 * "location.coordinates.1" 转为无效的 "$location.coordinates[1]"，
 * 导致查询结果为空数组而非期望的数值。
 * 修复方案：使用 $arrayElemAt 操作符。</p>
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("MongoDB数组元素访问测试 - $arrayElemAt")
class MongoArrayElementAccessIT extends MongoTestSupport {

    @Resource
    private JdbcService jdbcService;

    private static final String QUERY_MODEL_NAME = "GeoStationQueryModel";
    private static final String COLLECTION_NAME = "geo_station_test";

    /**
     * 测试数据：经纬度坐标
     */
    private static final double[][] COORDINATES = {
            {120.381405, 36.088746},  // 青岛
            {121.473701, 31.230416},  // 上海
            {116.407526, 39.904030},  // 北京
            {113.264385, 23.129110},  // 广州
            {104.065735, 30.659462},  // 成都
    };

    private static final String[] STATION_NAMES = {"青岛站", "上海站", "北京站", "广州站", "成都站"};
    private static final String[] CITIES = {"青岛", "上海", "北京", "广州", "成都"};

    // ==========================================
    // 测试数据准备
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("清空并初始化GeoJSON测试数据")
    void setupTestData() {
        clearCollection(COLLECTION_NAME);

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < COORDINATES.length; i++) {
            Document doc = new Document();
            doc.put("name", STATION_NAMES[i]);
            doc.put("city", CITIES[i]);
            doc.put("status", "ACTIVE");
            doc.put("location", new Document()
                    .append("type", "Point")
                    .append("coordinates", Arrays.asList(COORDINATES[i][0], COORDINATES[i][1]))
            );
            documents.add(doc);
        }

        insertDocuments(COLLECTION_NAME, documents);

        long count = getCollectionCount(COLLECTION_NAME);
        assertEquals(COORDINATES.length, count, "文档数量应与插入数量一致");
        log.info("测试数据初始化完成，共插入 {} 条GeoJSON记录", count);
    }

    // ==========================================
    // 核心测试：数组元素访问
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("核心验证 - QM查询lat/lng应返回正确数值（非空数组）")
    void testArrayElementAccess_LatLng() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("id", "name", "lng", "lat"));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        log.info("QM查询返回 {} 条记录", result.getItems().size());
        assertEquals(COORDINATES.length, result.getItems().size(), "返回条数应一致");

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;

            Object lngValue = row.get("lng");
            Object latValue = row.get("lat");

            log.info("站点: {}, lng={} (type={}), lat={} (type={})",
                    row.get("name"), lngValue,
                    lngValue == null ? "null" : lngValue.getClass().getSimpleName(),
                    latValue,
                    latValue == null ? "null" : latValue.getClass().getSimpleName());

            // 核心断言：值不能为 null
            assertNotNull(lngValue, "lng 不应为 null");
            assertNotNull(latValue, "lat 不应为 null");

            // 核心断言：值必须是数值类型，而不是空数组 []
            assertFalse(lngValue instanceof List, "lng 不应是数组（BUG表现：返回[]）");
            assertFalse(latValue instanceof List, "lat 不应是数组（BUG表现：返回[]）");

            assertTrue(lngValue instanceof Number, "lng 应为数值类型, 实际: " + lngValue.getClass());
            assertTrue(latValue instanceof Number, "lat 应为数值类型, 实际: " + latValue.getClass());
        }
    }

    @Test
    @Order(11)
    @DisplayName("数值精度验证 - QM查询的lat/lng应与原始数据一致")
    void testArrayElementAccess_ValueAccuracy() {
        // 按城市过滤，逐个验证精确值
        for (int i = 0; i < COORDINATES.length; i++) {
            String city = CITIES[i];
            double expectedLng = COORDINATES[i][0];
            double expectedLat = COORDINATES[i][1];

            DbQueryRequestDef queryRequest = new DbQueryRequestDef();
            queryRequest.setQueryModel(QUERY_MODEL_NAME);
            queryRequest.setColumns(Arrays.asList("name", "city", "lng", "lat"));

            List<SliceRequestDef> slices = new ArrayList<>();
            SliceRequestDef slice = new SliceRequestDef();
            slice.setField("city");
            slice.setOp("=");
            slice.setValue(city);
            slices.add(slice);
            queryRequest.setSlice(slices);

            PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 10);
            PagingResultImpl result = jdbcService.queryModelData(form);

            assertEquals(1, result.getItems().size(), city + "应返回1条记录");
            Map<String, Object> row = (Map<String, Object>) result.getItems().get(0);

            Number lng = (Number) row.get("lng");
            Number lat = (Number) row.get("lat");

            assertEquals(expectedLng, lng.doubleValue(), 0.000001,
                    city + " 经度应匹配: 期望=" + expectedLng);
            assertEquals(expectedLat, lat.doubleValue(), 0.000001,
                    city + " 纬度应匹配: 期望=" + expectedLat);

            log.info("{}: lng={}, lat={} ✓", city, lng, lat);
        }
    }

    @Test
    @Order(12)
    @DisplayName("对比验证 - QM查询结果与直接聚合查询($arrayElemAt)一致")
    void testArrayElementAccess_CompareWithDirectQuery() {
        // 1. 直接用 MongoTemplate 聚合查询（$arrayElemAt 方式）
        Document projectDoc = new Document()
                .append("name", 1)
                .append("lng", new Document("$arrayElemAt", Arrays.asList("$location.coordinates", 0)))
                .append("lat", new Document("$arrayElemAt", Arrays.asList("$location.coordinates", 1)));

        AggregationResults<Document> directResults = mongoTemplate.aggregate(
                Aggregation.newAggregation(
                        ctx -> new Document("$project", projectDoc)
                ),
                COLLECTION_NAME,
                Document.class
        );

        List<Document> directList = directResults.getMappedResults();
        log.info("直接聚合查询返回 {} 条", directList.size());

        // 2. 通过 QM 模型查询
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        queryRequest.setColumns(Arrays.asList("name", "lng", "lat"));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl qmResult = jdbcService.queryModelData(form);

        // 3. 逐条对比
        assertEquals(directList.size(), qmResult.getItems().size(), "记录数应一致");

        // 按 name 建立映射进行对比（避免排序差异）
        Map<String, Document> directMap = new HashMap<>();
        for (Document doc : directList) {
            directMap.put(doc.getString("name"), doc);
        }

        for (Object item : qmResult.getItems()) {
            Map<String, Object> qmRow = (Map<String, Object>) item;
            String name = (String) qmRow.get("name");
            Document directDoc = directMap.get(name);

            assertNotNull(directDoc, "直接查询中应包含站点: " + name);

            Number qmLng = (Number) qmRow.get("lng");
            Number qmLat = (Number) qmRow.get("lat");
            Number directLng = (Number) directDoc.get("lng");
            Number directLat = (Number) directDoc.get("lat");

            assertEquals(directLng.doubleValue(), qmLng.doubleValue(), 0.000001,
                    name + " 经度应与直接查询一致");
            assertEquals(directLat.doubleValue(), qmLat.doubleValue(), 0.000001,
                    name + " 纬度应与直接查询一致");

            log.info("{}: QM({}, {}) == Direct({}, {}) ✓",
                    name, qmLng, qmLat, directLng, directLat);
        }
    }

    @Test
    @Order(13)
    @DisplayName("混合查询 - 同时查询普通字段和数组元素字段")
    void testArrayElementAccess_MixedFields() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(QUERY_MODEL_NAME);
        // 混合查询：普通字段 + 嵌套字段 + 数组元素字段 + 数组字段
        queryRequest.setColumns(Arrays.asList("id", "name", "city", "locationType", "coordinates", "lng", "lat"));

        PagingRequest<DbQueryRequestDef> form = PagingRequest.buildPagingRequest(queryRequest, 50);
        PagingResultImpl result = jdbcService.queryModelData(form);

        assertEquals(COORDINATES.length, result.getItems().size());

        for (Object item : result.getItems()) {
            Map<String, Object> row = (Map<String, Object>) item;

            // 普通字段
            assertNotNull(row.get("id"), "id 不应为 null");
            assertNotNull(row.get("name"), "name 不应为 null");
            assertNotNull(row.get("city"), "city 不应为 null");

            // 嵌套文档字段
            assertEquals("Point", row.get("locationType"), "locationType 应为 Point");

            // 数组字段（整个数组）
            Object coords = row.get("coordinates");
            assertNotNull(coords, "coordinates 不应为 null");
            assertTrue(coords instanceof List, "coordinates 应为数组");
            assertEquals(2, ((List<?>) coords).size(), "coordinates 应有2个元素");

            // 数组元素字段
            assertTrue(row.get("lng") instanceof Number, "lng 应为数值");
            assertTrue(row.get("lat") instanceof Number, "lat 应为数值");

            log.info("站点={}, city={}, type={}, coords={}, lng={}, lat={}",
                    row.get("name"), row.get("city"), row.get("locationType"),
                    row.get("coordinates"), row.get("lng"), row.get("lat"));
        }
    }

    // ==========================================
    // 清理
    // ==========================================

    @Test
    @Order(99)
    @DisplayName("清理GeoJSON测试数据")
    void cleanupTestData() {
        clearCollection(COLLECTION_NAME);
        log.info("测试数据已清理");
    }
}
