package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

public interface JdbcModelLoader {
    void clearAll();

    JdbcModel load(String s);

    JdbcModel load(DataSource dataSource, Fsscript fScript, JdbcModelDef def, Bundle bundle, MongoTemplate defMongoTemplate);

    /**
     * 呃，加这个是因为ai经常直接使用getJdbcModel来获取模型，而不是找load
     *
     * @param s
     * @return
     */
    default JdbcModel getJdbcModel(String s) {
        return load(s);
    }
}
