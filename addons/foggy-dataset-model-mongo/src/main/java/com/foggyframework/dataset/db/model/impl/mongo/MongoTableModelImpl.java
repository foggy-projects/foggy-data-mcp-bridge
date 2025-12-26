package com.foggyframework.dataset.db.model.impl.mongo;

import com.foggyframework.dataset.db.model.impl.model.TableModelSupport;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import org.springframework.data.mongodb.core.MongoTemplate;

@Getter
public class MongoTableModelImpl extends TableModelSupport {
    Fsscript fScript;

    MongoTemplate mongoTemplate;

    public MongoTableModelImpl() {

    }

    public MongoTableModelImpl(MongoTemplate mongoTemplate, Fsscript fScript) {
        this.mongoTemplate = mongoTemplate;
        this.fScript = fScript;
    }

    
}
