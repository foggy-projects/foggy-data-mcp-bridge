package com.foggyframework.dataset.jdbc.model.impl.mongo;

import com.foggyframework.dataset.jdbc.model.impl.model.JdbcModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import org.springframework.data.mongodb.core.MongoTemplate;

@Getter
public class MongoTableModelImpl extends JdbcModelSupport {
    Fsscript fScript;

    MongoTemplate mongoTemplate;

    public MongoTableModelImpl() {

    }

    public MongoTableModelImpl(MongoTemplate mongoTemplate, Fsscript fScript) {
        this.mongoTemplate = mongoTemplate;
        this.fScript = fScript;
    }

    
}
