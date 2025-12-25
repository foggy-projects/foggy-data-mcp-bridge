package com.foggyframework.dataset.mongo.support;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.model.support.ResultSetModelSupport;
import com.foggyframework.dataset.mongo.MongoKey;
import com.foggyframework.dataset.mongo.MongoModel;
import com.foggyframework.dataset.mongo.expression.MongoDbExpFactory;
import com.foggyframework.dataset.mongo.funs.MongoFileFsscriptLoader;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import com.mongodb.BasicDBObject;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 fsscript 的 MongoDB DataSetModel
 * 用于加载和执行 .ms 格式的 MongoDB 查询脚本
 */
@Data
@Slf4j
public class MongoFscriptDataSetModel<T> extends ResultSetModelSupport implements MongoModel {

    private BundleResource bundleResource;

    MongoFileFsscriptLoader fileFsscriptLoader;

    public MongoFscriptDataSetModel(BundleResource bundleResource, MongoFileFsscriptLoader fileFsscriptLoader) {
        this.bundleResource = bundleResource;
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

    public FsscriptClosureDefinition getClosureDefinition() {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource.getResource());
        FsscriptClosureDefinition closureDefinition = fsscript.getFsscriptClosureDefinition();
        return closureDefinition;
    }

    @Override
    public QueryExpEvaluator newQueryExpEvaluator(ApplicationContext appCtx) {
        return new QueryExpEvaluator(DefaultExpEvaluator.newInstance(appCtx, getClosureDefinition().newFoggyClosure()));
    }

