package com.foggyframework.dataset.db.model.vector;

import com.foggyframework.dataset.db.model.impl.vector.VectorDbConfig;
import com.foggyframework.dataset.db.model.impl.vector.VectorTableModelImpl;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorTableModelImpl 单元测试
 *
 * <p>测试向量表模型实现类的基本功能</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("VectorTableModelImpl 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorTableModelImplTest {

    // ==========================================
    // 构造函数测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("构造函数 - 默认构造")
    void testDefaultConstructor() {
        VectorTableModelImpl model = new VectorTableModelImpl();

        assertNull(model.getFScript());
        assertNull(model.getVectorDbConfig());
        assertNull(model.getCollectionName());
        assertNull(model.getVectorFieldName());
        assertEquals(0, model.getVectorDimensions());
        assertEquals("cosine", model.getMetricType());

        log.info("默认构造函数测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("构造函数 - 带配置构造")
    void testConstructorWithConfig() {
        VectorDbConfig config = VectorDbConfig.builder()
                .host("localhost")
                .port(19530)
                .build();

        VectorTableModelImpl model = new VectorTableModelImpl(config, null);

        assertNotNull(model.getVectorDbConfig());
        assertEquals("localhost", model.getVectorDbConfig().getHost());
        assertEquals(19530, model.getVectorDbConfig().getPort());

        log.info("带配置构造函数测试通过");
    }

    // ==========================================
    // 属性设置测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("属性设置 - 集合名称")
    void testSetCollectionName() {
        VectorTableModelImpl model = new VectorTableModelImpl();
        model.setCollectionName("documents");

        assertEquals("documents", model.getCollectionName());

        log.info("集合名称设置测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("属性设置 - 向量字段名")
    void testSetVectorFieldName() {
        VectorTableModelImpl model = new VectorTableModelImpl();
        model.setVectorFieldName("embedding");

        assertEquals("embedding", model.getVectorFieldName());

        log.info("向量字段名设置测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("属性设置 - 向量维度")
    void testSetVectorDimensions() {
        VectorTableModelImpl model = new VectorTableModelImpl();

        // 测试不同维度
        model.setVectorDimensions(768);
        assertEquals(768, model.getVectorDimensions());

        model.setVectorDimensions(1536);
        assertEquals(1536, model.getVectorDimensions());

        model.setVectorDimensions(3072);
        assertEquals(3072, model.getVectorDimensions());

        log.info("向量维度设置测试通过");
    }

    @Test
    @Order(13)
    @DisplayName("属性设置 - 度量类型")
    void testSetMetricType() {
        VectorTableModelImpl model = new VectorTableModelImpl();

        // 默认是 cosine
        assertEquals("cosine", model.getMetricType());

        // 设置为其他类型
        model.setMetricType("euclidean");
        assertEquals("euclidean", model.getMetricType());

        model.setMetricType("dotProduct");
        assertEquals("dotProduct", model.getMetricType());

        model.setMetricType("IP");
        assertEquals("IP", model.getMetricType());

        log.info("度量类型设置测试通过");
    }

    // ==========================================
    // 模型类型测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("模型类型 - 设置为 vector")
    void testModelType() {
        VectorTableModelImpl model = new VectorTableModelImpl();
        model.setModelType(DbModelType.vector);

        assertEquals(DbModelType.vector, model.getModelType());

        log.info("模型类型设置测试通过");
    }

    // ==========================================
    // 完整配置测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("完整配置 - 文档搜索模型")
    void testDocumentSearchModel() {
        VectorDbConfig config = VectorDbConfig.builder()
                .type("milvus")
                .host("milvus.example.com")
                .port(19530)
                .database("production")
                .embedding(VectorDbConfig.EmbeddingConfig.builder()
                        .type("openai")
                        .model("text-embedding-3-small")
                        .dimensions(1536)
                        .build())
                .build();

        VectorTableModelImpl model = new VectorTableModelImpl(config, null);
        model.setCollectionName("documents");
        model.setVectorFieldName("content_embedding");
        model.setVectorDimensions(1536);
        model.setMetricType("cosine");
        model.setModelType(DbModelType.vector);
        model.setName("DocumentSearchModel");
        model.setCaption("文档搜索");

        assertEquals("documents", model.getCollectionName());
        assertEquals("content_embedding", model.getVectorFieldName());
        assertEquals(1536, model.getVectorDimensions());
        assertEquals("cosine", model.getMetricType());
        assertEquals(DbModelType.vector, model.getModelType());
        assertEquals("DocumentSearchModel", model.getName());
        assertEquals("文档搜索", model.getCaption());

        log.info("完整文档搜索模型配置测试通过");
    }

    @Test
    @Order(31)
    @DisplayName("完整配置 - 商品搜索模型")
    void testProductSearchModel() {
        VectorDbConfig config = VectorDbConfig.builder()
                .type("milvus")
                .host("localhost")
                .port(19530)
                .embedding(VectorDbConfig.EmbeddingConfig.builder()
                        .type("ollama")
                        .baseUrl("http://localhost:11434/v1")
                        .model("nomic-embed-text")
                        .dimensions(768)
                        .build())
                .build();

        VectorTableModelImpl model = new VectorTableModelImpl(config, null);
        model.setCollectionName("products");
        model.setVectorFieldName("description_embedding");
        model.setVectorDimensions(768);
        model.setMetricType("euclidean");
        model.setModelType(DbModelType.vector);
        model.setName("ProductSearchModel");
        model.setCaption("商品搜索");

        assertEquals("products", model.getCollectionName());
        assertEquals("description_embedding", model.getVectorFieldName());
        assertEquals(768, model.getVectorDimensions());
        assertEquals("euclidean", model.getMetricType());
        assertEquals("ollama", config.getEmbedding().getType());

        log.info("完整商品搜索模型配置测试通过");
    }

    // ==========================================
    // 边界条件测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("边界条件 - 空字符串属性")
    void testEmptyStringProperties() {
        VectorTableModelImpl model = new VectorTableModelImpl();

        model.setCollectionName("");
        model.setVectorFieldName("");
        model.setMetricType("");

        assertEquals("", model.getCollectionName());
        assertEquals("", model.getVectorFieldName());
        assertEquals("", model.getMetricType());

        log.info("空字符串属性测试通过");
    }

    @Test
    @Order(41)
    @DisplayName("边界条件 - 零维度")
    void testZeroDimensions() {
        VectorTableModelImpl model = new VectorTableModelImpl();
        model.setVectorDimensions(0);

        assertEquals(0, model.getVectorDimensions());

        log.info("零维度测试通过");
    }

    @Test
    @Order(42)
    @DisplayName("边界条件 - 特殊集合名")
    void testSpecialCollectionNames() {
        VectorTableModelImpl model = new VectorTableModelImpl();

        // 中文集合名
        model.setCollectionName("文档集合");
        assertEquals("文档集合", model.getCollectionName());

        // 带下划线
        model.setCollectionName("product_embeddings");
        assertEquals("product_embeddings", model.getCollectionName());

        // 带数字
        model.setCollectionName("docs_v2");
        assertEquals("docs_v2", model.getCollectionName());

        log.info("特殊集合名测试通过");
    }
}
