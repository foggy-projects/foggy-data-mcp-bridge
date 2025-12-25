package com.foggyframework.dataset.mongo;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 查询 Key
 * 封装 MongoDB 查询所需的所有参数
 */
public final class MongoKey {
    public Object sql;
    public Bson sort;
    public final int start;
    public final int limit;
    public String setName;
    public MongoTemplate template;
    public MongoCollection<Document> set;

    public MongoKey(MongoTemplate template, String setName, MongoCollection<Document> set, Object sq, int start,
                    int limit) {
        super();
        this.sql = sq;
        this.start = start;
        this.limit = limit;
        this.set = set;
        this.template = template;
        this.setName = setName;
    }
}
