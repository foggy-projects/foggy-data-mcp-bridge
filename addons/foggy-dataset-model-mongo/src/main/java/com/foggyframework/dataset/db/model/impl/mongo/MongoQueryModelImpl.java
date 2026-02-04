package com.foggyframework.dataset.db.model.impl.mongo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.tuple.Tuple3;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.MongoModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.expression.MongoCalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.CalculatedFieldProcessor;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.utils.MongoModelNamedUtils;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.*;

@Getter
@Setter
@Slf4j
public class MongoQueryModelImpl extends QueryModelSupport implements MongoQueryModel {


    MongoTemplate defaultMongoTemplate;

    /**
     * 计算字段处理器（延迟初始化）
     */
    private CalculatedFieldProcessor calculatedFieldProcessor;


    public MongoQueryModelImpl(List<TableModel> jdbcModelList, Fsscript fsscript, MongoTemplate defaultMongoTemplate) {
        super(jdbcModelList, fsscript);
        this.defaultMongoTemplate = defaultMongoTemplate;
    }

    @Override
    public CalculatedFieldProcessor getCalculatedFieldProcessor() {
        if (calculatedFieldProcessor == null) {
            calculatedFieldProcessor = new MongoCalculatedFieldProcessor(this);
        }
        return calculatedFieldProcessor;
    }


    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public DbQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        return queryMongo(systemBundlesContext, context);
    }

    /**
     * 执行 MongoDB 查询
     * <p>
     * 支持 L2 缓存（聚合管道级别）：在管道生成后、执行前检查缓存。
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              查询上下文
     * @return 查询结果
     */
    public DbQueryResult queryMongo(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        PagingRequest<DbQueryRequestDef> form = context.getRequest();
        DbQueryRequestDef queryRequest = form.getParam();

        MongoModelQueryEngine queryEngine = new MongoModelQueryEngine(this);

        /**
         * 构建查询语句（包含权限条件注入）
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, context);
        Tuple3<Criteria, AggregationOperation, Sort> options = queryEngine.buildOptions();

        // 构建 $addFields 操作（用于计算字段）
        AggregationOperation addFieldsOp = queryEngine.buildAddFieldsOperation();

        // 生成聚合管道字符串（用于 L2 缓存键）
        String pipelineKey = buildPipelineCacheKey(options, addFieldsOp, queryEngine, form);

        // 【L2 缓存检查】在管道生成后、执行前
        QueryCacheProvider cacheProvider = getQueryCacheProvider(context);
        boolean l2Enabled = QueryCacheProvider.isL2Enabled(context);

        if (l2Enabled && cacheProvider != null) {
            PagingResultImpl cached = cacheProvider.checkL2Cache(getName(), pipelineKey, Collections.emptyList(), context);
            if (cached != null) {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache HIT for mongo model={}", getName());
                }
                QueryCacheProvider.markL2Hit(context);
                return DbQueryResult.of(cached, queryEngine);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache MISS for mongo model={}", getName());
                }
            }
        }

        // 调试输出 - MongoDB 聚合管道
        if (log.isDebugEnabled()) {
            log.debug("生成 MongoDB 聚合查询");
            log.debug("集合: {}", this.jdbcModel.getTableName());
            log.debug("$match: {}", MongoModelNamedUtils.criteriaToString(options.getT1()));
            if (addFieldsOp != null) {
                log.debug("$addFields: {}", queryEngine.buildAddFieldsDocument());
            }
            log.debug("$project: {}", queryEngine.getProjectionDocument());
            if (options.getT3() != null) {
                log.debug("$sort: {}", MongoModelNamedUtils.formatSort(options.getT3()));
            }
            log.debug("$skip: {}, $limit: {}", form.getStart(), form.getLimit());
        }

        // 构建聚合管道：$match -> [$addFields] -> $project -> [$sort] -> $skip -> $limit
        List<AggregationOperation> pipeline = new ArrayList<>();
        pipeline.add(Aggregation.match(options.getT1()));

        // 如果有计算字段，添加 $addFields 阶段
        if (addFieldsOp != null) {
            pipeline.add(addFieldsOp);
        }

        pipeline.add(options.getT2()); // $project

        Aggregation queryAgg = Aggregation.newAggregation(pipeline);
        if (options.getT3() != null) {
            queryAgg.getPipeline().add(Aggregation.sort(options.getT3()));
        }
        queryAgg.getPipeline().add(Aggregation.skip(form.getStart()));
        queryAgg.getPipeline().add(Aggregation.limit(form.getLimit()));

        AggregationResults<Document> results = defaultMongoTemplate.aggregate(queryAgg, this.jdbcModel.getTableName(), Document.class);

        /**
         * 转换objectId
         */
        for (Document r : results.getMappedResults()) {
            if (r.get("_id") instanceof ObjectId) {
                r.put("_id", r.get("_id").toString());
            }
        }

        /**
         * 查询汇总数据
         */
        Map<String, Object> totalData = null;
        int total = 0;
        if (form.getParam().isReturnTotal()) {
            GroupOperation groupOperation = queryEngine.buildGroupOperation(systemBundlesContext, null, queryRequest);
            Aggregation groupAgg = Aggregation.newAggregation(Aggregation.match(options.getT1()), options.getT2(), groupOperation);
            totalData = defaultMongoTemplate.aggregate(groupAgg, this.jdbcModel.getTableName(), Document.class).getUniqueMappedResult();
            Number it = totalData == null ? 0 : (Number) totalData.get("total");
            if (it != null && totalData != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }

        PagingResultImpl result = PagingResultImpl.of(results.getMappedResults(), form.getStart(), form.getLimit(), totalData, total);

        // 【L2 缓存写入】
        if (l2Enabled && cacheProvider != null) {
            boolean l2Hit = context.getCacheConfig() != null && context.getCacheConfig().isL2CacheHit();
            if (!l2Hit) {
                cacheProvider.writeL2Cache(getName(), pipelineKey, Collections.emptyList(), result, context);
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache WRITE for mongo model={}", getName());
                }
            }
        }

        return DbQueryResult.of(result, queryEngine);
    }

    /**
     * 构建聚合管道的缓存键
     *
     * @param options      查询选项（match、project、sort）
     * @param addFieldsOp  addFields 操作
     * @param queryEngine  查询引擎
     * @param form         分页请求
     * @return 管道字符串表示（用于缓存键）
     */
    private String buildPipelineCacheKey(Tuple3<Criteria, AggregationOperation, Sort> options,
                                         AggregationOperation addFieldsOp,
                                         MongoModelQueryEngine queryEngine,
                                         PagingRequest<DbQueryRequestDef> form) {
        StringBuilder sb = new StringBuilder();
        sb.append("collection:").append(this.jdbcModel.getTableName());
        sb.append("|match:").append(MongoModelNamedUtils.criteriaToString(options.getT1()));
        if (addFieldsOp != null) {
            sb.append("|addFields:").append(queryEngine.buildAddFieldsDocument());
        }
        sb.append("|project:").append(queryEngine.getProjectionDocument());
        if (options.getT3() != null) {
            sb.append("|sort:").append(MongoModelNamedUtils.formatSort(options.getT3()));
        }
        sb.append("|skip:").append(form.getStart());
        sb.append("|limit:").append(form.getLimit());
        return sb.toString();
    }

    /**
     * 从上下文获取缓存提供者
     */
    private QueryCacheProvider getQueryCacheProvider(ModelResultContext context) {
        if (context == null || context.getExtData() == null) {
            return null;
        }
        Object provider = context.getExtData().get("queryCacheProvider");
        if (provider instanceof QueryCacheProvider) {
            return (QueryCacheProvider) provider;
        }
        return null;
    }

}
