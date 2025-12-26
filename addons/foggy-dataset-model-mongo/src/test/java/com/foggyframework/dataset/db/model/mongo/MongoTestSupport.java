package com.foggyframework.dataset.db.model.mongo;

import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * MongoDB 测试基类
 *
 * <p>提供 MongoDB 测试的公共支持方法</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
@ActiveProfiles({"docker"})
public abstract class MongoTestSupport {

    @Resource
    protected MongoTemplate mongoTemplate;

    /**
     * MCP 审计日志集合名称
     */
    protected static final String MCP_AUDIT_COLLECTION = "mcp_tool_audit_log";

    /**
     * 清空集合
     *
     * @param collectionName 集合名称
     */
    protected void clearCollection(String collectionName) {
        if (mongoTemplate.collectionExists(collectionName)) {
            mongoTemplate.dropCollection(collectionName);
            log.info("已清空集合: {}", collectionName);
        }
    }

    /**
     * 插入文档
     *
     * @param collectionName 集合名称
     * @param document       文档
     */
    protected void insertDocument(String collectionName, Document document) {
        mongoTemplate.insert(document, collectionName);
        log.debug("已插入文档到 {}: {}", collectionName, document.get("_id"));
    }

    /**
     * 批量插入文档
     *
     * @param collectionName 集合名称
     * @param documents      文档列表
     */
    protected void insertDocuments(String collectionName, List<Document> documents) {
        mongoTemplate.insert(documents, collectionName);
        log.info("已批量插入 {} 条文档到 {}", documents.size(), collectionName);
    }

    /**
     * 查询所有文档
     *
     * @param collectionName 集合名称
     * @return 文档列表
     */
    protected List<Document> findAll(String collectionName) {
        return mongoTemplate.findAll(Document.class, collectionName);
    }

    /**
     * 按条件查询文档
     *
     * @param collectionName 集合名称
     * @param query          查询条件
     * @return 文档列表
     */
    protected List<Document> find(String collectionName, Query query) {
        return mongoTemplate.find(query, Document.class, collectionName);
    }

    /**
     * 获取集合文档数量
     *
     * @param collectionName 集合名称
     * @return 文档数量
     */
    protected long getCollectionCount(String collectionName) {
        return mongoTemplate.getCollection(collectionName).countDocuments();
    }

    /**
     * 打印查询结果
     *
     * @param documents 文档列表
     */
    protected void printDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            log.info("查询结果为空");
            return;
        }

        log.info("查询结果数量: {}", documents.size());
        for (int i = 0; i < Math.min(10, documents.size()); i++) {
            log.info("Document {}: {}", i + 1, documents.get(i).toJson());
        }

        if (documents.size() > 10) {
            log.info("... 还有 {} 条文档未显示", documents.size() - 10);
        }
    }

    /**
     * 打印文档详情
     *
     * @param document 文档
     * @param description 描述
     */
    protected void printDocument(Document document, String description) {
        log.info("========== {} ==========", description);
        if (document != null) {
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                log.info("  {}: {}", entry.getKey(), entry.getValue());
            }
        } else {
            log.info("  (null)");
        }
        log.info("==========================================");
    }
}
