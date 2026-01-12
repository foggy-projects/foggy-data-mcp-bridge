package com.foggyframework.dataset.db.model.impl.vector;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.def.DbModelDef;
import com.foggyframework.dataset.db.model.def.measure.DbMeasureDef;
import com.foggyframework.dataset.db.model.def.property.DbPropertyDef;
import com.foggyframework.dataset.db.model.def.query.DbQueryModelDef;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.impl.LoaderSupport;
import com.foggyframework.dataset.db.model.spi.DbModelType;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 向量模型加载器实现
 * <p>
 * 仅当 Milvus SDK 存在时才会加载此 Bean。
 * 向量模型专注于相似度检索，不支持聚合等复杂特性。
 */
@Service
@Slf4j
@ConditionalOnClass(name = "io.milvus.v2.client.MilvusClientV2")
public class TmVectorModelLoaderImpl extends LoaderSupport implements TableModelLoader, QueryModelBuilder {

    @Value("${foggy.vector.host:localhost}")
    private String vectorHost;

    @Value("${foggy.vector.port:19530}")
    private int vectorPort;

    @Value("${foggy.vector.database:}")
    private String vectorDatabase;

    @Value("${foggy.vector.username:}")
    private String vectorUsername;

    @Value("${foggy.vector.password:}")
    private String vectorPassword;

    @Value("${foggy.vector.embedding.type:openai}")
    private String embeddingType;

    @Value("${foggy.vector.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${foggy.vector.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${foggy.vector.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${foggy.vector.embedding.dimensions:1536}")
    private int embeddingDimensions;

    public TmVectorModelLoaderImpl(SystemBundlesContext systemBundlesContext, FileFsscriptLoader fileFsscriptLoader) {
        super(systemBundlesContext, fileFsscriptLoader);
    }

    @Override
    public TableModel load(Fsscript fScript, DbModelDef def, Bundle bundle) {
        // 向量模型不支持维度（维度是 JDBC 模型的概念）
        if (def.getDimensions() != null && !def.getDimensions().isEmpty()) {
            throw new RuntimeException("Vector model does not support dimensions");
        }

        // 必须指定集合名称
        if (StringUtils.isEmpty(def.getTableName())) {
            throw new RuntimeException("Vector model must set tableName (collection name)");
        }

        // 构建虚拟 viewSql（兼容 JDBC 模型体系）
        if (StringUtils.isEmpty(def.getViewSql())) {
            StringBuilder sb = new StringBuilder("select ");
            Set<String> columns = new HashSet<>();

            String vectorField = null;
            int vectorDimensions = embeddingDimensions;

            if (def.getProperties() != null) {
                for (DbPropertyDef property : def.getProperties()) {
                    RX.hasText(property.getColumn(), "Property column name cannot be empty: " + def.getName());

                    if (!columns.contains(property.getColumn())) {
                        appendColumn(sb, property.getColumn());
                        columns.add(property.getColumn());
                    }

                    // 检查是否为向量字段
                    if ("VECTOR".equalsIgnoreCase(property.getType())) {
                        vectorField = property.getColumn();
                        if (property.getDimensions() != null) {
                            vectorDimensions = property.getDimensions();
                        }
                    }

                    String name = fixName(property.getColumn(), property.getName());
                    property.setName(name);

                    String alias = fixName(property.getColumn(), property.getAlias());
                    property.setAlias(alias);
                }
            }

            if (def.getMeasures() != null) {
                for (DbMeasureDef measure : def.getMeasures()) {
                    RX.hasText(measure.getColumn(), "Measure column name cannot be empty: " + def.getName());

                    if (!columns.contains(measure.getColumn())) {
                        appendColumn(sb, measure.getColumn());
                        columns.add(measure.getColumn());
                    }

                    String name = fixName(measure.getColumn(), measure.getName());
                    measure.setName(name);

                    String alias = fixName(measure.getColumn(), measure.getAlias());
                    measure.setAlias(alias);
                }
            }

            sb.append("0 as _id from dual");
            def.setViewSql(sb.toString());

            log.debug("Built viewSql for vector model {}: {}", def.getName(), def.getViewSql());
        }

        // 构建向量数据库配置
        VectorDbConfig vectorDbConfig = buildVectorDbConfig(def);

        // 创建向量表模型
        VectorTableModelImpl vectorModel = new VectorTableModelImpl(vectorDbConfig, fScript);
        def.apply(vectorModel);

        // 设置模型类型为 vector
        vectorModel.setModelType(DbModelType.vector);
        vectorModel.setCollectionName(def.getTableName());

        // 查找向量字段
        String vectorFieldName = findVectorField(def);
        if (vectorFieldName != null) {
            vectorModel.setVectorFieldName(vectorFieldName);
        }

        log.debug("Loaded vector model: {} -> collection: {}", def.getName(), def.getTableName());

        return vectorModel;
    }

