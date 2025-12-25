package com.foggyframework.dataset.jdbc.model.impl.mongo;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.def.JdbcModelDef;
import com.foggyframework.dataset.jdbc.model.def.measure.JdbcMeasureDef;
import com.foggyframework.dataset.jdbc.model.def.property.JdbcPropertyDef;
import com.foggyframework.dataset.jdbc.model.engine.mongo.MongoModelLoader;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModel;
import com.foggyframework.dataset.jdbc.model.spi.JdbcModelLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.Set;

/**
 * MongoDB 模型加载器实现
 *
 * <p>仅当 MongoDB 客户端存在时才会加载此 Bean。
 * 目前 mongo 不考虑维度的实现。
 */
@Service
@Slf4j
public class TmMongoModelLoaderImpl implements MongoModelLoader {

    @Resource
    @Lazy
    JdbcModelLoader jdbcModelLoader;

    @Resource
    DataSource dataSource;

    @Resource
    MongoTemplate mongoTemplate;

    @Resource
    MongoClient mongoClient;

    @Override
    public JdbcModel load(Fsscript fScript, JdbcModelDef def, Bundle bundle) {
        if (def.getDimensions() != null && def.getDimensions().size() > 0) {
            throw new RuntimeException("mongo model not support dimension");
        }
        if (StringUtils.isEmpty(def.getTableName())) {
            throw new RuntimeException("mongo model must set tableName");
        }
        if (StringUtils.isNotEmpty(def.getViewSql())) {
            log.debug("传入了viewSql?使用它" + def.getViewSql());
        } else {
            boolean hasId = false;
            StringBuilder sb = new StringBuilder("select ");
            Set<String> columns = new HashSet<>();
            if (def.getProperties() != null) {
                for (JdbcPropertyDef property : def.getProperties()) {
                    RX.hasText(property.getColumn(), "Property列名不能为空:" + def.getName());
                    if(!columns.contains(property.getColumn())) {
                        appendColumn(sb, property.getColumn(), property.getType());
                        columns.add(property.getColumn());
                    }

                    String name = fixName(property.getColumn(), property.getName());
                    property.setName(name);

                    String alias = fixName(property.getColumn(), property.getAlias());
                    property.setAlias(alias);
                    if ("_id".equals(property.getColumn())) {
                        hasId = true;
                    }
                }
            }
            if (def.getMeasures() != null) {
                for (JdbcMeasureDef measure : def.getMeasures()) {
                    RX.hasText(measure.getColumn(), "Measure列名不能为空:" + def.getName());
                    if(!columns.contains(measure.getColumn())) {
                        appendColumn(sb, measure.getColumn(), measure.getType());
                        columns.add(measure.getColumn());
                    }

                    String name = fixName(measure.getColumn(), measure.getName());
                    measure.setName(name);
                    String alias = fixName(measure.getColumn(), measure.getAlias());
                    measure.setAlias(alias);
                }
            }
            if (!hasId) {
                sb.append("0 as _id from dual");
            } else {
                sb.append("0 as _id2 from dual");
            }


            def.setViewSql(sb.toString());
            log.debug("为mongo模型" + def.getName() + "构建了viewSql:" + def.getViewSql());
        }
        MongoTemplate defMongoTemplate = mongoTemplate;
        if (!StringUtils.isEmpty(def.getSchema())) {
            defMongoTemplate = new MongoTemplate(mongoClient, def.getSchema());
        }
        //TODO 呃，如果自定义了def，那就用自定义的~但这里有个问题，不支持切schema，后续再说吧
        if (def.getMongoTemplate() != null) {
            defMongoTemplate = def.getMongoTemplate();
        }
        JdbcModel model = jdbcModelLoader.load(dataSource, fScript, def, bundle, defMongoTemplate);

        return model;
    }

    private String fixName(String column, String name) {
        if (StringUtils.isEmpty(name)) {
            if (column.startsWith("_")) {
                return column;
            }
            column = column.replaceAll("\\.", "_");
            name = StringUtils.to(column);
        }
        return name;
    }

    private void appendColumn(StringBuilder sb, String column, String type) {
        sb.append(0).append(" `").append(column).append("`,");
    }

}
