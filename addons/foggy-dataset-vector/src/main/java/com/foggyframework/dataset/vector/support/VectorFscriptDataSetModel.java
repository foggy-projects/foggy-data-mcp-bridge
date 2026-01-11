package com.foggyframework.dataset.vector.support;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.QueryExpEvaluator;
import com.foggyframework.dataset.model.support.ResultSetModelSupport;
import com.foggyframework.dataset.vector.VectorKey;
import com.foggyframework.dataset.vector.VectorModel;
import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 fsscript 的向量数据库 DataSetModel
 * 类似于 MongoFscriptDataSetModel
 */
@Data
@Slf4j
public class VectorFscriptDataSetModel<T> extends ResultSetModelSupport implements VectorModel {

    private BundleResource bundleResource;
    private VectorFileFsscriptLoader fileFsscriptLoader;

    public VectorFscriptDataSetModel(BundleResource bundleResource, VectorFileFsscriptLoader fileFsscriptLoader) {
        this.bundleResource = bundleResource;
        this.fileFsscriptLoader = fileFsscriptLoader;
    }

    public FsscriptClosureDefinition getClosureDefinition() {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource.getResource());
        return fsscript.getFsscriptClosureDefinition();
    }

    public QueryExpEvaluator newQueryExpEvaluator(ApplicationContext appCtx) {
        return new QueryExpEvaluator(DefaultExpEvaluator.newInstance(appCtx, getClosureDefinition().newFoggyClosure()));
    }

    public PagingResultImpl queryPaging(QueryExpEvaluator ee) {
        return (PagingResultImpl) query(this.getVectorKey(ee), ee, true);
    }

    private Object query(final VectorKey key, QueryExpEvaluator ee, boolean retPaging) {
        VectorStore vectorStore = key.getVectorStore();

        // 构建向量检索请求 (Spring AI 1.0.1 API)
        SearchRequest searchRequest = SearchRequest.builder()
                .query(key.getQuery())
                .topK(key.getTopK())
                .similarityThreshold(key.getThreshold())
                .build();

        // 执行向量检索
        List<Document> results = vectorStore.similaritySearch(searchRequest);

        // 转换为标准格式
        List<Map<String, Object>> list = results.stream()
                .map(this::documentToMap)
                .collect(Collectors.toList());

        // 应用分页
        if (key.getStart() > 0 || key.getLimit() > 0) {
            int fromIndex = Math.min(key.getStart(), list.size());
            int toIndex = key.getLimit() > 0 ? Math.min(key.getStart() + key.getLimit(), list.size()) : list.size();
            list = list.subList(fromIndex, toIndex);
        }

        if (retPaging) {
            PagingResultImpl pagingResult = new PagingResultImpl();
            pagingResult.setLimit(key.getLimit());
            pagingResult.setStart(key.getStart());
            pagingResult.setTotal((long) results.size());
            pagingResult.setItems(list);
            return pagingResult;
        }
        return list;
    }

    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", doc.getId());
        map.put("content", doc.getText());
        map.put("similarity", doc.getMetadata().get("distance"));
        map.putAll(doc.getMetadata());
        return map;
    }

    private VectorKey getVectorKey(QueryExpEvaluator ee) {
        Fsscript fsscript = fileFsscriptLoader.findLoadFsscript(bundleResource.getResource());
        fsscript.eval(ee);

        VectorStore vectorStore = ee.getExportObject("vectorStore");
        if (vectorStore == null) {
            if (log.isDebugEnabled()) {
                log.debug(bundleResource.getResource() + "未定义vectorStore，使用系统自带的vectorStore");
            }
            vectorStore = (VectorStore) ee.getApplicationContext().getBean("vectorStore");
        }
        if (vectorStore == null) {
            throw RX.throwB(bundleResource.getResource() + " 没有export vectorStore");
        }

        String query = ee.getExportObject("query");
        if (StringUtils.isEmpty(query)) {
            FsscriptFunction buildQuery = ee.getExportObject("buildQuery");
            if (buildQuery == null) {
                throw RX.throwB(bundleResource.getResource() + "未导出query或buildQuery");
            }
            query = (String) buildQuery.autoApply(ee);
        }

        Integer topK = ee.getExportObject("topK");
        if (topK == null) {
            topK = 10;
        }

        Double threshold = ee.getExportObject("threshold");
        if (threshold == null) {
            threshold = 0.7;
        }

        return buildKey(vectorStore, query, topK, threshold, ee);
    }

    private VectorKey buildKey(VectorStore vectorStore, String query, int topK, double threshold, QueryExpEvaluator ee) {
        if (ee.needPaging()) {
            return new VectorKey(vectorStore, query, topK, threshold, ee.getStart(), ee.getLimit());
        } else {
            return new VectorKey(vectorStore, query, topK, threshold, 0, 0);
        }
    }

    public Map<String, Object> queryMap(QueryExpEvaluator ee) {
        ee.setLimit(1);
        List list = (List) query(this.getVectorKey(ee), ee, false);
        return list.isEmpty() ? Collections.EMPTY_MAP : (Map<String, Object>) list.get(0);
    }

    public List<Object> queryList(QueryExpEvaluator ee) {
        ee.setLimit(1);
        List list = (List) query(this.getVectorKey(ee), ee, false);
        if (list != null && !list.isEmpty()) {
            Map<String, Object> map = (Map<String, Object>) list.get(0);
            return new ArrayList<>(map.values());
        }
        return Collections.EMPTY_LIST;
    }
}
