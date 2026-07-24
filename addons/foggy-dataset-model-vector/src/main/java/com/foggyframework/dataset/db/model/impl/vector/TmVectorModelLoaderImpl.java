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
import com.foggyframework.dataset.db.model.spi.DetachedQueryModelBuilderFactory;
import com.foggyframework.dataset.db.model.spi.QueryModelBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.TableModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 向量模型加载器实现
 * <p>
 * 仅当 Milvus SDK 存在时才会加载此 Bean。
 * 向量模型专注于相似度检索，不支持聚合等复杂特性。
 * <p>
 * v2.0 新增元数据自动发现功能：
 * <ul>
 *   <li>自动从 Milvus 获取向量字段的 dimension、metric、indexType</li>
 *   <li>TM 文件中仅需声明 type: 'VECTOR'，无需手动配置元数据</li>
 *   <li>支持配置优先级：TM 显式配置 > Milvus 自动发现 > Spring 配置默认值</li>
 * </ul>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
public class TmVectorModelLoaderImpl extends LoaderSupport implements TableModelLoader,
        QueryModelBuilder, DetachedQueryModelBuilderFactory {

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

    @Value("${foggy.vector.auto-discovery:true}")
    private boolean autoDiscovery;

    @Value("${foggy.vector.pool-size:5}")
    private int poolSize;

    @Value("${foggy.vector.pool-max-wait-ms:5000}")
    private long poolMaxWaitMs;

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

        // 构建向量数据库配置
        VectorDbConfig vectorDbConfig = buildVectorDbConfig(def);

        // 元数据自动发现
        VectorFieldMetadata discoveredMetadata = null;
        String vectorFieldName = findVectorField(def);

        if (vectorDbConfig.isAutoDiscovery() && vectorFieldName != null) {
            discoveredMetadata = discoverVectorFieldMetadata(vectorDbConfig, def.getTableName(), vectorFieldName);
            if (discoveredMetadata != null) {
                log.info("Auto-discovered vector field metadata: collection={}, field={}, dimension={}, metric={}, indexType={}",
                        def.getTableName(), vectorFieldName,
                        discoveredMetadata.getDimension(),
                        discoveredMetadata.getMetricType(),
                        discoveredMetadata.getIndexType());

                // 更新 TM 中向量字段的元数据（如果未显式指定）
                updateVectorFieldFromMetadata(def, discoveredMetadata);
            }
        }

        // 构建虚拟 viewSql（兼容 JDBC 模型体系）
        if (StringUtils.isEmpty(def.getViewSql())) {
            StringBuilder sb = new StringBuilder("select ");
            Set<String> columns = new HashSet<>();

            if (def.getProperties() != null) {
                for (DbPropertyDef property : def.getProperties()) {
                    RX.hasText(property.getColumn(), "Property column name cannot be empty: " + def.getName());

                    if (!columns.contains(property.getColumn())) {
                        appendColumn(sb, property.getColumn());
                        columns.add(property.getColumn());
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

        // 创建向量表模型
        VectorTableModelImpl vectorModel = new VectorTableModelImpl(vectorDbConfig, fScript);
        def.apply(vectorModel);

        // 设置模型类型为 vector
        vectorModel.setModelType(DbModelType.vector);
        vectorModel.setCollectionName(def.getTableName());

        // 设置向量字段名
        if (vectorFieldName != null) {
            vectorModel.setVectorFieldName(vectorFieldName);
        }

        // 存储发现的元数据
        if (discoveredMetadata != null) {
            vectorModel.setVectorFieldMetadata(discoveredMetadata);
        }

        log.debug("Loaded vector model: {} -> collection: {}, autoDiscovery: {}",
                def.getName(), def.getTableName(), vectorDbConfig.isAutoDiscovery());

        return vectorModel;
    }

    @Override
    public String getTypeName() {
        return "vector";
    }

    @Override
    public QueryModelBuilder createDetachedQueryModelBuilder(
            TableModelLoaderManager tableModelLoaderManager,
            SystemBundlesContext systemBundlesContext,
            FileFsscriptLoader fileFsscriptLoader
    ) {
        TmVectorModelLoaderImpl detached = new TmVectorModelLoaderImpl(
                systemBundlesContext,
                fileFsscriptLoader
        );
        detached.vectorHost = vectorHost;
        detached.vectorPort = vectorPort;
        detached.vectorDatabase = vectorDatabase;
        detached.vectorUsername = vectorUsername;
        detached.vectorPassword = vectorPassword;
        detached.autoDiscovery = autoDiscovery;
        detached.poolSize = poolSize;
        detached.poolMaxWaitMs = poolMaxWaitMs;
        detached.embeddingType = embeddingType;
        detached.embeddingBaseUrl = embeddingBaseUrl;
        detached.embeddingApiKey = embeddingApiKey;
        detached.embeddingModel = embeddingModel;
        detached.embeddingDimensions = embeddingDimensions;
        return detached;
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

        // 传递元数据
        if (mainTm.getVectorFieldMetadata() != null) {
            qm.setVectorFieldMetadata(mainTm.getVectorFieldMetadata());
        }

        queryModelDef.apply(qm);

        log.debug("Vector QueryModel built successfully: {}", qm.getName());
        return qm;
    }

    /**
     * 从 Milvus 自动发现向量字段元数据
     */
    private VectorFieldMetadata discoverVectorFieldMetadata(VectorDbConfig config, String collectionName, String fieldName) {
        MilvusMetadataService metadataService = null;
        try {
            metadataService = new MilvusMetadataService(config);

            // 先检查集合是否存在
            if (!metadataService.collectionExists(collectionName)) {
                log.warn("Collection '{}' does not exist in Milvus, skipping auto-discovery", collectionName);
                return null;
            }

            // 获取向量字段元数据
            VectorFieldMetadata metadata = metadataService.getVectorFieldMetadata(collectionName, fieldName);
            if (metadata == null) {
                log.warn("Could not get metadata for vector field '{}.{}', will use default configuration",
                        collectionName, fieldName);
            }
            return metadata;

        } catch (Exception e) {
            log.warn("Failed to auto-discover vector field metadata for '{}.{}': {}",
                    collectionName, fieldName, e.getMessage());
            return null;
        } finally {
            if (metadataService != null) {
                metadataService.close();
            }
        }
    }

    /**
     * 使用发现的元数据更新 TM 定义中的向量字段
     * <p>
     * 配置优先级：TM 显式配置 > Milvus 自动发现
     */
    private void updateVectorFieldFromMetadata(DbModelDef def, VectorFieldMetadata metadata) {
        if (def.getProperties() == null) {
            return;
        }

        for (DbPropertyDef property : def.getProperties()) {
            if ("VECTOR".equalsIgnoreCase(property.getType())
                    && property.getColumn().equals(metadata.getFieldName())) {

                // 仅在未显式指定时使用自动发现的值
                if (property.getDimensions() == null || property.getDimensions() == 0) {
                    property.setDimensions(metadata.getDimension());
                    log.debug("Set dimension from auto-discovery: {}", metadata.getDimension());
                }

                if (StringUtils.isEmpty(property.getMetric())) {
                    property.setMetric(metadata.getMetricType());
                    log.debug("Set metric from auto-discovery: {}", metadata.getMetricType());
                }

                // Note: indexType is stored in VectorFieldMetadata, not in DbPropertyDef
                log.debug("IndexType from auto-discovery: {}", metadata.getIndexType());

                break;
            }
        }
    }

    /**
     * 构建向量数据库配置
     */
    private VectorDbConfig buildVectorDbConfig(DbModelDef def) {
        VectorDbConfig.VectorDbConfigBuilder builder = VectorDbConfig.builder()
                .type("milvus")
                .host(vectorHost)
                .port(vectorPort)
                .autoDiscovery(autoDiscovery)
                .poolSize(poolSize)
                .poolMaxWaitMs(poolMaxWaitMs);

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