    @Override
    public String getTypeName() {
        return "vector";
    }

    @Override
    public QueryModelSupport build(DbQueryModelDef queryModelDef, Fsscript fsscript) {
        log.debug("TmVectorModelLoaderImpl.build() called, QM: {}", queryModelDef.getName());

        List<TableModel> modelList = queryModelDef.getParsedModels();

        if (modelList == null || modelList.isEmpty()) {
            log.debug("Model list is empty, skipping");
            return null;
        }

        // 获取主表模型，检查是否为向量模型
        TableModel firstModel = modelList.get(0);
        log.debug("First model type: {}", firstModel.getClass().getName());

        if (firstModel instanceof QueryModelSupport.JdbcModelDx dx) {
            firstModel = dx.getDelegate();
            log.debug("Unwrapped model type: {}", firstModel.getClass().getName());
        }

        VectorTableModelImpl mainTm = firstModel.getDecorate(VectorTableModelImpl.class);
        if (mainTm == null) {
            log.debug("Not a Vector model, skipping");
            return null;
        }

        log.debug("Detected Vector model, building VectorQueryModelImpl");

        // 向量模型不支持 JOIN
        if (modelList.size() > 1) {
            throw RX.throwB("Vector model does not support JOIN");
        }

        VectorQueryModelImpl qm = new VectorQueryModelImpl(modelList, fsscript, mainTm.getVectorDbConfig());
        qm.setVectorFieldName(mainTm.getVectorFieldName());
        qm.setCollectionName(mainTm.getCollectionName());

        queryModelDef.apply(qm);

        log.debug("Vector QueryModel built successfully: {}", qm.getName());
        return qm;
    }

    /**
     * 构建向量数据库配置
     */
    private VectorDbConfig buildVectorDbConfig(DbModelDef def) {
        VectorDbConfig.VectorDbConfigBuilder builder = VectorDbConfig.builder()
                .type("milvus")
                .host(vectorHost)
                .port(vectorPort);

        if (StringUtils.isNotEmpty(vectorDatabase)) {
            builder.database(vectorDatabase);
        }
        if (StringUtils.isNotEmpty(vectorUsername)) {
            builder.username(vectorUsername);
            builder.password(vectorPassword);
        }

        // 从模型定义中获取配置（如果有）
        if (def.getVectorConfig() != null) {
            Object config = def.getVectorConfig();
            if (config instanceof VectorDbConfig) {
                return (VectorDbConfig) config;
            }
        }

        // 配置 Embedding 服务
        VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                .type(embeddingType)
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .model(embeddingModel)
                .dimensions(embeddingDimensions)
                .build();

        builder.embedding(embeddingConfig);

        return builder.build();
    }

    /**
     * 查找向量字段
     */
    private String findVectorField(DbModelDef def) {
        if (def.getProperties() != null) {
            for (DbPropertyDef property : def.getProperties()) {
                if ("VECTOR".equalsIgnoreCase(property.getType())) {
                    return property.getColumn();
                }
            }
        }
        return null;
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

    private void appendColumn(StringBuilder sb, String column) {
        sb.append("0 `").append(column).append("`,");
    }
}
