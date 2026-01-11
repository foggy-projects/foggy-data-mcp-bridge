package com.foggyframework.dataset.vector;

import lombok.Data;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * 向量数据库查询 Key
 * 类似于 MongoKey
 */
@Data
public class VectorKey {
    public VectorStore vectorStore;
    public String query;
    public int topK;
    public double threshold;
    public int start;
    public int limit;

    public VectorKey(VectorStore vectorStore, String query, int topK, double threshold, int start, int limit) {
        this.vectorStore = vectorStore;
        this.query = query;
        this.topK = topK;
        this.threshold = threshold;
        this.start = start;
        this.limit = limit;
    }
}