    private MongoTemplate getMongoTemplate(ExpEvaluator ee) {
        try {
            return ee.getExportObject("mongoTemplate");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PagingResultImpl queryPaging(QueryExpEvaluator ee) {
        return (PagingResultImpl) query(this.getSql(ee), ee, true, ee.getBeanCls() == null);
    }

    private Object query(final MongoKey key, QueryExpEvaluator ee, boolean retPaging, boolean returnDocument) {
        MongoCollection<Document> set = key.set;

        FindIterable<Document> r;
        if (key.sql instanceof FindIterable) {
            r = (FindIterable<Document>) key.sql;
        } else if (key.sql instanceof AggregateIterable) {
            AggregateIterable<Document> aggregateIterable = (AggregateIterable<Document>) key.sql;
            return processAggregateIterable(key, ee, aggregateIterable, retPaging, returnDocument);
        } else {
            if (key.sql instanceof Map) {
                BasicDBObject root = new BasicDBObject();
                for (Map.Entry e : ((Map<?, ?>) key.sql).entrySet()) {
                    root.append((String) e.getKey(), e.getValue());
                }
                key.sql = root;
            }
            r = set.find((Bson) key.sql);
        }

        if (key.limit > 0) {
            r.limit(key.limit);
        }
        if (key.start > 0) {
            r.skip(key.start);
        }
        if (key.sort != null) {
            r.sort(key.sort);
        }

        return processResult(key, ee, r, retPaging, returnDocument);
    }

    private Object processResult(final MongoKey key, QueryExpEvaluator ee, MongoIterable<Document> r, boolean retPaging, boolean returnDocument) {
        MongoTemplate template = key.template;
        MongoCollection<Document> set = key.set;

        List<Object> list = new ArrayList<>();
        if (ee.getBeanCls() == null || returnDocument) {
            for (Document arg0 : r) {
                arg0.put("id", arg0.get("_id"));
                list.add(arg0);
            }
        } else {
            for (Document arg0 : r) {
                Object obj = template.getConverter().read(ee.getBeanCls(), arg0);
                list.add(obj);
            }
        }

        if (retPaging) {
            PagingResultImpl pagingResult = new PagingResultImpl();
            pagingResult.setLimit(key.limit);
            pagingResult.setStart(key.start);
            if (ee.isReturnTotal() && key.sql instanceof Bson) {
                long total = set.countDocuments((Bson) key.sql);
                pagingResult.setTotal(total);
            }
            pagingResult.setItems(list);
            return pagingResult;
        }
        return list;
    }

    private Object processAggregateIterable(MongoKey key, QueryExpEvaluator ee, AggregateIterable<Document> aggregateIterable, boolean retPaging, boolean returnDocument) {
        Document first = aggregateIterable.first();
        if (first == null) {
            return processResult(key, ee, aggregateIterable, retPaging, returnDocument);
        }
        Object totalCount = first.get("totalCount");
        Object results = first.get("results");
        if (totalCount instanceof List && results instanceof List) {
            return processAggregateIterablePaging(key, ee, (List) totalCount, (List<Document>) results, retPaging, returnDocument);
        }
        return processResult(key, ee, aggregateIterable, retPaging, returnDocument);
    }

    private Object processAggregateIterablePaging(final MongoKey key, QueryExpEvaluator ee, List totalCount, List<Document> results, boolean retPaging, boolean returnDocument) {
        if (totalCount.isEmpty()) {
            if (results.isEmpty()) {
                if (retPaging) {
                    PagingResultImpl pagingResult = new PagingResultImpl();
                    pagingResult.setLimit(key.limit);
                    pagingResult.setStart(key.start);
                    pagingResult.setItems(Collections.EMPTY_LIST);
                    return pagingResult;
                }
                return Collections.EMPTY_LIST;
            } else {
                throw RX.throwB("处理数据集【" + name + "】分页信息时失败，totalCount与期望的不同，请参阅aggregate分页规范");
            }
        }
        Object total = ((Map) totalCount.get(0)).get("totalCount");
        if (!(total instanceof Number)) {
            throw RX.throwB("处理数据集【" + name + "】分页信息时失败，totalCount期望是number型，但实际是【" + total + "】");
        }

        MongoTemplate template = key.template;
        long totalNum = ((Number) total).longValue();

        List list = new ArrayList();
        if (ee.getBeanCls() == null || returnDocument) {
            list = results;
            for (Document arg0 : results) {
                arg0.put("id", arg0.get("_id"));
            }
        } else {
            for (Document arg0 : results) {
                Object obj = template.getConverter().read(ee.getBeanCls(), arg0);
                list.add(obj);
            }
        }

        if (retPaging) {
            PagingResultImpl pagingResult = new PagingResultImpl();
            pagingResult.setLimit(key.limit);
            pagingResult.setStart(key.start);
            pagingResult.setTotal(totalNum);
            pagingResult.setItems(list);
            return pagingResult;
        }
        return list;
    }

    private MongoKey getSql(QueryExpEvaluator ee) {
        try {
            ee.setExpFactory(MongoDbExpFactory.MONGODB);
            Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource.getResource());
            fsscript.eval(ee);

            MongoTemplate dbTemplate = ee.getExportObject("mongoTemplate");
            if (dbTemplate == null) {
                if (log.isDebugEnabled()) {
                    log.debug(bundleResource.getResource() + "未定义mongoTemplate，使用系统自带的mongoTemplate");
                }
                dbTemplate = (MongoTemplate) ee.getApplicationContext().getBean("mongoTemplate");
            }
            if (dbTemplate == null) {
                throw RX.throwB(bundleResource.getResource() + " 没有export mongoTemplate");
            }
            String setName = ee.getExportObject("setName");
            if (StringUtils.isEmpty(setName)) {
                throw RX.throwB(bundleResource.getResource() + " 没有export setName");
            }
            Object mongo = ee.getExportObject("mongo");

            if (mongo == null) {
                FsscriptFunction buildMongo = ee.getExportObject("buildMongo");
                if (buildMongo == null) {
                    throw RX.throwB(bundleResource.getResource() + "未导出mongo或buildMongo");
                }
                mongo = buildMongo.autoApply(ee);
            }
            if (mongo != null) {
                if (log.isDebugEnabled()) {
                    if (mongo instanceof Map) {
                        log.debug(JsonUtils.toJsonNotIgnoreNull(mongo));
                    } else {
                        log.debug(mongo.toString());
                    }
                }
            }
            MongoCollection<Document> set = dbTemplate.getCollection(setName);
            MongoKey key = buildKey(dbTemplate, setName, set, ee, mongo);

            return key;
        } finally {
        }
    }

    private MongoKey buildKey(MongoTemplate template, String setName, MongoCollection<Document> _set, QueryExpEvaluator ee,
                              Object currentSql) {
        MongoKey k;

        if (ee.needPaging()) {
            k = new MongoKey(template, setName, _set, currentSql, ee.getStart(), ee.getLimit());
        } else {
            k = new MongoKey(template, setName, _set, currentSql, 0, 0);
        }
        Object sortExport = ee.getExportObject("sort");
        if (sortExport instanceof Bson) {
            k.sort = (Bson) sortExport;
        } else if (sortExport instanceof Map) {
            BasicDBObject bson = new BasicDBObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) sortExport).entrySet()) {
                bson.append(e.getKey(), e.getValue());
            }
            k.sort = bson;
        }

        return k;
    }

    @Override
    public Map<String, Object> queryMap(QueryExpEvaluator ee) {
        ee.setLimit(1);
        List list = (List) query(this.getSql(ee), ee, false, false);
        return list.isEmpty() ? Collections.EMPTY_MAP : (Map<String, Object>) list.get(0);
    }

    @Override
    public List<Object> queryList(QueryExpEvaluator ee) {
        ee.setLimit(1);
        Document document = (Document) query(this.getSql(ee), ee, false, true);
        if (document != null) {
            return new ArrayList<>(document.values());
        }
        return Collections.EMPTY_LIST;
    }
}
