package com.foggyframework.dataset.jdbc.model.impl.mongo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.tuple.Tuple3;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.MongoModelQueryEngine;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.engine.query_model.QueryModelSupport;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.spi.*;
import com.foggyframework.dataset.jdbc.model.utils.JdbcModelNamedUtils;
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
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.*;

@Getter
@Setter
@Slf4j
public class MongoQueryModelImpl extends QueryModelSupport implements MongoQueryModel {


    MongoTemplate defaultMongoTemplate;


    public MongoQueryModelImpl(List<JdbcModel> jdbcModelList, Fsscript fsscript, MongoTemplate defaultMongoTemplate) {
        super(jdbcModelList, fsscript);
        this.defaultMongoTemplate = defaultMongoTemplate;
    }


    //    public void addSelectColumn(JdbcColumn jdbcColumn) {
//        selectColumns.add(jdbcColumn);
//    }
    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form) {
        // 创建新的上下文
        ModelResultContext context = new ModelResultContext(form, null);
        return query(systemBundlesContext, context);
    }

    @Override
    public JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        return queryMongo(systemBundlesContext, context.getRequest());

    }

    public JdbcQueryResult queryMongo(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form) {
        JdbcQueryRequestDef queryRequest = form.getParam();

        MongoModelQueryEngine queryEngine = new MongoModelQueryEngine(this);

        /**
         * 构建 查询语句
         */
        queryEngine.analysisQueryRequest(systemBundlesContext, queryRequest);
        Tuple3<Criteria, ProjectionOperation, Sort> options = queryEngine.buildOptions();

        if (log.isDebugEnabled()) {
            log.debug("生成查询对象");
            log.debug(JdbcModelNamedUtils.criteriaToString(options.getT1()));
            log.debug(JdbcModelNamedUtils.projectionOperationToString(options.getT2()));
            if (options.getT3() != null) {
                log.debug(JdbcModelNamedUtils.formatSort(options.getT3()));
            }
        }

        Aggregation queryAgg = Aggregation.newAggregation(Aggregation.match(options.getT1()), options.getT2());
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
         * TODO 查询汇总数据
         */
        Map<String, Object> totalData = null;

        int total = 0;
        if (form.getParam().isReturnTotal()) {
            GroupOperation groupOperation = queryEngine.buildGroupOperation(systemBundlesContext, null, queryRequest);
            Aggregation groupAgg = Aggregation.newAggregation(Aggregation.match(options.getT1()), options.getT2(), groupOperation);
            totalData = defaultMongoTemplate.aggregate(groupAgg, this.jdbcModel.getTableName(), Document.class).getUniqueMappedResult();
//            totalData = DataSourceQueryUtils.getDatasetTemplate(defaultDataSource).queryMapObject1(queryEngine.getAggSql(), queryEngine.getValues());
            Number it = totalData == null ? 0 : (Number) totalData.get("total");
            if (it != null && totalData != null) {
                total = it.intValue();
                totalData.put("total", total);
            }
        }
        return JdbcQueryResult.of(PagingResultImpl.of(results.getMappedResults(), form.getStart(), form.getLimit(), totalData, total), null);
    }

}
